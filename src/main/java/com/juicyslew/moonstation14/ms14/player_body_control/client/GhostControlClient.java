package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Narrow client input bridge for the isolated ghost harness experiment. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class GhostControlClient {
    private static final GhostMovementMotor MOVEMENT_MOTOR = new GhostMovementMotor();
    private static GhostMovementMotor observerMovementMotor;
    private static long highestEpoch;
    private static long pendingEpoch;
    private static long acceptedBeginEpoch;
    private static int ghostEntityId = -1;
    private static MobHarnessKind harnessKind;
    private static long offeredEpoch;
    private static int offeredEntityId = -1;
    private static MobHarnessKind offeredKind;
    private static boolean offerReadySent;
    private static float latestSurfaceFactor = 1f;
    private static float latestVoluntaryFactor = 1f;
    private static boolean latestMovementBlocked;
    private static long activeEpoch;
    private static long sentSequence;
    private static boolean readySent;
    private static boolean committed;
    private static LocalPlayer owner;
    private static Level ownerLevel;
    private static GhostPredictionHistory predictionHistory;
    private static boolean characterPredictionStopped;
    private static boolean predictionMedicalSuspended;
    private static GhostControlPayloads.Snapshot.MovementMode characterMovementMode = GhostControlPayloads.Snapshot.MovementMode.TRANSIT;
    private static final TransitInterpolationPolicy transitInterpolation = new TransitInterpolationPolicy();
    private static final ExternalFlightPredictionPolicy externalFlightPrediction = new ExternalFlightPredictionPolicy();
    private static GhostControlPayloads.Begin deferredBegin;

    static {
        // This class is discovered only on the physical client due to its Dist.CLIENT subscriber annotation.
        GhostControlNetworking.installClientHandler(GhostControlClient::onPayload);
    }

    private GhostControlClient() { }

    private static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload instanceof GhostControlPayloads.Begin begin) {
            if (minecraft.player == null || minecraft.level == null || context.player() != minecraft.player) {
                deferredBegin = ClientBeginPolicy.defer(deferredBegin, begin, highestEpoch);
                return;
            }
            if (!(context.player() instanceof LocalPlayer player)
                    || !ClientBeginPolicy.canAcceptPayload(begin, highestEpoch, true)) return;
            acceptBegin(minecraft, player, begin);
            return;
        }
        if (!(context.player() instanceof LocalPlayer player) || player != minecraft.player || minecraft.level == null)
            return;

        if (payload instanceof GhostControlPayloads.Offer offer) {
            // Offer is advisory only: preserve the current committed body and fail closed on stale or same-kind offers.
            if (!committed || owner != player || ownerLevel != minecraft.level
                    || !GhostControlNetworking.matchesOffer(activeEpoch, ghostEntityId, harnessKind, offer)) return;
            offeredEpoch = offer.currentEpoch();
            offeredEntityId = offer.targetEntityId();
            offeredKind = offer.targetKind();
            offerReadySent = false;
            tryOfferReady(minecraft, player);
        } else if (payload instanceof GhostControlPayloads.Commit commit) {
            if (pendingEpoch == 0 || !readySent || commit.epoch() != pendingEpoch || owner != player
                    || ownerLevel != minecraft.level || !player.isSpectator()) return;
            Entity ghost = currentBody(minecraft, ghostEntityId, harnessKind);
            if (ghost != null && minecraft.getCameraEntity() == ghost) {
                activeEpoch = pendingEpoch;
                committed = true;
                sentSequence = 0;
                predictionHistory = new GhostPredictionHistory(activeEpoch, ghostEntityId, harnessKind);
                characterPredictionStopped = false;
                characterMovementMode = harnessKind == MobHarnessKind.CHARACTER
                        ? GhostControlPayloads.Snapshot.MovementMode.TRANSIT
                        : GhostControlPayloads.Snapshot.MovementMode.GROUNDED;
            }
        } else if (payload instanceof GhostControlPayloads.Stop stop) {
            if (pendingEpoch == stop.epoch() && owner == player) clearSession(minecraft, true);
        } else if (payload instanceof GhostControlPayloads.Snapshot snapshot) {
            reconcileSnapshot(minecraft, player, snapshot);
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ContainedCharacterClientView.reconcileCamera(minecraft);
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            if (owner != null) clearSession(minecraft, true);
            owner = null;
            ownerLevel = null;
            return;
        }
        if (owner != null && (owner != player || ownerLevel != minecraft.level
                || (readySent && !player.isSpectator()))) {
            clearSession(minecraft, true);
            owner = null;
            ownerLevel = null;
            return;
        }
        if (ClientBeginPolicy.canAccept(deferredBegin, highestEpoch, true)) {
            GhostControlPayloads.Begin begin = deferredBegin;
            deferredBegin = null;
            acceptBegin(minecraft, player, begin);
        }
        if (pendingEpoch == 0 || owner != player || ownerLevel != minecraft.level) return;
        tryOfferReady(minecraft, player);
        if (!player.isSpectator()) return;
        tryReady(minecraft, player);
        if (!committed || activeEpoch != pendingEpoch || !readySent || player.input == null) return;

        Entity ghost = currentBody(minecraft, ghostEntityId, harnessKind);
        if (ghost == null) return;
        MedicalPredictionPolicy.Decision medical = medicalDecision(ghost);
        if (minecraft.getCameraEntity() != ghost) return;
        if (sentSequence == Long.MAX_VALUE) {
            clearSession(minecraft, true);
            return;
        }

        var ordinaryInput=new com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent.State(
                player.input.leftImpulse,player.input.forwardImpulse,player.input.jumping,player.input.shiftKeyDown,player.isSprinting());
        float[] observerLook = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.lookForGhost(ghost);
        float yaw = normalizeYaw(observerLook == null ? player.getYRot() : observerLook[0]);
        float pitch = Math.max(-90f, Math.min(90f, finiteOrZero(observerLook == null ? player.getXRot() : observerLook[1])));
        if (!Float.isFinite(yaw)) return;
        // Keep the camera-facing ghost responsive locally while authoritative rotation is in flight.
        if (ghost instanceof GhostMobHarnessEntity ghostBody) setOwnedBodyLook(ghostBody, yaw, pitch);
        else if (ghost instanceof Mob mob) setOwnedBodyLook(mob, yaw, pitch);
        if (predictionHistory == null) return;
        long sequence=++sentSequence;
        GameplayWindowMovementDispatch.dispatch(activeEpoch,sequence,harnessKind,minecraft.screen,ordinaryInput,yaw,pitch,intent->{
            if (!medical.recordHistory()) { predictionHistory.skip(intent); return true; }
            if (predictionHistory.record(intent) != GhostPredictionHistory.RecordResult.RECORDED) return false;
            predict(ghost,intent,true);
            return true;
        },GhostControlNetworking::sendToServer);
    }

    /** Projects the local spectator's render-frame look onto only its committed ghost camera target. */
    public static void projectOwnedGhostLookForRenderFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!committed || !readySent || predictionHistory == null || activeEpoch == 0
                || activeEpoch != pendingEpoch || player == null || owner != player || !player.isSpectator()
                || minecraft.level == null || ownerLevel != minecraft.level) return;

        Entity entity = currentBody(minecraft, ghostEntityId, harnessKind);
        if (entity == null || entity.getId() != ghostEntityId || minecraft.getCameraEntity() != entity) return;

        float[] observerLook = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.lookForGhost(entity);
        float yaw = normalizeYaw(observerLook == null ? player.getYRot() : observerLook[0]);
        if (!Float.isFinite(yaw)) return;
        float pitch = Math.max(-90f, Math.min(90f, finiteOrZero(observerLook == null ? player.getXRot() : observerLook[1])));
        if (entity instanceof GhostMobHarnessEntity ghost) setOwnedBodyLook(ghost, yaw, pitch);
        else if (entity instanceof Mob mob) setOwnedBodyLook(mob, yaw, pitch);
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft minecraft = Minecraft.getInstance();
        clearSession(minecraft, true);
        highestEpoch = 0;
        deferredBegin = ClientBeginPolicy.afterLogout();
    }

    static final class ClientBeginPolicy {
        private ClientBeginPolicy() { }

        static GhostControlPayloads.Begin defer(GhostControlPayloads.Begin current,
                                                GhostControlPayloads.Begin incoming, long highestEpoch) {
            if (incoming.mindEpoch() <= highestEpoch) return current;
            return current == null || incoming.mindEpoch() > current.mindEpoch() ? incoming : current;
        }

        static boolean canAccept(GhostControlPayloads.Begin deferred, long highestEpoch, boolean ownerAndLevelReady) {
            return ownerAndLevelReady && deferred != null && deferred.mindEpoch() > highestEpoch;
        }

        static boolean canAcceptPayload(GhostControlPayloads.Begin begin, long highestEpoch, boolean matchingContext) {
            return matchingContext && begin.mindEpoch() > highestEpoch;
        }

        static GhostControlPayloads.Begin afterSessionClear(GhostControlPayloads.Begin deferred) { return deferred; }

        static GhostControlPayloads.Begin afterLogout() { return null; }
    }

    static final class ClientReadyPolicy {
        private ClientReadyPolicy() { }
        static boolean canSend(long beginEpoch, long pendingEpoch, boolean entityPresent,
                               boolean spectator, boolean matchingOwnerLevel) {
            return beginEpoch > 0 && beginEpoch == pendingEpoch && entityPresent && spectator && matchingOwnerLevel;
        }
    }

    private static void acceptBegin(Minecraft minecraft, LocalPlayer player, GhostControlPayloads.Begin begin) {
        if (begin.mindEpoch() <= highestEpoch) return;
        clearSession(minecraft, false);
        highestEpoch = begin.mindEpoch();
        pendingEpoch = begin.mindEpoch();
        acceptedBeginEpoch = begin.mindEpoch();
        ghostEntityId = begin.harnessEntityId();
        harnessKind = begin.harnessKind();
        observerMovementMotor = harnessKind == MobHarnessKind.GHOST
                ? new GhostMovementMotor(begin.ghostWalkSpeed(), begin.ghostSprintSpeed(), begin.ghostSprintSpeed()) : null;
        owner = player;
        ownerLevel = minecraft.level;
        tryReady(minecraft, player);
    }

    private static void tryReady(Minecraft minecraft, LocalPlayer player) {
        Entity body = currentBody(minecraft, ghostEntityId, harnessKind);
        if (readySent || !ClientReadyPolicy.canSend(acceptedBeginEpoch, pendingEpoch, body != null,
                player.isSpectator(), owner == player && ownerLevel == minecraft.level)) return;
        readySent = true;
        GhostControlNetworking.sendToServer(new GhostControlPayloads.Ready(pendingEpoch));
    }

    private static void tryOfferReady(Minecraft minecraft, LocalPlayer player) {
        if (offerReadySent || offeredEpoch == 0 || !committed || activeEpoch != offeredEpoch || owner != player
                || ownerLevel != minecraft.level || !player.isSpectator()) return;
        if (offeredKind == harnessKind || offeredEntityId == ghostEntityId
                || currentBody(minecraft, offeredEntityId, offeredKind) == null) return;
        offerReadySent = true;
        GhostControlNetworking.sendToServer(new GhostControlPayloads.OfferReady(
                offeredEpoch, offeredEntityId, offeredKind));
    }

    private static Entity currentBody(Minecraft minecraft, int id, MobHarnessKind kind) {
        if (minecraft.level == null || id < 0 || kind == null) return null;
        Entity entity = minecraft.level.getEntity(id);
        if (kind == MobHarnessKind.GHOST) return entity instanceof GhostMobHarnessEntity ? entity : null;
        if (entity instanceof PlayerCharacterHarnessEntity body)
            return characterBodyRecognized(characterPolicy(body) != null, false) ? body : null;
        if (!(entity instanceof Mob mob) || entity instanceof GhostMobHarnessEntity) return null;
        return characterBodyRecognized(false, characterPolicy(mob) != null) ? mob : null;
    }

    static boolean characterBodyRecognized(boolean registeredPlayerCharacter, boolean mappedLegacyPolicyAvailable) {
        return registeredPlayerCharacter || mappedLegacyPolicyAvailable;
    }

    private static CharacterMovementPolicy characterPolicy(Mob mob) {
        try {
            return CharacterIdentitySystem.projectForActor(mob)
                    .map(CharacterMovementPolicy::fromCharacterData).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void clearSession(Minecraft minecraft, boolean restoreCamera) {
        ContainedCharacterClientView.clear();
        Entity ghost = ghostEntityId < 0 ? null : currentBody(minecraft, ghostEntityId, harnessKind);
        if (restoreCamera && ghost != null && minecraft.getCameraEntity() == ghost
                && minecraft.player != null) minecraft.setCameraEntity(minecraft.player);
        pendingEpoch = 0;
        acceptedBeginEpoch = 0;
        ghostEntityId = -1;
        activeEpoch = 0;
        sentSequence = 0;
        readySent = false;
        committed = false;
        harnessKind = null;
        offeredEpoch = 0;
        offeredEntityId = -1;
        offeredKind = null;
        offerReadySent = false;
        predictionHistory = null;
        observerMovementMotor = null;
        characterPredictionStopped = false;
        predictionMedicalSuspended = false;
        characterMovementMode = GhostControlPayloads.Snapshot.MovementMode.TRANSIT;
        transitInterpolation.clear();
        latestSurfaceFactor = 1f;
        latestVoluntaryFactor = 1f;
        latestMovementBlocked = false;
        externalFlightPrediction.clear();
        owner = null;
        ownerLevel = null;
        deferredBegin = ClientBeginPolicy.afterSessionClear(deferredBegin);
    }

    /** Returns the exact locally owned prediction target, or null when vanilla must own tracking. */
    public static Entity ownedGhostForTracker(Entity target) {
        return ownedHarnessForTracker(target);
    }

    /** Exact-identity guard shared by ghost and configured-character tracker handling. */
    public static Entity ownedHarnessForTracker(Entity target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!committed || !readySent || predictionHistory == null || activeEpoch == 0
                || activeEpoch != pendingEpoch || target == null || target.getId() != ghostEntityId
                || minecraft.player == null || owner != minecraft.player || !owner.isSpectator()
                || minecraft.level == null || ownerLevel != minecraft.level)
            return null;

        Entity ghost = currentBody(minecraft, ghostEntityId, harnessKind);
        if (ghost == null || ghost != target) return null;
        MedicalPredictionPolicy.Decision medical = medicalDecision(ghost);
        return medical.ownsTracker() && minecraft.getCameraEntity() == ghost ? ghost : null;
    }

    /** Returns the committed, locally owned CHARACTER body for read-only HUD presentation. */
    public static Mob ownedCharacterForHud() {
        if (harnessKind != MobHarnessKind.CHARACTER) return null;
        Entity owned = ownedHarnessForTracker(currentBody(Minecraft.getInstance(), ghostEntityId, harnessKind));
        return owned instanceof Mob mob && !(mob instanceof GhostMobHarnessEntity) ? mob : null;
    }

    public static boolean isOwnedMedicalObserverGhost(Entity entity) {
        return entity instanceof GhostMobHarnessEntity && harnessKind == MobHarnessKind.GHOST
                && committed && readySent && activeEpoch > 0 && activeEpoch == pendingEpoch
                && entity.getId() == ghostEntityId && currentBody(Minecraft.getInstance(), ghostEntityId, harnessKind) == entity;
    }

    /** Stable presentation identity; unlike tracker ownership this does not depend on the active camera. */
    public static Mob ownedCharacterForPresentation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!committed || !readySent || activeEpoch == 0 || activeEpoch != pendingEpoch
                || harnessKind != MobHarnessKind.CHARACTER || minecraft.player == null
                || owner != minecraft.player || minecraft.level == null || ownerLevel != minecraft.level)
            return null;

        Entity body = currentBody(minecraft, ghostEntityId, harnessKind);
        return body instanceof Mob mob && !(mob instanceof GhostMobHarnessEntity) ? mob : null;
    }

    public static long presentationCharacterEpoch() {
        return ownedCharacterForPresentation() == null ? 0 : activeEpoch;
    }

    /** True while this exact local player and level are in a pending or committed Mind session. */
    public static boolean isLocalMindCarrier() {
        Minecraft minecraft = Minecraft.getInstance();
        return pendingEpoch != 0 && owner != null && owner == minecraft.player
                && minecraft.player != null && minecraft.level != null && ownerLevel == minecraft.level;
    }

    /** Applies an authority barrier only to the still-owned ghost after vanilla's teleport handling. */
    public static void onOwnedGhostTeleport(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity target = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (ownedHarnessForTracker(target) != null && predictionHistory != null)
            predictionHistory.onAuthorityBarrier();
    }

    private static float normalizeYaw(float yaw) {
        if (!Float.isFinite(yaw)) return Float.NaN;
        float normalized = yaw % 360f;
        if (normalized > 180f) normalized -= 360f;
        if (normalized < -180f) normalized += 360f;
        return normalized;
    }

    private static float finiteOrZero(float value) { return Float.isFinite(value) ? value : 0f; }

    /** CHARACTER uses Shift as walk; GHOST retains Minecraft's sprint input. */
    static int intentButtons(MobHarnessKind kind, boolean jumping, boolean shiftKeyDown, boolean minecraftSprinting) {
        boolean fast = kind == MobHarnessKind.CHARACTER ? !shiftKeyDown : minecraftSprinting;
        return (jumping ? GhostControlPayloads.BUTTON_JUMP : 0)
                | (kind == MobHarnessKind.CHARACTER && shiftKeyDown ? GhostControlPayloads.BUTTON_SNEAK : 0)
                | (fast ? GhostControlPayloads.BUTTON_SPRINT : 0);
    }

    /** Epoch of the exact committed CHARACTER camera session, or zero when it is not owned. */
    public static long committedCharacterEpoch() {
        return ownedCharacterForHud() == null ? 0 : activeEpoch;
    }

    record OwnedCharacterBinding(java.util.UUID body, long epoch) { }
    static OwnedCharacterBinding ownedCharacterBinding() {
        Mob body = ownedCharacterForHud();
        return body == null ? null : new OwnedCharacterBinding(body.getUUID(), activeEpoch);
    }

    private static void reconcileSnapshot(Minecraft minecraft, LocalPlayer player,
                                          GhostControlPayloads.Snapshot snapshot) {
        if (!committed || !readySent || owner != player || minecraft.player != player || minecraft.level == null
                || ownerLevel != minecraft.level || !player.isSpectator() || activeEpoch != pendingEpoch
                || snapshot.epoch() != activeEpoch || snapshot.harnessEntityId() != ghostEntityId
                || snapshot.harnessKind() != harnessKind
                || predictionHistory == null) return;
        Entity entity = currentBody(minecraft, ghostEntityId, harnessKind);
        if (entity == null) return;
        MedicalPredictionPolicy.Decision medical = medicalDecision(entity);
        if (minecraft.getCameraEntity() != entity) return;

        GhostPredictionHistory.SnapshotResult result = predictionHistory.reconcile(snapshot);
        if (result.status() == GhostPredictionHistory.SnapshotStatus.REJECTED) return;
        if (characterPredictionStopped) predictionHistory.onAuthorityBarrier();
        characterMovementMode = snapshot.movementMode();
        if (harnessKind == MobHarnessKind.CHARACTER)
            ContainedCharacterClientView.update(entity.getUUID(), activeEpoch, snapshot.containedView().orElse(null));

        // Pulled targets are positioned by vanilla's remote entity tracker, not this
        // controller. Snapshot velocity may seed later recovery, but never teleport them.
        if (!medical.recordHistory()) {
            latestSurfaceFactor = snapshot.surfaceFactor();
            latestVoluntaryFactor = snapshot.voluntarySpeedFactor();
            latestMovementBlocked = snapshot.movementBlocked();
            externalFlightPrediction.reset(snapshot.externalFlightTicks());
            entity.setDeltaMovement(entity instanceof GhostMobHarnessEntity ? Vec3.ZERO : new Vec3(
                    snapshot.velocityX() / 20d, snapshot.velocityY() / 20d, snapshot.velocityZ() / 20d));
            return;
        }

        float visualYaw = entity.getYRot();
        float visualPitch = entity.getXRot();
        boolean transitCharacter = harnessKind == MobHarnessKind.CHARACTER
                && snapshot.movementMode() == GhostControlPayloads.Snapshot.MovementMode.TRANSIT;
        int interpolationSteps = harnessKind == MobHarnessKind.CHARACTER
                ? transitInterpolation.accept(activeEpoch, entity.getUUID(), snapshot.movementMode(),
                    snapshot.containedView().map(com.juicyslew.moonstation14.ms14.containment.ContainedBodyView::ownerIdentity)
                            .orElse(null))
                : 0;
        if (transitCharacter && interpolationSteps > 0) {
            // Keep the server sample authoritative, but let Entity's normal client tick interpolate it
            // rather than teleporting the camera target on every transit snapshot.
            entity.lerpTo(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch(), interpolationSteps);
        } else {
            entity.setPos(snapshot.x(), snapshot.y(), snapshot.z());
            entity.lerpTo(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch(), 0);
        }
        latestSurfaceFactor = snapshot.surfaceFactor();
        latestVoluntaryFactor = snapshot.voluntarySpeedFactor();
        latestMovementBlocked = snapshot.movementBlocked();
        externalFlightPrediction.reset(snapshot.externalFlightTicks());
        if (entity instanceof Mob) entity.setOnGround(snapshot.onGround());
        entity.setDeltaMovement(entity instanceof GhostMobHarnessEntity || isCharacterContainedMode() ? Vec3.ZERO : new Vec3(
                snapshot.velocityX() / 20d, snapshot.velocityY() / 20d, snapshot.velocityZ() / 20d));
        if (entity instanceof GhostMobHarnessEntity ghost) setOwnedBodyLook(ghost, snapshot.yaw(), snapshot.pitch());
        else if (entity instanceof Mob mob) setOwnedBodyLook(mob, snapshot.yaw(), snapshot.pitch());
        if (CharacterMovementSnapshotPolicy.requiresBarrier(characterMovementMode)) predictionHistory.onAuthorityBarrier();
        else if (CharacterMovementSnapshotPolicy.allowsReplay(characterMovementMode, result.status(), characterPredictionStopped)
                && medical.replaySnapshot()) {
            for (GhostControlPayloads.Intent intent : result.replayFrames()) predict(entity, intent, false);
        }
        // Preserve the local view; movement replay still uses each intent's quantized yaw.
        if (entity instanceof GhostMobHarnessEntity ghost) setOwnedBodyLook(ghost, visualYaw, visualPitch);
        else if (entity instanceof Mob mob) setOwnedBodyLook(mob, visualYaw, visualPitch);
    }

    private static void predict(Entity body, GhostControlPayloads.Intent intent, boolean animate) {
        if (body instanceof GhostMobHarnessEntity ghost) {
        ghost.setDeltaMovement(Vec3.ZERO);
        MovementVector position = new MovementVector(ghost.getX(), ghost.getY(), ghost.getZ());
        boolean sprint = (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0;
        GhostMovementMotor motor = observerMovementMotor;
        (motor == null ? MOVEMENT_MOTOR : observerMovementMotor).tick(position, intent.wishX(), intent.wishZ(), intent.verticalWish(), sprint, intent.yaw(),
                (before, requested, wasOnGround) -> {
                    Vec3 oldPosition = ghost.position();
                    ghost.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
                    Vec3 actual = ghost.position().subtract(oldPosition);
                    return new MovementCollisionResolver.CollisionResult(
                            new MovementVector(actual.x, actual.y, actual.z), ghost.onGround());
                });
        ghost.setDeltaMovement(Vec3.ZERO);
        } else if (body instanceof Mob mob) {
            if (!characterPredictionStopped && CharacterMovementSnapshotPolicy.allowsMotor(characterMovementMode))
                predictCharacter(mob, intent, animate);
        }
    }

    private static MovementVector tetherImpulse(Mob body, CharacterMovementState state) {
        Vec3 position = new Vec3(state.position().x(), state.position().y(), state.position().z());
        Vec3 velocityPerSecond = new Vec3(state.velocity().x(), state.velocity().y(), state.velocity().z());
        Vec3 impulse = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                .deltaVelocityPerSecond(body, position, velocityPerSecond);
        return new MovementVector(impulse.x, impulse.y, impulse.z);
    }

    private static MedicalPredictionPolicy.Decision medicalDecision(Entity entity) {
        var state = entity instanceof Mob mob
                ? mob.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get())
                : null;
        var medicalState = state == null ? com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.ALIVE
                : state.data().state();
        var rope = entity.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ROPE_CONSTRAINT.get());
        boolean externallyMoved = rope != null && rope.constraints().stream().anyMatch(c ->
                c.role() == com.juicyslew.moonstation14.ms14.loose_objects.pulling.RopeConstraintAttachment.EndpointRole.PULLED);
        MedicalPredictionPolicy.Decision decision = MedicalPredictionPolicy.decide(harnessKind, medicalState,
                predictionMedicalSuspended, false, externallyMoved);
        boolean barrierApplied = false;
        if (decision.authorityBarrierRequired()) {
            if (predictionHistory != null) {
                predictionHistory.onAuthorityBarrier();
                barrierApplied = true;
                decision = MedicalPredictionPolicy.decide(harnessKind, medicalState,
                        predictionMedicalSuspended, true, externallyMoved);
            }
        }
        if (!decision.authorityBarrierRequired() || barrierApplied)
            predictionMedicalSuspended = decision.suspended();
        return decision;
    }

    private static boolean isCharacterContainedMode() {
        return harnessKind == MobHarnessKind.CHARACTER
                && characterMovementMode != GhostControlPayloads.Snapshot.MovementMode.GROUNDED;
    }

    private static void predictCharacter(Mob body, GhostControlPayloads.Intent intent, boolean animate) {
        CharacterMovementPolicy policy = characterPolicy(body);
        if (policy == null) return;
        Vec3 initialPosition = body.position();
        Vec3 initialVelocity = body.getDeltaMovement();
        CharacterMovementState state = new CharacterMovementState(
                new MovementVector(body.getX(), body.getY(), body.getZ()),
                new MovementVector(initialVelocity.x * 20d, initialVelocity.y * 20d, initialVelocity.z * 20d),
                body.onGround());
        float surface = Math.max(0f, Math.min(64f, latestSurfaceFactor));
        float voluntary = Math.max(0f, Math.min(8f, latestVoluntaryFactor));
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(GroundedHarnessPhysics.TICK_SECONDS,
                GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED, GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND,
                GroundedHarnessPhysics.VERTICAL_DRAG, MovementVector.ZERO, 32d,
                body.maxUpStep(), (before, requested, wasOnGround) -> {
                    Vec3 old = body.position();
                    body.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
                    Vec3 actual = body.position().subtract(old);
                    return new MovementCollisionResolver.CollisionResult(
                            CharacterPredictionFailurePolicy.postMoveDisplacement(actual.x, actual.y, actual.z),
                            body.onGround());
                }, surface, voluntary, tetherImpulse(body, state));
        CharacterMovementState result = CharacterPredictionFailurePolicy.run(() ->
                new GroundedHarnessMotor(policy).tick(state, intent.wishX(), intent.wishZ(),
                        (intent.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0,
                        (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0,
                        intent.yaw(), environment, latestMovementBlocked, externalFlightPrediction.advanceFrame()), predictionHistory);
        if (result == null) {
            // The resolver may already have moved the entity. Never retry, roll back, or replay this frame.
            characterPredictionStopped = true;
            MoonStation14.LOGGER.error("[ghost control client] CHARACTER prediction stopped after post-move collision rejection; epoch={} entityId={} sequence={} phase={}; awaiting authoritative snapshots",
                    activeEpoch, ghostEntityId, intent.sequence(), animate ? "tick" : "replay");
            return;
        }
        body.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d,
                result.velocity().z() / 20d);
        if (animate) OwnedHarnessWalkAnimation.update(body, body.position().subtract(initialPosition));
    }

    /** Keeps every interpolated rotation used by the locally owned body camera on the current look. */
    private static void setOwnedBodyLook(LivingEntity body, float yaw, float pitch) {
        body.setYRot(yaw);
        body.setXRot(pitch);
        body.yRotO = yaw;
        body.xRotO = pitch;
        body.yBodyRot = yaw;
        body.yBodyRotO = yaw;
        body.yHeadRot = yaw;
        body.yHeadRotO = yaw;
    }
}
