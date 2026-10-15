package com.juicyslew.moonstation14.ms14.medical_observer.client;

import com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Client input and camera for server-authored observer sessions. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ObserverClient {
    private static ObserverPayloads.Snapshot current;
    private static long lastEpoch = -1;
    private static int previousDirections;
    private static final KeyMapping RETURN = new KeyMapping("key.moonstation14.observer_return", GLFW.GLFW_KEY_R, "key.categories.moonstation14");
    private static boolean returnHintShown;
    private static boolean querySent;
    private static MedicalCameraAnchorEntity medicalAnchor;
    private static boolean ghostLookInitialized;
    private static long ghostLookEpoch = -1;
    private static java.util.UUID ghostLookId;
    private static float ghostLookYaw, ghostLookPitch;
    private static int medicalBodyEntityId = -1;
    private static java.util.UUID medicalBodyId;
    private static LivingEntity medicalBody;
    private static long ghostMovementEpoch = -1;
    private static java.util.UUID ghostMovementId;
    private static com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent ghostMovement;
    private static int medicalLookEntityId = -1;
    private static java.util.UUID medicalLookBodyId;

    private ObserverClient() { }

    public static void deliverClient(ObserverPayloads.Snapshot snapshot) { receive(snapshot); }

    public static void register(RegisterKeyMappingsEvent event) { event.register(RETURN); }

    public static void receive(ObserverPayloads.Snapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()) return;
        if (current != null && snapshot.epoch() < current.epoch()) {
            MoonStation14.LOGGER.debug("[observer client] stale snapshot rejected mode={} epoch={} body={} currentMode={} currentEpoch={} currentBody={}",
                    snapshot.mode(), snapshot.epoch(), snapshot.body(), current.mode(), current.epoch(), current.body());
            return;
        }
        if (current == null || current.mode() != snapshot.mode() || current.epoch() != snapshot.epoch()
                || !current.body().equals(snapshot.body())) {
            MoonStation14.LOGGER.info("[observer client] snapshot mode={} epoch={} body={} ghost={}",
                    snapshot.mode(), snapshot.epoch(), snapshot.body(), snapshot.ghost());
        }
        if (current == null || current.epoch() != snapshot.epoch() || !java.util.Objects.equals(current.ghost(), snapshot.ghost()))
            resetGhostMovement();
        if (snapshot.mode() == ObserverPayloads.Mode.END) {
            current = null;
            resetGhostLook();
            resetGhostMovement();
            previousDirections = 0;
            lastEpoch = Math.max(lastEpoch, snapshot.epoch());
            if (mc.getCameraEntity() == medicalAnchor) mc.setCameraEntity(resolveEndBody(mc));
            medicalAnchor = null;
            medicalBody = null; medicalBodyEntityId = -1; medicalBodyId = null;
            returnHintShown = false;
            com.juicyslew.moonstation14.ms14.mobs.client.MedicalPerspectiveClient.onObservationChanged();
            return;
        }
        if (snapshot.epoch() < lastEpoch) {
            MoonStation14.LOGGER.debug("[observer client] stale snapshot rejected mode={} epoch={} body={} lastEpoch={}",
                    snapshot.mode(), snapshot.epoch(), snapshot.body(), lastEpoch);
            return;
        }
        boolean fresh = current == null || current.epoch() != snapshot.epoch();
        if (fresh) previousDirections = snapshot.mode() == ObserverPayloads.Mode.CORPSE && mc.player != null
                ? directionMask(mc) : 0;
        current = snapshot;
        if (snapshot.mode() == ObserverPayloads.Mode.CORPSE) {
            if (medicalBodyEntityId != snapshot.bodyEntity() || !snapshot.body().equals(medicalBodyId)) {
                medicalBodyEntityId = snapshot.bodyEntity(); medicalBodyId = snapshot.body(); medicalBody = null;
            }
        }
        lastEpoch = snapshot.epoch();
        if (fresh) returnHintShown = false;
        if (snapshot.mode() == ObserverPayloads.Mode.CORPSE && mc.level != null) {
            if (mc.player != null) {
                if (medicalAnchor == null || medicalAnchor.level() != mc.level)
                    medicalAnchor = new MedicalCameraAnchorEntity(mc.level);
                LivingEntity body = resolveSnapshotBody(mc, snapshot);
                if (medicalAnchorBodyChanged(body)) {
                    Entity camera = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
                    medicalAnchor.setYRot(camera.getYRot());
                    medicalAnchor.setXRot(camera.getXRot());
                }
                if (body != null) medicalAnchor.follow(body);
                mc.setCameraEntity(medicalAnchor);
            }
        }
        if (snapshot.returnAvailable() && !returnHintShown) {
            returnHintShown = true;
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("message.moonstation14.observer_return_available"), true);
        }
    }

    private static ObserverPayloads.Snapshot effectiveSnapshot() {
        boolean ghost = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl();
        boolean character = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalCharacterControl();
        Mob ownedCharacter = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        boolean matches = ownedCharacter != null && current != null && current.body().equals(ownedCharacter.getUUID());
        return ObserverMedicalScopePolicy.expose(ghost, character, matches) ? current : null;
    }

    public static ObserverPayloads.Snapshot current() { return effectiveSnapshot(); }
    /** Hide only this client's native carrier while its observer camera represents a distinct body. */
    public static boolean hideLocalCarrierModel(Entity candidate) {
        Minecraft mc = Minecraft.getInstance();
        ObserverPayloads.Snapshot snapshot = effectiveSnapshot();
        return snapshot != null && LocalCarrierRenderPolicy.hide(candidate != null && candidate == mc.player,
                snapshot.mode(), candidate == null ? null : candidate.getUUID(), snapshot.body());
    }
    public static boolean isGhostSnapshot(Entity entity) {
        ObserverPayloads.Snapshot snapshot = effectiveSnapshot();
        return entity != null && snapshot != null && snapshot.mode() == ObserverPayloads.Mode.GHOST
                && entity.getId() == snapshot.ghostEntity() && entity.getUUID().equals(snapshot.ghost());
    }

    @SubscribeEvent public static void blockGhostBodyInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (effectiveSnapshot() != null) event.setCanceled(true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { perClientTick(); }

    public static void tickCamera() { perClientTick(); }

    private static void perClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ObserverPayloads.Snapshot s = current;
        if (mc.player == null || mc.level == null) return;
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()) {
            // A stale medical snapshot must not retake the camera from canonical ghost control.
            Entity ghost = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedGhostForPresentation();
            if (medicalAnchor != null && mc.getCameraEntity() == medicalAnchor && ghost != null) mc.setCameraEntity(ghost);
            medicalAnchor = null;
            return;
        }
        if (!querySent) {
            PacketDistributor.sendToServer(new ObserverPayloads.Request(ObserverPayloads.Request.Kind.QUERY, Math.max(0, lastEpoch)));
            querySent = true;
        }
        if (s == null) {
            Mob owned = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
            LivingEntity target = owned != null ? owned : (!mc.player.isSpectator() ? mc.player : null);
            MobStateAttachment state = target == null ? null : target.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
            MobStateComponent.State value = state == null ? MobStateComponent.State.ALIVE : state.data().state();
            boolean critical = target != null && (value == MobStateComponent.State.CRITICAL || value == MobStateComponent.State.DEAD);
            if (critical && target != null) {
                boolean enteringMedicalCamera = medicalAnchor == null || mc.getCameraEntity() != medicalAnchor;
                if (medicalAnchor == null || medicalAnchor.level() != mc.level) medicalAnchor = new MedicalCameraAnchorEntity(mc.level);
                if (mc.getCameraEntity() != medicalAnchor) {
                    Entity camera = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
                    medicalAnchor.setYRot(camera.getYRot()); medicalAnchor.setXRot(camera.getXRot());
                }
                medicalAnchor.follow(target); mc.setCameraEntity(medicalAnchor);
                if (enteringMedicalCamera) MoonStation14.LOGGER.info("[observer client] medical camera ENTER body={} state={}", target.getUUID(), value);
            } else if (medicalAnchor != null) {
                boolean leavingMedicalCamera = mc.getCameraEntity() == medicalAnchor;
                LivingEntity previousBody = observedBody();
                if (mc.getCameraEntity() == medicalAnchor) mc.setCameraEntity(target != null ? target : mc.player);
                medicalAnchor = null;
                medicalLookEntityId = -1; medicalLookBodyId = null;
                if (leavingMedicalCamera) MoonStation14.LOGGER.info("[observer client] medical camera EXIT body={} state={}",
                        previousBody == null ? "unresolved" : previousBody.getUUID(), value);
            }
            return;
        }
        if (s.mode() == ObserverPayloads.Mode.CORPSE) {
            LivingEntity body = resolveSnapshotBody(mc, s);
            if (medicalAnchor != null && body != null) { medicalAnchor.follow(body); mc.setCameraEntity(medicalAnchor); }
            if (mc.screen == null) {
                int directions = directionMask(mc);
                if ((directions & ~previousDirections) != 0)
                    PacketDistributor.sendToServer(new ObserverPayloads.Request(ObserverPayloads.Request.Kind.GHOST, s.epoch()));
                previousDirections = directions;
            }
            return;
        }
        if (s.mode() != ObserverPayloads.Mode.GHOST) return;
        Entity ghost = mc.level.getEntity(s.ghostEntity());
        if (ghost == null || !ghost.getUUID().equals(s.ghost())) return;
        initializeGhostLook(mc, s, ghost);
        if (mc.getCameraEntity() != ghost) mc.setCameraEntity(ghost);
        projectCameraLook(ghost);
        if (mc.screen != null) return;
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalMindCarrier()
                && ghost.getId() == s.ghostEntity() && ghost.getUUID().equals(s.ghost())) return;
        LocalPlayer p = mc.player;
        int strafe = (mc.options.keyLeft.isDown() ? 1000 : 0) - (mc.options.keyRight.isDown() ? 1000 : 0);
        int forward = (mc.options.keyUp.isDown() ? 1000 : 0) - (mc.options.keyDown.isDown() ? 1000 : 0);
        int vertical = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        PacketDistributor.sendToServer(ObserverPayloads.Intent.fromCamera(s.epoch(), strafe, forward, vertical, mc.options.keySprint.isDown(), ghostLookYaw));
        if (s.returnAvailable() && RETURN.consumeClick()) PacketDistributor.sendToServer(new ObserverPayloads.Request(ObserverPayloads.Request.Kind.RETURN, s.epoch()));
    }

    public static boolean usesMedicalCamera() {
        return !com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()
                && medicalAnchor != null && Minecraft.getInstance().getCameraEntity() == medicalAnchor;
    }
    /** Exact UUID-bound look from the active observer ghost snapshot. */
    public static float[] lookForGhost(Entity entity) {
        ObserverPayloads.Snapshot s = current;
        if (entity == null || s == null || s.mode() != ObserverPayloads.Mode.GHOST
                || entity.getId() != s.ghostEntity() || !entity.getUUID().equals(s.ghost())) return null;
        initializeGhostLook(Minecraft.getInstance(), s, entity);
        return new float[] { ghostLookYaw, ghostLookPitch };
    }

    /** Seeds the active observer camera's private look state for an authoritative teleport result. */
    public static void applyAuthoritativeGhostLook(Entity entity, float yaw, float pitch) {
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()) return;
        ObserverPayloads.Snapshot snapshot = current;
        if (entity == null || snapshot == null || snapshot.mode() != ObserverPayloads.Mode.GHOST
                || entity.getId() != snapshot.ghostEntity() || !entity.getUUID().equals(snapshot.ghost())
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        ghostLookYaw = yaw;
        ghostLookPitch = Mth.clamp(pitch, -90f, 90f);
        ghostLookEpoch = snapshot.epoch();
        ghostLookId = entity.getUUID();
        ghostLookInitialized = true;
        projectCameraLook(entity);
    }

    /** Resolved movement policy cached against the exact observer corpse snapshot identity. */
    public static com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent movementForGhost(Entity entity) {
        ObserverPayloads.Snapshot s = current;
        if (entity == null || s == null || s.mode() != ObserverPayloads.Mode.GHOST
                || entity.getId() != s.ghostEntity() || !entity.getUUID().equals(s.ghost())) return null;
        if (ghostMovementEpoch == s.epoch() && s.ghost().equals(ghostMovementId)) return ghostMovement;
        LivingEntity corpse = resolveSnapshotBody(Minecraft.getInstance(), s);
        if (corpse == null || !corpse.getUUID().equals(s.body())) return null;
        var data = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(corpse).orElse(null);
        ghostMovement = data == null ? null : data.component(
                com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent.class).orElse(null);
        if (ghostMovement != null) { ghostMovementEpoch = s.epoch(); ghostMovementId = s.ghost(); }
        return ghostMovement;
    }
    /** Resolves the body whose synchronized medical state is presented by the current camera. */
    public static LivingEntity observedBody() {
        ObserverPayloads.Snapshot snapshot = effectiveSnapshot();
        if (snapshot != null && snapshot.mode() == ObserverPayloads.Mode.GHOST) return null;
        Minecraft mc = Minecraft.getInstance();
        if (snapshot != null && snapshot.mode() == ObserverPayloads.Mode.CORPSE) return resolveSnapshotBody(mc, snapshot);
        if (usesMedicalCamera()) {
            Mob owned = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
            return owned != null ? owned : mc.player != null && !mc.player.isSpectator() ? mc.player : null;
        }
        return mc.getCameraEntity() instanceof LivingEntity living ? living : null;
    }
    /** Exact body identity for status presentation, including the original corpse during GHOST mode. */
    public static LivingEntity statusBody() {
        Minecraft mc = Minecraft.getInstance();
        ObserverPayloads.Snapshot snapshot = effectiveSnapshot();
        return snapshot == null ? null : resolveSnapshotBody(mc, snapshot);
    }
    public static boolean isObservedMedicalBody(Entity entity) { return entity != null && entity == observedBody(); }

    private static LivingEntity resolveSnapshotBody(Minecraft mc, ObserverPayloads.Snapshot s) {
        if (mc.level == null || s.bodyEntity() < 0) return null;
        Entity entity = mc.level.getEntity(s.bodyEntity());
        if (!(entity instanceof LivingEntity living) || !living.getUUID().equals(s.body())) return null;
        medicalBody = living; return living;
    }
    private static boolean medicalAnchorBodyChanged(LivingEntity body) {
        if (body == null) return false;
        if (medicalLookEntityId == body.getId() && body.getUUID().equals(medicalLookBodyId)) return false;
        medicalLookEntityId = body.getId(); medicalLookBodyId = body.getUUID(); return true;
    }
    private static Entity resolveEndBody(Minecraft mc) {
        Mob owned = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        if (owned != null) return owned;
        if (medicalBodyEntityId >= 0 && mc.level != null) {
            Entity entity = mc.level.getEntity(medicalBodyEntityId);
            if (entity instanceof LivingEntity && entity.getUUID().equals(medicalBodyId)) return entity;
        }
        return mc.player;
    }
    public static Entity medicalCamera() { return medicalAnchor; }
    public static void turnCamera(double yaw, double pitch) {
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()) return;
        if (current != null && current.mode() == ObserverPayloads.Mode.GHOST) {
            Minecraft mc = Minecraft.getInstance();
            Entity ghost = mc.level == null ? null : mc.level.getEntity(current.ghostEntity());
            if (ghost == null || !ghost.getUUID().equals(current.ghost())) return;
            initializeGhostLook(mc, current, ghost);
            ghostLookYaw += (float)(yaw * 0.15);
            ghostLookPitch = Mth.clamp(ghostLookPitch + (float)(pitch * 0.15), -90f, 90f);
            projectCameraLook(ghost);
        } else if (medicalAnchor != null) medicalAnchor.turn(yaw, pitch);
    }

    public static void projectCameraLookForRenderFrame() {
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()) return;
        Minecraft mc = Minecraft.getInstance();
        ObserverPayloads.Snapshot s = current;
        if (s == null || s.mode() != ObserverPayloads.Mode.GHOST || mc.level == null) return;
        Entity ghost = mc.level.getEntity(s.ghostEntity());
        if (ghost == null || !ghost.getUUID().equals(s.ghost())) return;
        initializeGhostLook(mc, s, ghost);
        projectCameraLook(ghost);
    }

    private static void initializeGhostLook(Minecraft mc, ObserverPayloads.Snapshot s, Entity ghost) {
        if (ghostLookInitialized && ghostLookEpoch == s.epoch() && ghost.getUUID().equals(ghostLookId)) return;
        Entity heading = medicalAnchor != null ? medicalAnchor : mc.getCameraEntity();
        ghostLookYaw = heading == null ? mc.gameRenderer.getMainCamera().getYRot() : heading.getYRot();
        ghostLookPitch = Mth.clamp(heading == null ? mc.gameRenderer.getMainCamera().getXRot() : heading.getXRot(), -90f, 90f);
        ghostLookEpoch = s.epoch(); ghostLookId = ghost.getUUID(); ghostLookInitialized = true;
    }

    private static void projectCameraLook(Entity ghost) {
        if (ghost instanceof LivingEntity living)
            com.juicyslew.moonstation14.ms14.medical_observer.ObserverGhostLook.apply(living, ghostLookYaw, ghostLookPitch);
        else { ghost.setYRot(ghostLookYaw); ghost.setXRot(ghostLookPitch); ghost.yRotO = ghostLookYaw; ghost.xRotO = ghostLookPitch; }
    }

    private static void resetGhostLook() {
        ghostLookInitialized = false; ghostLookEpoch = -1; ghostLookId = null;
    }
    private static void resetGhostMovement() { ghostMovementEpoch = -1; ghostMovementId = null; ghostMovement = null; }

    private static int directionMask(Minecraft mc) {
        return (mc.options.keyUp.isDown() ? 1 : 0) | (mc.options.keyDown.isDown() ? 2 : 0)
                | (mc.options.keyLeft.isDown() ? 4 : 0) | (mc.options.keyRight.isDown() ? 8 : 0);
    }

    private static void reset() { Minecraft mc = Minecraft.getInstance(); if (medicalAnchor != null && mc.getCameraEntity() == medicalAnchor && mc.player != null) mc.setCameraEntity(mc.player); current = null; resetGhostLook(); resetGhostMovement(); previousDirections = 0; returnHintShown = false; querySent = false; medicalAnchor = null; }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { reset(); lastEpoch = -1; }
    @SubscribeEvent public static void levelLeaving(ClientPlayerNetworkEvent.LoggingOut event) { reset(); lastEpoch = -1; }
    @SubscribeEvent public static void levelEntering(ClientPlayerNetworkEvent.LoggingIn event) { reset(); lastEpoch = -1; }
}

