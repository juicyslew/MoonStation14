package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
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

/** Local-only medical camera presentation; never writes medical state or consumes perspective input. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class MedicalPerspectiveClient {
    private static final MedicalPerspectivePolicy<net.minecraft.client.CameraType> POLICY = new MedicalPerspectivePolicy<>();
    private static LivingEntity trackedBody;
    private static MobStateComponent.State lastState;

    private MedicalPerspectiveClient() { }

    @SubscribeEvent public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        var mapping = Minecraft.getInstance().options.keyTogglePerspective;
        if (mapping.matches(event.getKey(), event.getScanCode())) POLICY.manualChange();
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
            var decision = POLICY.observationChanged();
            if (decision.restore() != null) mc.options.setCameraType(decision.restore());
            trackedBody = body; lastState = null;
        }
        if (body == null) return;
        MobStateAttachment attachment = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        MobStateComponent.State state = attachment == null ? MobStateComponent.State.ALIVE : attachment.data().state();
        boolean active = state == MobStateComponent.State.CRITICAL || state == MobStateComponent.State.DEAD;
        if (state != lastState) {
            var decision = state == MobStateComponent.State.DEAD && lastState == MobStateComponent.State.CRITICAL
                    ? POLICY.dead(mc.options.getCameraType())
                    : POLICY.state(active, mc.options.getCameraType());
            if (decision.pushRear()) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            else if (decision.restore() != null) mc.options.setCameraType(decision.restore());
            lastState = state;
        }
    }

    /** Observation transport can call this when the owned body ceases to be observed. */
    public static void onObservationChanged() {
        var decision = POLICY.observationChanged();
        if (decision.restore() != null) Minecraft.getInstance().options.setCameraType(decision.restore());
        trackedBody = null; lastState = null;
    }
}
