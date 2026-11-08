package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ClientConfig;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.eye.client.AngularRecoveryComfortCameraPolicy;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads;
import com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/** Local-only medical camera presentation; never writes medical state or consumes perspective input. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class MedicalPerspectiveClient {
    private static final MedicalPerspectivePolicy<net.minecraft.client.CameraType> POLICY = new MedicalPerspectivePolicy<>();
    private static LivingEntity trackedBody;
    private static MobStateComponent.State lastState;
    private static boolean lastComfort;
    private static boolean lastActive;
    private static final AngularRecoveryComfortCameraPolicy COMFORT_CAMERA = new AngularRecoveryComfortCameraPolicy();
    private static long comfortEpoch;
    private static boolean comfortManualOptOut;

    private MedicalPerspectiveClient() { }

    @SubscribeEvent public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        var mapping = Minecraft.getInstance().options.keyTogglePerspective;
        if (mapping.matches(event.getKey(), event.getScanCode())) {
            POLICY.manualChange();
            if (COMFORT_CAMERA.active()) {
                finishComfort(Minecraft.getInstance(), currentRecoveryState());
                comfortManualOptOut = true;
                lastComfort = false;
            }
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean ghostObservation = ObserverClient.current() != null
                && ObserverClient.current().mode() == ObserverPayloads.Mode.GHOST;
        LivingEntity body = ghostObservation ? null : ObserverClient.current() != null
                && ObserverClient.current().mode() == ObserverPayloads.Mode.CORPSE
                ? ObserverClient.observedBody() : GhostControlClient.ownedCharacterForPresentation();
        if (!ghostObservation && body == null && mc.player != null && mc.level != null && !mc.player.isSpectator()) body = mc.player;
        if (body != trackedBody) {
            clearComfort();
            var decision = POLICY.observationChanged();
            if (decision.restore() != null) mc.options.setCameraType(decision.restore());
            trackedBody = body; lastState = null; lastComfort = false; lastActive = false;
        }
        if (body == null) return;
        long epoch = GhostControlClient.presentationCharacterEpoch();
        if (epoch != comfortEpoch) {
            var oldLease = POLICY.observationChanged();
            if (oldLease.restore() != null) mc.options.setCameraType(oldLease.restore());
            clearComfort();
            comfortEpoch = epoch;
            lastState = null; lastComfort = false; lastActive = false;
        }
        MobStateAttachment attachment = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        MobStateComponent.State state = attachment == null ? MobStateComponent.State.ALIVE : attachment.data().state();
        MedicalMovementClientTrace.observeMedicalState(body, state, GhostControlClient.presentationCharacterEpoch());
        boolean medicalActive = state == MobStateComponent.State.CRITICAL || state == MobStateComponent.State.DEAD;
        boolean contained = com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained();
        boolean requested = ClientConfig.ANGULAR_RECOVERY_COMFORT_CAMERA.get()
                && GhostControlClient.angularRecoveryComfortRequested();
        if (!requested) comfortManualOptOut = false;
        boolean comfort = MedicalPerspectivePolicy.comfortActive(
                ClientConfig.ANGULAR_RECOVERY_COMFORT_CAMERA.get(), requested, medicalActive,
                contained, comfortManualOptOut);
        AngularRecoveryModel.State recovery = recoveryState(body);
        if (comfort && mc.player != null) {
            if (!COMFORT_CAMERA.active()) COMFORT_CAMERA.start(recovery, mc.player.getYRot(), mc.player.getXRot());
            else COMFORT_CAMERA.update(mc.player.getYRot(), mc.player.getXRot());
        } else if (COMFORT_CAMERA.active() && !medicalActive && !contained) {
            finishComfort(mc, recovery);
        } else if (COMFORT_CAMERA.active()) {
            COMFORT_CAMERA.clear(); // takeover: do not rebase logical body look to a medical/contained view
        }
        boolean active = !contained && (medicalActive || comfort);
        if (!contained && (state != lastState || comfort != lastComfort || active != lastActive)) {
            var decision = state == MobStateComponent.State.DEAD && lastState == MobStateComponent.State.CRITICAL
                    ? POLICY.dead(mc.options.getCameraType())
                    : POLICY.state(active, mc.options.getCameraType());
            if (decision.pushRear()) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            else if (decision.restore() != null) mc.options.setCameraType(decision.restore());
            lastState = state;
            lastComfort = comfort;
        }
        lastActive = active;
    }

    /** Observation transport can call this when the owned body ceases to be observed. */
    public static void onObservationChanged() {
        var decision = POLICY.observationChanged();
        if (decision.restore() != null) Minecraft.getInstance().options.setCameraType(decision.restore());
        clearComfort(); trackedBody = null; lastState = null; lastComfort = false; lastActive = false;
    }

    /** Stable external-world angles for the viewport camera override; absent outside comfort mode. */
    public static Optional<AngularRecoveryComfortCameraPolicy.Angles> angularRecoveryViewAngles() {
        return COMFORT_CAMERA.worldAngles();
    }

    public static boolean isAngularRecoveryCameraActive() { return COMFORT_CAMERA.active(); }

    /** Releases only this owner's lease; containment composes the returned baseline into its own lease. */
    public static net.minecraft.client.CameraType releaseForContainment(net.minecraft.client.CameraType current) {
        clearComfort();
        var decision = POLICY.observationChanged();
        lastComfort = false; lastActive = false; lastState = null;
        return decision.restore() == null ? current : decision.restore();
    }

    private static AngularRecoveryModel.State currentRecoveryState() { return recoveryState(trackedBody); }
    private static AngularRecoveryModel.State recoveryState(LivingEntity entity) {
        return entity instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity character
                ? character.angularRecoveryState().orElse(null) : null;
    }
    private static void finishComfort(Minecraft mc, AngularRecoveryModel.State state) {
        var handoff = COMFORT_CAMERA.finish(state);
        if (handoff.isEmpty()) return;
        var look = handoff.get();
        if (mc.player != null) com.juicyslew.moonstation14.ms14.medical_observer.ObserverGhostLook.apply(mc.player, look.yaw(), look.pitch());
        if (trackedBody != null) com.juicyslew.moonstation14.ms14.medical_observer.ObserverGhostLook.apply(trackedBody, look.yaw(), look.pitch());
    }
    private static void clearComfort() {
        COMFORT_CAMERA.clear(); comfortEpoch = 0; comfortManualOptOut = false;
    }
}

