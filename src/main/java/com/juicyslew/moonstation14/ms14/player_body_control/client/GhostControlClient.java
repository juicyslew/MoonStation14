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
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactPolicy;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer;
import com.juicyslew.moonstation14.ms14.player_body_control.CharacterCadenceDiagnostics;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import java.util.List;
import com.juicyslew.moonstation14.ms14.player_body_control.client.BodyMovementIntentScreen;
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
import com.juicyslew.moonstation14.ms14.doors.client.ClientAirlockPredictionSystem;

/** Narrow client input bridge for the isolated ghost harness experiment. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class GhostControlClient {
    static final String CONNECTED_WALL_FORMAT = "[CONNECTED_WALL] epoch={} body={} tick={} cameraPosition={} eventPartial={} logicalBody={} velocity={} yaw={} sentSeq={} historyAck={} historyRecordedSeq={} historyServerTick={} lastReconcileSummary={} lastNativeTick={} lastNativeSeq={} lastNativePhase={} airlockContacts={} native={} angular={} camera={}";
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
    private static java.util.UUID committedBodyUuid;
    private static LocalPlayer owner;
    private static Level ownerLevel;
    private static GhostPredictionHistory predictionHistory;
    private static final ClientFramePredictionState clientFrameState = new ClientFramePredictionState();
    private static final ClientOwnedSimulationClock ownedSimulationClock = new ClientOwnedSimulationClock();
    private static final ClientSupportedHeading supportedHeading = new ClientSupportedHeading();
    private static final AngularRecoveryPrediction angularPrediction = new AngularRecoveryPrediction();
    private static boolean characterPredictionStopped;
    private static long characterMovementRebaseRevision;
    private static long characterPredictionFailedSequence;
    private static long lastFramedDiagnosticEpoch = Long.MIN_VALUE;
    private static long lastFramedDiagnosticMillis;
    private static int framedDiagnosticCount;
    private static SableOwnedBodyMotion.MoveResult lastFramedNativeMove;
    private static boolean predictionMedicalSuspended;
    private static GhostControlPayloads.Snapshot.MovementMode characterMovementMode = GhostControlPayloads.Snapshot.MovementMode.TRANSIT;
    private static final TransitInterpolationPolicy transitInterpolation = new TransitInterpolationPolicy();
    private static final ExternalFlightPredictionPolicy externalFlightPrediction = new ExternalFlightPredictionPolicy();
    private static GhostControlPayloads.Begin deferredBegin;
    private static final CharacterCadenceDiagnostics characterCadence = new CharacterCadenceDiagnostics();
    private static long cadenceTick, lastCadenceTick;
    private static short previousWishX, previousWishZ;
    private static boolean previousJump;
    private static int previousButtons;
    private static String lastCadenceSnapshotStatus = "";
    private static String latestReconciliationDiagnostic = "status=none";
    private static int cadenceSamplesSuppressed;
    private static long previousClientTickNano;
    private static long clientTickGapMillis;
    private static long maxClientTickGapMillis;
    private static final ConnectedWallCapture wallCapture = new ConnectedWallCapture();
    private static final ConnectedPredictionFixture connectedPredictionFixture = new ConnectedPredictionFixture();
    private static boolean connectedFixtureReported;
    private static long connectedFixtureReconciliationGeneration;
    private static Vec3 connectedFixtureTranslationVelocity;
    private static boolean connectedFixtureTranslationConsistent = true;
    private static long lastClientSendTraceTick = Long.MIN_VALUE;
    private static long lastNativeObservationClientTick = -1, lastNativeObservationIntentSeq = -1;
    private static String lastNativeObservationPhase = "unknown";
    private static long connectedWallCaptureEpoch;
    private static int connectedWallCaptureBody = -1;
    private static long lastReconcileSnapshotTick = -1, lastReconcileAck = -1;
    private static int lastReconcileReplayFrames = -1;
    private static long lastReconcileArrivalTick = -1;
    private static com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State latestDiagnosticSnapshotAngularState;
    private static long latestDiagnosticAngularSnapshotTick = -1, latestDiagnosticAngularSnapshotAck = -1;
    private static long vanillaMotionCount;
    private static Vec3 vanillaMotionAppliedVelocity;
    private static final ClientAirlockPredictionSystem airlockPredictions = new ClientAirlockPredictionSystem();

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
            if (context.player() instanceof LocalPlayer exact && owner == exact && ownerLevel == minecraft.level
                    && pendingEpoch == begin.mindEpoch() && acceptedBeginEpoch == begin.mindEpoch()
                    && ghostEntityId == begin.harnessEntityId() && harnessKind == begin.harnessKind()) {
                readySent = false; // Repeated server Begin is a bounded readiness retransmission request.
                tryReady(minecraft, exact);
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
                characterCadence.reset(); cadenceTick = 0; lastCadenceTick = 0;
                previousClientTickNano = 0; clientTickGapMillis = 0; maxClientTickGapMillis = 0;
                lastClientSendTraceTick = Long.MIN_VALUE;
                latestReconciliationDiagnostic = "status=none"; cadenceSamplesSuppressed = 0;
                resetConnectedWallMetadata();
                vanillaMotionCount = 0; vanillaMotionAppliedVelocity = null;
                committedBodyUuid = ghost.getUUID();
                committed = true;
                sentSequence = 0;
                lastFramedNativeMove = null;
                lastFramedDiagnosticEpoch = activeEpoch;
                lastFramedDiagnosticMillis = 0;
                framedDiagnosticCount = 0;
        predictionHistory = new GhostPredictionHistory(activeEpoch, ghostEntityId, harnessKind);
        clientFrameState.reset();
        ownedSimulationClock.reset();
        angularPrediction.reset();
                characterPredictionStopped = false;
                characterMovementRebaseRevision = 0;
                characterPredictionFailedSequence = 0;
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
        try {
            long now = System.nanoTime();
            clientTickGapMillis = previousClientTickNano == 0 ? 0 : Math.max(0, (now - previousClientTickNano) / 1_000_000L);
            previousClientTickNano = now;
            maxClientTickGapMillis = Math.max(maxClientTickGapMillis, clientTickGapMillis);
        } catch (RuntimeException ignored) { clientTickGapMillis = 0; }
        Minecraft minecraft = Minecraft.getInstance();
        connectedPredictionFixture.heartbeat(System.nanoTime());
        reportConnectedPredictionFixture(minecraft);
        ContainedCharacterClientView.reconcileCamera(minecraft);
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            connectedPredictionFixture.abort(ConnectedPredictionFixture.Failure.CONTEXT_CHANGED);
            reportConnectedPredictionFixture(minecraft);
            airlockPredictions.clear();
            wallCapture.reset();
            if (owner != null) clearSession(minecraft, true);
            owner = null;
            ownerLevel = null;
            return;
        }
        if (owner != null && (owner != player || ownerLevel != minecraft.level
                || (readySent && !player.isSpectator()))) {
            connectedPredictionFixture.abort(ConnectedPredictionFixture.Failure.CONTEXT_CHANGED);
            reportConnectedPredictionFixture(minecraft);
            wallCapture.reset();
            airlockPredictions.clear();
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
        if (pendingEpoch == 0 || owner != player || ownerLevel != minecraft.level) { airlockPredictions.clear(); return; }
        if (harnessKind == MobHarnessKind.CHARACTER) cadenceTick++;
        tryOfferReady(minecraft, player);
        if (!player.isSpectator()) { airlockPredictions.clear(); cadenceEarly("not-spectator", minecraft, player); return; }
        tryReady(minecraft, player);
        if (!committed || activeEpoch != pendingEpoch || !readySent || player.input == null) { airlockPredictions.clear(); cadenceEarly("not-committed-or-input-null", minecraft, player); return; }

        Entity ghost = currentBody(minecraft, ghostEntityId, harnessKind);
        if (ghost == null) { wallCapture.reset(); airlockPredictions.clear(); supportedHeading.resetForOwnershipGap(); cadenceEarly("ghost-not-found", minecraft, player); return; }
        if (harnessKind == MobHarnessKind.CHARACTER
                && wallCapture.mode() == ConnectedWallCapture.Mode.WAITING_JUMP)
            wallCapture.waiting(activeEpoch,ghost.getId(),ghost.getUUID(),cadenceTick);
        MedicalPredictionPolicy.Decision medical = medicalDecision(ghost);
        if (minecraft.getCameraEntity() != ghost) { wallCapture.reset(); airlockPredictions.clear(); supportedHeading.resetForOwnershipGap(); cadenceEarly("camera-mismatch", minecraft, player); return; }
        if (harnessKind == MobHarnessKind.CHARACTER && ghost instanceof Mob character)
            airlockPredictions.tick(activeEpoch, character.getId(), character.getUUID(), minecraft.level.getGameTime());
        else airlockPredictions.clear();
        if (sentSequence == Long.MAX_VALUE) {
            clearSession(minecraft, true);
            return;
        }

        // A vanilla Screen suppresses LocalPlayer.input; sample only the committed CHARACTER popup.
        var popup = harnessKind == MobHarnessKind.CHARACTER && minecraft.screen instanceof com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow screen
                && screen instanceof BodyMovementIntentScreen
                ? screen.movementIntent() : null;
        double strafe = popup == null ? player.input.leftImpulse : popup.left();
        double forward = popup == null ? player.input.forwardImpulse : popup.forward();
        boolean jumping = popup == null ? player.input.jumping : popup.jump();
        boolean sprinting = popup == null ? player.isSprinting() : popup.sprint();
        if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING) {
            var frame = harnessKind == MobHarnessKind.CHARACTER ? supportedFrame(minecraft, ghost, false, 0) : null;
            var sample = connectedPredictionFixture.input(cadenceTick, System.nanoTime(), activeEpoch,
                    ghost.getUUID(), minecraft.level.dimension().location().toString(), ghost.onGround(), frame == null ? null : frame.frameId());
            strafe = sample.wishX() / 1000d; forward = sample.wishZ() / 1000d;
            jumping = sample.jump(); sprinting = sample.sprint();
        }
        double length = Math.hypot(strafe, forward);
        if (length > 1d) { strafe /= length; forward /= length; }
        short wishX = (short) Math.max(-1000, Math.min(1000, Math.round(strafe * 1000d)));
        short wishZ = (short) Math.max(-1000, Math.min(1000, Math.round(forward * 1000d)));
        boolean shifting = popup == null ? player.input.shiftKeyDown : popup.shift();
        byte verticalWish = (byte) ((jumping ? 1 : 0) - (shifting ? 1 : 0));
        int buttons = intentButtons(harnessKind, jumping, shifting,
                sprinting);
        if (harnessKind == MobHarnessKind.CHARACTER) {
            boolean changed = wishX != previousWishX || wishZ != previousWishZ || jumping != previousJump || buttons != previousButtons;
            if (changed) {
                lastCadenceTick = cadenceTick; previousWishX = wishX; previousWishZ = wishZ; previousJump = jumping; previousButtons = buttons;
                cadenceLog(activeEpoch, true, "INPUT wish="+wishX+","+wishZ+" gapMillis="+clientTickGapMillis+" rawKeys="+
                        minecraft.options.keyUp.isDown()+","+minecraft.options.keyDown.isDown()+","+
                        minecraft.options.keyLeft.isDown()+","+minecraft.options.keyRight.isDown()+","+
                        minecraft.options.keyJump.isDown()+" input="+player.input.leftImpulse+","+
                        player.input.forwardImpulse+","+player.input.jumping+" buttons="+buttons+" screen="+
                        (minecraft.screen==null?"none":minecraft.screen.getClass().getName())+" focused="+
                        minecraft.isWindowActive()+" camera="+minecraft.getCameraEntity().getId());
            }
        }
        float[] observerLook = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.lookForGhost(ghost);
        float yaw = normalizeYaw(observerLook == null ? player.getYRot() : observerLook[0]);
        float pitch = Math.max(-90f, Math.min(90f, finiteOrZero(observerLook == null ? player.getXRot() : observerLook[1])));
        if (observerLook == null && harnessKind == MobHarnessKind.CHARACTER
                && characterMovementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED) {
            var support = supportedFrame(minecraft, ghost, false, 0);
            float delta = supportedHeading.carry(activeEpoch, ghost.getUUID(), support == null ? null : support.frameId,
                    support == null ? null : support.orientation, support != null,
                    medical.recordHistory() && !clientFrameState.paused());
            yaw = normalizeYaw(yaw + delta);
            player.setYRot(yaw);
            player.yRotO = yaw;
        } else if (observerLook != null || harnessKind != MobHarnessKind.CHARACTER
                || characterMovementMode != GhostControlPayloads.Snapshot.MovementMode.GROUNDED) {
            supportedHeading.reset();
        }
        if (!Float.isFinite(yaw)) return;
        // Keep the camera-facing ghost responsive locally while authoritative rotation is in flight.
        if (ghost instanceof GhostMobHarnessEntity ghostBody) setOwnedBodyLook(ghostBody, yaw, pitch);
        else if (ghost instanceof Mob mob) setOwnedBodyLook(mob, yaw, pitch);
        java.util.Optional<GhostControlPayloads.SupportedMovementReference> movementReference = java.util.Optional.empty();
        if (ghost instanceof PlayerCharacterHarnessEntity character && ghost.onGround()
                && characterMovementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED) {
            AngularSupport support = angularSupport(character);
            if (support != null) {
                var angular = character.angularRecoveryState().orElse(null);
                float worldMovementYaw = com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook
                        .worldPlanarYaw(angular, yaw);
                movementReference = java.util.Optional.of(
                        com.juicyslew.moonstation14.ms14.player_body_control.character.SupportedMovementYaw.capture(
                                support.frameId(),
                                com.juicyslew.moonstation14.ms14.standing.WorldUpTwist.degrees(support.logical()),
                                worldMovementYaw));
            }
        }
        GhostControlPayloads.Intent intent = new GhostControlPayloads.Intent(activeEpoch, ++sentSequence,
                wishX, wishZ, verticalWish, buttons, yaw, pitch, movementReference);
        if (predictionHistory == null) return;
        boolean nativeFrameUnavailable = false;
        if (harnessKind == MobHarnessKind.CHARACTER
                && characterMovementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED) {
            var basis = clientFrameState.velocityBasis();
            nativeFrameUnavailable = clientFrameState.paused() || basis == null
                    || com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.pointAtWorldPosition(
                    minecraft.level, basis.frameId(), ghost.position()) == null;
            if (nativeFrameUnavailable && !clientFrameState.paused()) {
                clientFrameState.pause();
                predictionHistory.onAuthorityBarrier();
            }
        }
        if (!medical.recordHistory() || nativeFrameUnavailable) {
            GhostPredictionHistory.RecordResult skipResult = predictionHistory.skip(intent);
            GhostControlNetworking.sendToServer(intent);
            if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING)
                connectedPredictionFixture.sent(new ConnectedPredictionFixture.Sent(activeEpoch, intent.sequence(), intent.wishX(), intent.wishZ(), jumping, cadenceTick, System.nanoTime()), cadenceTick);
            cadenceSendLog("SEND seq="+intent.sequence()+" result="+skipResult+" skip="+
                    (!medical.recordHistory()?"medical":"native-frame-unavailable")+" mode="+characterMovementMode+
                    " paused="+clientFrameState.paused()+" blocked="+latestMovementBlocked+
                    " vanillaMotionCount="+vanillaMotionCount+" vanillaMotionAppliedVelocity="+vanillaMotionAppliedVelocity);
            return;
        }
        GhostPredictionHistory.RecordResult recordResult = predictionHistory.record(intent);
        if (recordResult != GhostPredictionHistory.RecordResult.RECORDED)
            return;
        predict(ghost, intent, true);
        GhostControlNetworking.sendToServer(intent);
        if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING)
            connectedPredictionFixture.sent(new ConnectedPredictionFixture.Sent(activeEpoch, intent.sequence(), intent.wishX(), intent.wishZ(), jumping, cadenceTick, System.nanoTime()), cadenceTick);
        cadenceSendLog("SEND seq="+intent.sequence()+" result="+recordResult+" mode="+
                characterMovementMode+" paused="+clientFrameState.paused()+" blocked="+latestMovementBlocked+
                " vanillaMotionCount="+vanillaMotionCount+" vanillaMotionAppliedVelocity="+vanillaMotionAppliedVelocity);
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
        airlockPredictions.clear();
        wallCapture.reset();
        resetConnectedWallMetadata();
        ContainedCharacterClientView.clear();
        Entity ghost = ghostEntityId < 0 ? null : currentBody(minecraft, ghostEntityId, harnessKind);
        if (ghost instanceof PlayerCharacterHarnessEntity character) character.clearPredictedAngularRecoveryState();
        if (restoreCamera && ghost != null && minecraft.getCameraEntity() == ghost
                && minecraft.player != null) minecraft.setCameraEntity(minecraft.player);
        pendingEpoch = 0;
        characterCadence.reset(); cadenceTick = 0; lastCadenceTick = 0;
        previousClientTickNano = 0; clientTickGapMillis = 0; maxClientTickGapMillis = 0;
        lastClientSendTraceTick = Long.MIN_VALUE;
        latestReconciliationDiagnostic = "status=none"; cadenceSamplesSuppressed = 0;
        vanillaMotionCount = 0; vanillaMotionAppliedVelocity = null;
        previousWishX = previousWishZ = 0; previousJump = false; previousButtons = 0; lastCadenceSnapshotStatus = "";
        acceptedBeginEpoch = 0;
        ghostEntityId = -1;
        activeEpoch = 0;
        sentSequence = 0;
        readySent = false;
        committed = false;
        committedBodyUuid = null;
        harnessKind = null;
        offeredEpoch = 0;
        offeredEntityId = -1;
        offeredKind = null;
        offerReadySent = false;
        predictionHistory = null;
        clientFrameState.reset();
        ownedSimulationClock.reset();
        if (ownerLevel != null && ghost != null)
            com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.reset(ownerLevel, ghost.getUUID());
        ownedSimulationClock.reset();
        angularPrediction.reset();
        supportedHeading.reset();
        observerMovementMotor = null;
        characterPredictionStopped = false;
        characterMovementRebaseRevision = 0;
        characterPredictionFailedSequence = 0;
        predictionMedicalSuspended = false;
        characterMovementMode = GhostControlPayloads.Snapshot.MovementMode.TRANSIT;
        transitInterpolation.clear();
        latestSurfaceFactor = 1f;
        latestVoluntaryFactor = 1f;
        latestMovementBlocked = false;
        externalFlightPrediction.clear();
        connectedFixtureReconciliationGeneration = 0;
        connectedFixtureTranslationVelocity = null;
        connectedFixtureTranslationConsistent = true;
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

    private record SupportPose(java.util.UUID frameId, org.joml.Quaterniond orientation) {}

    private static SupportPose supportedFrame(Minecraft minecraft, Entity body, boolean render, float partialTick) {
        if (minecraft.level == null || !(body instanceof Mob mob) || !mob.onGround()
                || harnessKind != MobHarnessKind.CHARACTER
                || characterMovementMode != GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                || clientFrameState.paused()) return null;
        var basis = clientFrameState.velocityBasis();
        var actual = LoadedFrameRayQuery.currentActorPoint(minecraft.level, body);
        if (basis == null || basis.frameId() == null || actual == null || !basis.frameId().equals(actual.frameId())
                || LoadedFrameRayQuery.pointAtWorldPosition(minecraft.level, basis.frameId(), body.position()) == null)
            return null;
        var framePose = render
                ? com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.presentation(
                        minecraft.level, body.getUUID(), basis.frameId(), partialTick)
                : com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.presentationEndpoint(
                        minecraft.level, body.getUUID(), basis.frameId(), false);
        var orientation = ClientSupportedHeading.orientation(framePose);
        return orientation == null ? null : new SupportPose(basis.frameId(), orientation);
    }

    /** Render-only relative offset; unavailable control/support states fail closed without rebinding the accumulator. */
    static float cameraRenderOffset(Minecraft minecraft, Entity cameraBody, float partialTick) {
        return presentationYawOffset(minecraft, cameraBody, partialTick);
    }

    /** Shared render yaw applied to camera and controlled model from the same frozen frame interval. */
    public static float presentationYawOffset(Entity cameraBody, float partialTick) {
        return presentationYawOffset(Minecraft.getInstance(), cameraBody, partialTick);
    }

    private static float presentationYawOffset(Minecraft minecraft, Entity cameraBody, float partialTick) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || !committed
                || activeEpoch == 0 || pendingEpoch != activeEpoch || owner != minecraft.player
                || ownerLevel != minecraft.level || harnessKind != MobHarnessKind.CHARACTER
                || characterMovementMode != GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                || cameraBody == null || cameraBody.isRemoved() || cameraBody.level() != minecraft.level
                || currentBody(minecraft, ghostEntityId, harnessKind) != cameraBody
                 || minecraft.getCameraEntity() != cameraBody || !Float.isFinite(partialTick)) return 0;
        var bridge = supportedHeading.transitionRenderOffset(activeEpoch, cameraBody.getUUID(), cameraBody.tickCount, partialTick);
        var support = supportedFrame(minecraft, cameraBody, true, partialTick);
        float supportOffset = support == null ? 0 : supportedHeading.renderOffset(activeEpoch, cameraBody.getUUID(), support.frameId,
                support.orientation, true);
        return ClientSupportedHeading.presentationOffset(bridge, supportOffset);
    }

    /** Called once from the post-particles world render hook; repeated frames in a tick are discarded. */
    public static void connectedWallRenderSample(net.minecraft.world.phys.Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        connectedPredictionFixture.heartbeat(System.nanoTime());
        reportConnectedPredictionFixture(mc);
        Entity body = committed && owner == mc.player && ownerLevel == mc.level
                ? currentBody(mc, ghostEntityId, harnessKind) : null;
        if (body == null || harnessKind != MobHarnessKind.CHARACTER) { wallCapture.stop(); resetConnectedWallMetadata(); return; }
        if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING) {
            connectedPredictionFixture.render(observeConnectedRender(mc,body,camera,partialTick,connectedPredictionFixture.startFrame()));
        }
        reportConnectedPredictionFixture(mc);
        if (connectedWallCaptureEpoch != activeEpoch || connectedWallCaptureBody != body.getId()) {
            wallCapture.reset(); resetConnectedWallMetadata();
            connectedWallCaptureEpoch = activeEpoch; connectedWallCaptureBody = body.getId();
        }
        try {
            if (wallCapture.mode() == ConnectedWallCapture.Mode.WAITING_JUMP) {
                if (wallCapture.waiting(activeEpoch, body.getId(), body.getUUID(), cadenceTick))
                    wallCapture.waitingRender(activeEpoch, body.getId(), body.getUUID(), cadenceTick,
                            connectedJumpRenderRow(mc, body, camera, partialTick));
                return;
            }
            if (wallCapture.mode() == ConnectedWallCapture.Mode.JUMP) {
                if (wallCapture.record(activeEpoch, body.getId(), body.getUUID(), cadenceTick, true))
                    MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase=RENDER epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} tick={} stage=AFTER_PARTICLES {}",
                            activeEpoch, body.getUUID(), body.getId(), wallCapture.triggerSequence(), sentSequence,
                            predictionHistory == null ? -1 : predictionHistory.lastAcknowledgedSequence(), cadenceTick,
                            connectedJumpRenderRow(mc, body, camera, partialTick));
                else if (!wallCapture.jumpActive() && wallCapture.finish())
                    MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase=END epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} tick={} reason=window-or-budget-expired",
                            activeEpoch, body.getUUID(), body.getId(), wallCapture.triggerSequence(), sentSequence,
                            predictionHistory == null ? -1 : predictionHistory.lastAcknowledgedSequence(), cadenceTick);
                return;
            }
        } catch (RuntimeException | LinkageError diagnosticFailure) {
            wallCapture.stop(); return;
        }
        if (!wallCapture.sample(activeEpoch, body.getId(), body.getUUID(), cadenceTick)) {
            if(wallCapture.remaining()==0)com.juicyslew.moonstation14.ms14.doors.AirlockContactCaptureGate.reset();
            return;
     }

         var move = lastFramedNativeMove;
          var cameraEntity = mc.getCameraEntity();
          var finalRotation = mc.gameRenderer.getMainCamera().rotation();
          var cameraForward = finalRotation.transform(new org.joml.Vector3f(0, 0, -1));
          var cameraUp = finalRotation.transform(new org.joml.Vector3f(0, 1, 0));
          var bodyLook = body.getLookAngle();
          var effectiveState = body instanceof PlayerCharacterHarnessEntity character ? character.angularRecoveryState().orElse(null) : null;
          var syncedState = body instanceof PlayerCharacterHarnessEntity character ? character.authoritativeAngularRecoveryState().orElse(null) : null;
          var sharedPresentation = body instanceof PlayerCharacterHarnessEntity character ? AngularRecoveryPresentation.orientation(character, partialTick) : null;
          String angularObservation = "current=" + angularState(effectiveState) + ";synchedData=" + angularState(syncedState)
                  + ";latestAppliedSnapshot(serverTick=" + latestDiagnosticAngularSnapshotTick + ",ack=" + latestDiagnosticAngularSnapshotAck + ")=" + angularState(latestDiagnosticSnapshotAngularState);
           String cameraObservation = "capture=ARMED"
                   + ";worldLogicalLook=" + vector(bodyLook.x, bodyLook.y, bodyLook.z) + ";rawLocalPlayerYawPitch=" + (mc.player == null ? "absent" : mc.player.getYRot() + "/" + mc.player.getXRot()) + ";sharedPresentationSample=" + quaternion(sharedPresentation)
                   + ";comfortRequested=" + (body instanceof PlayerCharacterHarnessEntity && angularRecoveryComfortRequested())
                  + ";finalCameraEntity=" + (cameraEntity == null ? "absent" : cameraEntity.getType() + "/" + cameraEntity.getId() + "/" + cameraEntity.getUUID())
                    + ";perspective=" + mc.options.getCameraType()
                   + ";finalForward=" + vector(cameraForward.x, cameraForward.y, cameraForward.z) + ";finalUp=" + vector(cameraUp.x, cameraUp.y, cameraUp.z);
           cameraObservation += ";renderObservation="+observeConnectedRender(mc,body,camera,partialTick,wallCapture.observationReferenceFrame()).observationText();
          MoonStation14.LOGGER.info(CONNECTED_WALL_FORMAT,
                 activeEpoch, body.getId(), cadenceTick, camera, partialTick, body.position(), body.getDeltaMovement(),
                body.getYRot(), sentSequence, predictionHistory == null ? "unknown" : predictionHistory.lastAcknowledgedSequence(),
                predictionHistory == null ? "unknown" : predictionHistory.lastRecordedSequence(),
                 predictionHistory == null ? "unknown" : predictionHistory.lastServerGameTick(),
                 "snapshotTick="+lastReconcileSnapshotTick+" ack="+lastReconcileAck+" replayFrames="+lastReconcileReplayFrames+" arrivalClientTick="+lastReconcileArrivalTick,
                 lastNativeObservationClientTick, lastNativeObservationIntentSeq, lastNativeObservationPhase, capturedAirlockContacts(move),
                  move == null ? "none" : com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic.describe(body, move), angularObservation, cameraObservation);
          if(wallCapture.remaining()==0)com.juicyslew.moonstation14.ms14.doors.AirlockContactCaptureGate.reset();
     }

    private static String connectedJumpRenderRow(Minecraft mc, Entity body, Vec3 camera, float partialTick) {
        var cameraEntity=mc.getCameraEntity(); var q=mc.gameRenderer.getMainCamera().rotation();
        var forward=q.transform(new org.joml.Vector3f(0,0,-1));
        UUID tracking=null;
        if(body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension extension) {
            var sub=extension.sable$getTrackingSubLevel(); if(sub!=null) tracking=sub.getUniqueId();
        }
        UUID saved=wallCapture.departureFrame(); UUID poseFrame=saved!=null?saved:tracking;
        String matrices="unavailable";
        try {
            var pair=currentFramePosePair(mc.level,poseFrame);
            SubLevel sub=poseFrame==null?null:SubLevelContainer.getContainer(mc.level).getSubLevel(poseFrame);
            dev.ryanhcode.sable.companion.math.Pose3dc render=sub==null?null:LoadedFrameRayQuery.renderPose(sub,partialTick);
            matrices="last="+(pair==null?"unavailable":java.util.Arrays.toString(pair.last().matrix()))
                    +";logical="+(pair==null?"unavailable":java.util.Arrays.toString(pair.logical().matrix()))
                    +";render="+(render==null?"unavailable":java.util.Arrays.toString(NativeFrameInterval.PoseValue.capture(render).matrix()));
        } catch(RuntimeException | LinkageError ignored) { matrices="unavailable"; }
        var look=body.getLookAngle();
        return "camera="+vector(camera.x,camera.y,camera.z)+";partial="+Float.toString(partialTick)
                +";body="+vector(body.getX(),body.getY(),body.getZ())+";old="+vector(body.xo,body.yo,body.zo)
                +";ground="+body.onGround()+";cameraEntity="+(cameraEntity==null?"unavailable":cameraEntity.getUUID()+"/"+cameraEntity.getId())
                +";cameraQ="+quaternion(q)+";forward="+vector(forward.x,forward.y,forward.z)
                +";perspective="+mc.options.getCameraType()+";trackingFrame="+tracking+";savedFrame="+saved
                +";poses="+matrices+";bodyLook="+vector(look.x,look.y,look.z)
                +";angular="+(body instanceof PlayerCharacterHarnessEntity character?angularState(character.angularRecoveryState().orElse(null)):"unavailable")
                +";sharedPresentation="+(body instanceof PlayerCharacterHarnessEntity character?quaternion(AngularRecoveryPresentation.orientation(character,partialTick)):"unavailable");
    }

    private static String angularState(com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State state) {
        if (state == null) return "absent";
        var q = state.orientation(); var w = state.worldAngularVelocity();
        return "present(qw=" + q.w() + ",qx=" + q.x() + ",qy=" + q.y() + ",qz=" + q.z()
                + ",wx=" + w.x() + ",wy=" + w.y() + ",wz=" + w.z() + ",airborneSeconds=" + state.airborneSeconds()
                + ",recoveryActive=" + state.recoveryActive() + ")";
    }
    private static String quaternion(org.joml.Quaternionfc q) { return q == null ? "absent" : "(" + q.w() + "," + q.x() + "," + q.y() + "," + q.z() + ")"; }
    private static String vector(double x, double y, double z) { return "(" + x + "," + y + "," + z + ")"; }
    private static String pointDescription(LoadedFrameRayQuery.ActorPoint point) {
        if(point==null||point.storagePoint()==null)return "unavailable";
        var p=point.storagePoint();return point.frameId()+":"+vector(p.x,p.y,p.z);
    }
    private static Vec3 frameVelocity(Entity entity, LoadedFrameRayQuery.ActorPoint point) {
        if(entity==null||point==null)return null;
        try { return com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity.pointVelocityAt(entity.level(),point); }
        catch(RuntimeException | LinkageError ignored) { return null; }
    }
    private static String vectorOrUnavailable(Vec3 value) {
        return value==null?"unavailable":vector(value.x,value.y,value.z);
    }
    private static String posePairDescription(NativeFrameInterval.PosePair pair) {
        return pair==null?"unavailable":"last="+java.util.Arrays.toString(pair.last().matrix())
                +";logical="+java.util.Arrays.toString(pair.logical().matrix());
    }

    /** Formats only observations attached to this exact move's collision result; never performs a world lookup. */
    private static String capturedAirlockContacts(com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult move){
        if(move==null||move.collisionInfo()==null||!(move.collisionInfo() instanceof com.juicyslew.moonstation14.ms14.doors.NativeAirlockContactAccess access))return "none";
        var contacts=access.moonstation14$airlockContacts();
        if(contacts.isEmpty())return "none";
        StringBuilder result=new StringBuilder();
        int count=0;
        for(var contact:contacts){
            if(count++>=4){result.append(";more");break;}
            if(result.length()>0)result.append(';');
            var state=contact.selectedState();
            boolean open=state.hasProperty(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN)
                    &&state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN);
            var view=contact.observedClientView();
            result.append("frame=").append(contact.frame().getUniqueId()).append(",root=").append(contact.rootPos())
                    .append(",rootId=").append(contact.rootControllerIdentity()==null?"unknown":contact.rootControllerIdentity())
                    .append(",selected=").append(contact.selectedPos()).append(",selectedOpen=").append(open)
                    .append(",clientView=").append(view==null?"unknown":view.phase()+"/"+view.revision()+"/"+view.passable());
        }
        return result.toString();
    }

    public static int armConnectedWallCapture() {
        Entity body = committed && harnessKind == MobHarnessKind.CHARACTER ? currentBody(Minecraft.getInstance(), ghostEntityId, harnessKind) : null;
        if (body == null) { wallCapture.reset(); resetConnectedWallMetadata(); return 0; }
        stopConnectedWallCapture(); resetConnectedWallMetadata(); connectedWallCaptureEpoch = activeEpoch; connectedWallCaptureBody = body.getId();
        UUID armObservationFrame=null;
        if(body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension ext){var tracked=ext.sable$getTrackingSubLevel();if(tracked!=null)armObservationFrame=tracked.getUniqueId();}
        if(!wallCapture.arm(activeEpoch,body.getId(),body.getUUID(),armObservationFrame))return 0;
        com.juicyslew.moonstation14.ms14.doors.AirlockContactCaptureGate.arm(activeEpoch,body.getId(),body.getUUID());
        return ConnectedWallCapture.MAX_TICKS;
    }
    public static boolean startConnectedPredictionFixture() {
        if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING) return false;
        Minecraft mc = Minecraft.getInstance(); Mob body = ownedCharacterForHud();
        if (body == null || !body.onGround() || mc.level == null) return false;
        var support = supportedFrame(mc, body, false, 0);
        if (support == null) return false;
        if (mc.getCameraEntity() != body) return false;
        connectedFixtureReported = false;
        connectedFixtureReconciliationGeneration = 0;
        connectedFixtureTranslationVelocity = null;
        connectedFixtureTranslationConsistent = true;
        return connectedPredictionFixture.start(activeEpoch, body.getUUID(), mc.level.dimension().location().toString(),
                cadenceTick, System.nanoTime());
    }
    public static void stopConnectedPredictionFixture() { connectedPredictionFixture.abort(ConnectedPredictionFixture.Failure.STOPPED); reportConnectedPredictionFixture(Minecraft.getInstance()); }
    private static void reportConnectedPredictionFixture(Minecraft mc) {
        var state = connectedPredictionFixture.state();
        if (connectedFixtureReported || (state != ConnectedPredictionFixture.State.COMPLETE && state != ConnectedPredictionFixture.State.FAILED)) return;
        connectedFixtureReported = true;
        double worst=0;
        for(var s:connectedPredictionFixture.snapshots()) if(s.beforeWorld()!=null&&s.finalWorld()!=null){var a=s.beforeWorld();var b=s.finalWorld();worst=Math.max(worst,Math.sqrt(Math.pow(b.x()-a.x(),2)+Math.pow(b.y()-a.y(),2)+Math.pow(b.z()-a.z(),2)));}
        long epoch=connectedPredictionFixture.epoch();
        String result=state==ConnectedPredictionFixture.State.COMPLETE?"PROTOCOL_PASS_VISUAL_UNVERIFIED":"FAIL_"+connectedPredictionFixture.failure();
        String path="report unavailable";
        try {
             var report=new java.util.LinkedHashMap<String,Object>(); report.put("schemaVersion",3); report.put("coordinateFieldSemantics",java.util.Map.of("world","server snapshot XYZ in world coordinates","frameStorage","server snapshot frameState XYZ in local storage coordinates; nullable","rendersCameraWorld","actual camera world position","rendersBodyWorld","interpolated body position at same render partial","framePoseMatrix","published selected pose at the same SDK interval")); report.put("outcome",result); report.put("failure",connectedPredictionFixture.failure());
             report.putAll(connectedPredictionFixture.diagnosticReport());
             report.put("startFrame",connectedPredictionFixture.startFrame()); report.put("sent",connectedPredictionFixture.sent()); report.put("snapshots",connectedPredictionFixture.snapshots()); report.put("renders",connectedPredictionFixture.renders());
             report.put("steps",connectedPredictionFixture.steps()); report.put("intervals",connectedPredictionFixture.intervals());
             report.put("jumpEdges",connectedPredictionFixture.jumpEdges()); report.put("releaseEdges",connectedPredictionFixture.releaseEdges()); report.put("plannedReplayCount",connectedPredictionFixture.replayed()); report.put("actualReplayCount",connectedPredictionFixture.actualReplayCount()); report.put("worstCorrection",worst); report.put("maxTicks",ConnectedPredictionFixture.MAX_TICKS); report.put("maxRecords",ConnectedPredictionFixture.MAX_RECORDS);
            var dir=mc.gameDirectory.toPath().resolve("diagnostics/connected-prediction"); java.nio.file.Files.createDirectories(dir);
            var file=dir.resolve("epoch-"+connectedPredictionFixture.epoch()+"-"+System.currentTimeMillis()+".json");
             java.nio.file.Files.writeString(file,ConnectedPredictionFixture.reportGson().toJson(report),java.nio.charset.StandardCharsets.UTF_8); path=file.toString();
        } catch(java.io.IOException | RuntimeException ioFailure) { MoonStation14.LOGGER.warn("[CONNECTED_PREDICTION] report write failed: {}",ioFailure.toString()); }
        String text="Connected prediction "+result+" epoch="+epoch+" jumps="+connectedPredictionFixture.jumpEdges()+" releases="+connectedPredictionFixture.releaseEdges()+" planned="+connectedPredictionFixture.replayed()+" actual="+connectedPredictionFixture.actualReplayCount()+" renders="+connectedPredictionFixture.renders().size()+" worstCorrection="+worst+" report="+path;
        MoonStation14.LOGGER.info("[CONNECTED_PREDICTION] {}",text);
        if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(text),false);
    }
    public static boolean armConnectedJumpCapture() {
        Entity body = committed && harnessKind == MobHarnessKind.CHARACTER
                ? currentBody(Minecraft.getInstance(), ghostEntityId, harnessKind) : null;
        if (body == null) return false;
        stopConnectedWallCapture(); resetConnectedWallMetadata();
        connectedWallCaptureEpoch=activeEpoch; connectedWallCaptureBody=body.getId();
        return wallCapture.armJump(activeEpoch, body.getId(), body.getUUID(), cadenceTick);
    }
    private static void resetConnectedWallMetadata() {
        com.juicyslew.moonstation14.ms14.doors.AirlockContactCaptureGate.reset();
        lastNativeObservationClientTick = lastNativeObservationIntentSeq = -1;
        lastNativeObservationPhase = "unknown";
        lastReconcileSnapshotTick = lastReconcileAck = lastReconcileArrivalTick = -1;
        latestDiagnosticSnapshotAngularState = null;
        latestDiagnosticAngularSnapshotTick = latestDiagnosticAngularSnapshotAck = -1;
        lastReconcileReplayFrames = -1;
        connectedWallCaptureEpoch = 0; connectedWallCaptureBody = -1;
    }
    public static int connectedWallCaptureRemaining() { return wallCapture.remaining(); }
    /** Native collision observer gate: exact currently captured owned body, no permission/movement effects. */
    public static void stopConnectedWallCapture() {
        if (wallCapture.mode()==ConnectedWallCapture.Mode.JUMP && wallCapture.finish())
            MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase=END epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} tick={} reason=command-stop",
                    wallCapture.epoch(),wallCapture.bodyUuid(),wallCapture.entityId(),wallCapture.triggerSequence(),sentSequence,
                    predictionHistory==null?-1:predictionHistory.lastAcknowledgedSequence(),cadenceTick);
        wallCapture.reset(); com.juicyslew.moonstation14.ms14.doors.AirlockContactCaptureGate.reset();
    }

    /** Exact committed CHARACTER Human owner only; observers and all remote entities remain SDK-owned. */
    public static boolean ownsSableRemoteFrameInterpolation(Entity target) {
        return harnessKind == MobHarnessKind.CHARACTER
                && target instanceof PlayerCharacterHarnessEntity
                && ownedHarnessForTracker(target) == target;
    }

    /** Observe completed vanilla velocity packets for this exact owned CHARACTER only. */
    public static void observeVanillaMotionPacket(Entity target) {
        try {
            if (harnessKind != MobHarnessKind.CHARACTER || target == null
                    || ownedHarnessForTracker(target) != target) return;
            vanillaMotionCount++;
            vanillaMotionAppliedVelocity = target.getDeltaMovement();
        } catch (RuntimeException ignored) {
            // Diagnostics only; never alter packet processing or movement.
        }
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

    /** Presentation-only hint; physical camera ownership remains with the presentation unit. */
    public static boolean angularRecoveryComfortRequested() {
        Mob body = ownedCharacterForPresentation();
        if (!(body instanceof PlayerCharacterHarnessEntity character)) return false;
        var state = character.angularRecoveryState().orElse(null);
        var policy = CharacterIdentitySystem.projectForActor(body)
                .flatMap(com.juicyslew.moonstation14.component.codec.json.CharacterData::angularRecoveryPolicy).orElse(null);
        if (state == null || policy == null || !policy.comfortReturnEnabled()) return false;
        var up = state.orientation().rotate(new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0,1,0));
        double tilt = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, up.y()))));
        return state.recoveryActive() && (tilt > policy.comfortTiltDegrees()
                || state.worldAngularVelocity().length() > policy.comfortAngularSpeed());
    }

    /** True while this exact local player and level are in a pending or committed Mind session. */
    public static boolean isLocalMindCarrier() {
        Minecraft minecraft = Minecraft.getInstance();
        return pendingEpoch != 0 && owner != null && owner == minecraft.player
                && minecraft.player != null && minecraft.level != null && ownerLevel == minecraft.level;
    }

    /** Applies an authority barrier only to the still-owned ghost after vanilla's teleport handling. */
    public static void onOwnedGhostTeleport(int entityId) {
        if (harnessKind != MobHarnessKind.GHOST) return;
        Minecraft minecraft = Minecraft.getInstance();
        Entity target = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (ownedHarnessForTracker(target) != null && predictionHistory != null) {
            predictionHistory.onAuthorityBarrier();
        }
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

        CharacterMovementRebasePolicy.Decision revisionDecision = harnessKind == MobHarnessKind.CHARACTER
                ? CharacterMovementRebasePolicy.decide(characterMovementRebaseRevision,
                        snapshot.movementRebaseRevision(), characterPredictionStopped)
                : CharacterMovementRebasePolicy.Decision.NORMAL;
        if (revisionDecision == CharacterMovementRebasePolicy.Decision.REJECT) {
            cadenceSnapshotStatus("revision-REJECT", null); return;
        }
        Vec3 diagnosticBefore = null;
        try { if (harnessKind == MobHarnessKind.CHARACTER) diagnosticBefore = entity.position(); }
        catch (RuntimeException ignored) { }
        Vec3 diagnosticServerPosition = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
        Vec3 fixtureBefore = connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING ? entity.position() : null;
        GhostPredictionHistory.SnapshotResult result = predictionHistory.reconcile(snapshot);
        // HISTORY_LOST means the production history cannot replay this authority point; keep
        // normal reconciliation behavior, but never let the diagnostic infer a clean replay.
        if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING
                && result.status() == GhostPredictionHistory.SnapshotStatus.HISTORY_LOST) {
            connectedPredictionFixture.abort(ConnectedPredictionFixture.Failure.REPLAY_FAILURE);
            reportConnectedPredictionFixture(minecraft);
        }
        if (harnessKind == MobHarnessKind.CHARACTER) latestReconciliationDiagnostic = "status="+result.status()+" tick="+
                snapshot.serverGameTick()+" ack="+snapshot.acknowledgedAppliedSequence()+" replay="+result.replayFrames().size()+
                " before="+diagnosticBefore+" server="+diagnosticServerPosition+" geometry=not-evaluated";
        cadenceSnapshotStatus(result.status()+" replay="+result.replayFrames().size()+" tick="+
                snapshot.serverGameTick()+" ack="+snapshot.acknowledgedAppliedSequence(), diagnosticBefore);
        if (result.status() == GhostPredictionHistory.SnapshotStatus.REJECTED) return;
        characterMovementMode = snapshot.movementMode();
        latestSurfaceFactor = snapshot.surfaceFactor();
        latestVoluntaryFactor = snapshot.voluntarySpeedFactor();
        latestMovementBlocked = snapshot.movementBlocked();
        externalFlightPrediction.reset(snapshot.externalFlightTicks());
        if (harnessKind == MobHarnessKind.CHARACTER)
            ContainedCharacterClientView.update(entity.getUUID(), activeEpoch, snapshot.containedView().orElse(null));

        // Pulled targets are positioned by vanilla's remote entity tracker, not this
        // controller. Snapshot velocity may seed later recovery, but never teleport them.
        if (!medical.recordHistory()) {
            latestSurfaceFactor = snapshot.surfaceFactor();
            latestVoluntaryFactor = snapshot.voluntarySpeedFactor();
            latestMovementBlocked = snapshot.movementBlocked();
            externalFlightPrediction.reset(snapshot.externalFlightTicks());
            if (harnessKind == MobHarnessKind.CHARACTER) {
                resetOwnedSimulationTimeline(entity);
                clientFrameState.reset();
            }
            entity.setDeltaMovement(entity instanceof GhostMobHarnessEntity ? Vec3.ZERO : new Vec3(
                    snapshot.velocityX() / 20d, snapshot.velocityY() / 20d, snapshot.velocityZ() / 20d));
            return;
        }

        float visualYaw = entity.getYRot();
        float visualPitch = entity.getXRot();
        boolean transitCharacter = harnessKind == MobHarnessKind.CHARACTER
                && snapshot.movementMode() == GhostControlPayloads.Snapshot.MovementMode.TRANSIT;
        boolean groundedCharacter = harnessKind == MobHarnessKind.CHARACTER
                && snapshot.movementMode() == GhostControlPayloads.Snapshot.MovementMode.GROUNDED;
        if (groundedCharacter) angularPrediction.reset();
        Vec3 authoritativePosition = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
        Vec3 clientBeforeAuthority = entity.position();
        LoadedFrameRayQuery.ActorPoint snapshotBasis = null;
        NativeFrameInterval.PosePair snapshotPoses = null;
        if (groundedCharacter) {
            if (snapshot.completedSableSimulationStep().isEmpty()) {
                connectedPredictionFixture.authorityFailure("SNAPSHOT_COMPLETED_SDK_STEP",
                        snapshot.frameState().map(f->f.frameId()).orElse(null),null,null,"NOT_AVAILABLE","unknown");
                reportConnectedPredictionFixture(minecraft);
                clientFrameState.pause();
                ownedSimulationClock.reset();
                predictionHistory.onAuthorityBarrier();
                cadenceSnapshotStatus("sable-clock-unavailable", null);
                return;
            }
            ownedSimulationClock.restore(activeEpoch, snapshot.completedSableSimulationStep().getAsLong(),
                    snapshot.acknowledgedAppliedSequence());
            lastReconcileSnapshotTick = snapshot.serverGameTick(); lastReconcileAck = snapshot.acknowledgedAppliedSequence();
            latestDiagnosticSnapshotAngularState = snapshot.angularRecoveryState().orElse(null);
            latestDiagnosticAngularSnapshotTick = snapshot.serverGameTick();
            latestDiagnosticAngularSnapshotAck = snapshot.acknowledgedAppliedSequence();
            lastReconcileReplayFrames = result.replayFrames().size(); lastReconcileArrivalTick = entity.level().getGameTime();
            var frame = snapshot.frameState().orElse(null);
            if (entity instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement) {
                SubLevel authorityFrame = frame == null ? null : SubLevelContainer.getContainer(minecraft.level).getSubLevel(frame.frameId());
                if (frame != null && (authorityFrame == null || authorityFrame.isRemoved()
                        || authorityFrame.getLevel() != minecraft.level
                        || SubLevelContainer.getContainer(minecraft.level).getSubLevel(frame.frameId()) != authorityFrame)) {
                    connectedPredictionFixture.authorityFailure("SNAPSHOT_FRAME_RESIDENCY",frame.frameId(),
                            snapshot.completedSableSimulationStep().getAsLong(),snapshot.completedSableSimulationStep().getAsLong(),
                            "NOT_SAMPLED","not-resident-or-removed"); reportConnectedPredictionFixture(minecraft);
                    clientFrameState.pause(); ownedSimulationClock.reset(); predictionHistory.onAuthorityBarrier(); return;
                }
                if (frame != null) {
                    long step = snapshot.completedSableSimulationStep().getAsLong();
                    var timeline = com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore
                            .timeline(minecraft.level, frame.frameId());
                    if (timeline == null || timeline.sample(frame.frameId(), step).kind()
                            == com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline.Kind.MISSING) {
                        connectedPredictionFixture.authorityFailure("SNAPSHOT_FRAME_POSE_HISTORY",frame.frameId(),step,step,
                                timeline==null?"TIMELINE_UNAVAILABLE":"MISSING","frame-resident");
                        reportConnectedPredictionFixture(minecraft);
                        clientFrameState.pause(); ownedSimulationClock.reset(); predictionHistory.onAuthorityBarrier(); return;
                    }
                }
                movement.sable$setTrackingSubLevel(authorityFrame);
            } else if (frame != null) {
                connectedPredictionFixture.authorityFailure("CONTROLLED_BODY_FRAME_EXTENSION",frame.frameId(),
                        snapshot.completedSableSimulationStep().getAsLong(),snapshot.completedSableSimulationStep().getAsLong(),
                        "NOT_SAMPLED","frame-resident-but-body-untracked");
                reportConnectedPredictionFixture(minecraft);
                clientFrameState.pause(); ownedSimulationClock.reset(); predictionHistory.onAuthorityBarrier(); return;
            }
            if (frame != null) {
                long authorityStep = snapshot.completedSableSimulationStep().getAsLong();
                try (var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope
                        .open(minecraft.level, entity, authorityStep, authorityStep)) {
                    if (!temporal.ready()) throw new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException(temporal.failure());
                    restoreSnapshotBody(entity, snapshot, authoritativePosition);
                    var selected = temporal.requireInterval(minecraft.level,
                            SubLevelContainer.getContainer(minecraft.level).getSubLevel(frame.frameId()));
                    snapshotPoses = NativeFrameInterval.PosePair.capture(selected.from(), selected.to());
                    snapshotBasis = LoadedFrameRayQuery.pointAtWorldPosition(minecraft.level, frame.frameId(), authoritativePosition);
                } catch (com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException missing) {
                    connectedPredictionFixture.authorityFailure("SNAPSHOT_FRAME_TEMPORAL_SCOPE",frame.frameId(),authorityStep,authorityStep,
                            missing.reason().name(),"frame-resident"); reportConnectedPredictionFixture(minecraft);
                    clientFrameState.pause(); ownedSimulationClock.reset(); predictionHistory.onAuthorityBarrier(); return;
                }
                if (snapshotBasis == null || snapshotPoses == null) {
                    connectedPredictionFixture.authorityFailure(snapshotBasis==null?"SNAPSHOT_FRAME_ACTOR_BASIS":"SNAPSHOT_FRAME_POSE_PAIR",
                            frame.frameId(),authorityStep,authorityStep,snapshotPoses==null?"POSE_PAIR_UNAVAILABLE":"POSE_PAIR_SELECTED",
                            "frame-resident");
                    reportConnectedPredictionFixture(minecraft);
                    cadenceSnapshotStatus("frame-basis-unavailable", null);
                    clientFrameState.pause();
                    onSnapshotAuthorityBarrier();
                    return; // unavailable frame pauses; never reinterpret worldXYZ as its parent
                }
                clientFrameState.authorityProjected(snapshotBasis, activeEpoch, snapshotPoses);
            } else {
                long authorityStep = snapshot.completedSableSimulationStep().getAsLong();
                try (var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope
                        .open(minecraft.level, entity, authorityStep, authorityStep)) {
                    if (!temporal.ready()) throw new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException(temporal.failure());
                    restoreSnapshotBody(entity, snapshot, authoritativePosition);
                    snapshotBasis = LoadedFrameRayQuery.pointAtWorldPosition(minecraft.level, null, authoritativePosition);
                } catch (com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException missing) {
                    connectedPredictionFixture.authorityFailure("SNAPSHOT_WORLD_TEMPORAL_SCOPE",null,authorityStep,authorityStep,
                            missing.reason().name(),"world-frame"); reportConnectedPredictionFixture(minecraft);
                    clientFrameState.pause(); ownedSimulationClock.reset(); predictionHistory.onAuthorityBarrier(); return;
                }
                if (snapshotBasis == null) {
                    connectedPredictionFixture.authorityFailure("SNAPSHOT_WORLD_ACTOR_BASIS",null,authorityStep,authorityStep,
                            "WORLD_BASIS_UNAVAILABLE","world-frame");
                    reportConnectedPredictionFixture(minecraft);
                    cadenceSnapshotStatus("world-basis-unavailable", null);
                    clientFrameState.pause();
                    onSnapshotAuthorityBarrier();
                    return;
                }
                clientFrameState.ordinaryWorldAuthority(snapshotBasis);
            }
        } else if (harnessKind == MobHarnessKind.CHARACTER) {
            clientFrameState.reset();
            entity.setPos(authoritativePosition);
            if (entity instanceof Mob authorityBody) authorityBody.setOnGround(snapshot.onGround());
            entity.setDeltaMovement(entity instanceof GhostMobHarnessEntity || isCharacterContainedMode() ? Vec3.ZERO : new Vec3(
                    snapshot.velocityX() / 20d, snapshot.velocityY() / 20d, snapshot.velocityZ() / 20d));
            if (entity instanceof PlayerCharacterHarnessEntity authorityCharacter)
                authorityCharacter.setAirMotionReference(snapshot.airMotionReference().orElse(null));
        }
        CharacterMovementRebasePolicy.Decision rebaseDecision = harnessKind == MobHarnessKind.CHARACTER
                ? CharacterMovementRebasePolicy.decide(characterMovementRebaseRevision,
                        snapshot.movementRebaseRevision(), characterPredictionStopped,
                        characterPredictionFailedSequence, snapshot.acknowledgedAppliedSequence(), true,
                        Double.isFinite(authoritativePosition.x) && Double.isFinite(authoritativePosition.y)
                                && Double.isFinite(authoritativePosition.z)
                                && (!groundedCharacter || snapshotBasis != null))
                : CharacterMovementRebasePolicy.Decision.NORMAL;
        if (rebaseDecision == CharacterMovementRebasePolicy.Decision.REJECT) return;
        boolean barrierCorrection = rebaseDecision == CharacterMovementRebasePolicy.Decision.REBASE
                || rebaseDecision == CharacterMovementRebasePolicy.Decision.RESUME_BARRIER;
        if (barrierCorrection) {
            connectedPredictionFixture.abort(ConnectedPredictionFixture.Failure.AUTHORITY_BARRIER);
            reportConnectedPredictionFixture(minecraft);
            characterMovementRebaseRevision = snapshot.movementRebaseRevision();
            characterPredictionStopped = false;
            characterPredictionFailedSequence = 0;
            predictionHistory.onAuthorityBarrier();
            clientFrameState.reset();
            if (groundedCharacter && snapshotBasis != null) {
                if (snapshot.frameState().isPresent()) clientFrameState.authorityProjected(snapshotBasis, activeEpoch, snapshotPoses);
                else clientFrameState.ordinaryWorldAuthority(snapshotBasis);
            }
        }
        int interpolationSteps = harnessKind == MobHarnessKind.CHARACTER
                ? transitInterpolation.accept(activeEpoch, entity.getUUID(), snapshot.movementMode(),
                    snapshot.containedView().map(com.juicyslew.moonstation14.ms14.containment.ContainedBodyView::ownerIdentity)
                            .orElse(null))
                : 0;
        if (transitCharacter && interpolationSteps > 0) {
            // Keep the server sample authoritative, but let Entity's normal client tick interpolate it
            // rather than teleporting the camera target on every transit snapshot.
            entity.lerpTo(authoritativePosition.x, authoritativePosition.y, authoritativePosition.z, snapshot.yaw(), snapshot.pitch(), interpolationSteps);
        } else {
            entity.setPos(authoritativePosition);
            entity.lerpTo(authoritativePosition.x, authoritativePosition.y, authoritativePosition.z, snapshot.yaw(), snapshot.pitch(), 0);
        }
        if (entity instanceof Mob) entity.setOnGround(snapshot.onGround());
        if (groundedCharacter) supportedHeading.authorityRestored();
        if (groundedCharacter) {
            entity.xo = authoritativePosition.x; entity.yo = authoritativePosition.y; entity.zo = authoritativePosition.z;
        }
        entity.setDeltaMovement(entity instanceof GhostMobHarnessEntity || isCharacterContainedMode() ? Vec3.ZERO : new Vec3(
                snapshot.velocityX() / 20d, snapshot.velocityY() / 20d, snapshot.velocityZ() / 20d));
        if (entity instanceof GhostMobHarnessEntity ghost) setOwnedBodyLook(ghost, snapshot.yaw(), snapshot.pitch());
        else if (entity instanceof Mob mob) setOwnedBodyLook(mob, snapshot.yaw(), snapshot.pitch());
        if (entity instanceof PlayerCharacterHarnessEntity character) {
            character.setAirMotionReference(snapshot.airMotionReference().orElse(null));
            if (groundedCharacter) character.setPredictedAngularRecoveryState(snapshot.angularRecoveryState());
            else { character.setPredictedAngularRecoveryState(java.util.Optional.empty()); angularPrediction.reset(); }
        }
        java.util.ArrayList<ConnectedPredictionFixture.Replay> actualFixtureReplay =
                connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING ? new java.util.ArrayList<>() : null;
        if (CharacterMovementSnapshotPolicy.requiresBarrier(characterMovementMode)) {
            onSnapshotAuthorityBarrier();
            if (groundedCharacter && snapshot.completedSableSimulationStep().isPresent())
                ownedSimulationClock.restore(activeEpoch, snapshot.completedSableSimulationStep().getAsLong(),
                        snapshot.acknowledgedAppliedSequence());
        }
        if (groundedCharacter && snapshot.completedSableSimulationStep().isPresent()) {
            long restoredStep = snapshot.completedSableSimulationStep().getAsLong();
            setOwnedBodyRenderFrom(entity, entity.position());
            var restoreFrame = snapshot.frameState().map(f -> f.frameId()).orElse(null);
            var airFrame = entity instanceof PlayerCharacterHarnessEntity character
                    ? character.airMotionReference().map(com.juicyslew.moonstation14.ms14.movement.AirMotionReference::frameId).orElse(null) : null;
            try (var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope
                    .open(minecraft.level, entity, restoredStep, restoredStep)) {
                if (temporal.ready()) {
                    var pairs = temporal.presentationSnapshot(restoreFrame, airFrame);
                    var angular = angularPose(entity);
                    com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.publish(
                            minecraft.level, activeEpoch, entity.getUUID(), restoredStep, restoredStep, pairs,
                            com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair.capture(angular,angular));
                } else com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.reset(minecraft.level, entity.getUUID());
            }
            if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING) {
                connectedFixtureReconciliationGeneration++;
                connectedPredictionFixture.step(new ConnectedPredictionFixture.Step(restoredStep,
                        connectedFixtureReconciliationGeneration, true));
            }
        }
        if (!CharacterMovementSnapshotPolicy.requiresBarrier(characterMovementMode) && !barrierCorrection
                && CharacterMovementSnapshotPolicy.allowsReplay(characterMovementMode, result.status(), characterPredictionStopped)
                && medical.replaySnapshot()) {
            for (GhostControlPayloads.Intent intent : result.replayFrames()) {
                if (predictionHistory == null || predictionHistory.pendingCount() == 0) break;
                predict(entity, intent, false);
                if (actualFixtureReplay != null) actualFixtureReplay.add(new ConnectedPredictionFixture.Replay(
                        intent.sequence(), !characterPredictionStopped));
            }
        }
        if (groundedCharacter && snapshot.frameState().isPresent())
            logFramedAuthorityDisagreement(entity, snapshot, clientBeforeAuthority, authoritativePosition,
                    snapshotBasis, barrierCorrection, result.replayFrames().size());
        if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING) {
            long[] planned = result.replayFrames().stream().mapToLong(GhostControlPayloads.Intent::sequence).toArray();
            boolean replaySucceeded = actualFixtureReplay != null && actualFixtureReplay.size() == planned.length
                    && actualFixtureReplay.stream().allMatch(ConnectedPredictionFixture.Replay::executed);
            var frameState = snapshot.frameState().orElse(null);
            var poseLast = snapshotPoses == null ? java.util.List.<Double>of() : java.util.Arrays.stream(snapshotPoses.last().matrix()).boxed().toList();
            var poseLogical = snapshotPoses == null ? java.util.List.<Double>of() : java.util.Arrays.stream(snapshotPoses.logical().matrix()).boxed().toList();
            connectedPredictionFixture.snapshot(new ConnectedPredictionFixture.Snapshot(activeEpoch,
                    snapshot.serverGameTick(), snapshot.acknowledgedAppliedSequence(), cadenceTick, System.nanoTime(), planned, true,
                    replaySucceeded, snapshot.onGround(), frameState == null ? null : frameState.frameId(),
                    new ConnectedPredictionFixture.Vector(snapshot.x(), snapshot.y(), snapshot.z()),
                    frameState == null ? "WORLD" : "FRAME", frameState == null ? null : new ConnectedPredictionFixture.Vector(frameState.x(), frameState.y(), frameState.z()), snapshot.movementRebaseRevision(), result.status().name(),
                    new ConnectedPredictionFixture.Vector(authoritativePosition.x, authoritativePosition.y, authoritativePosition.z),
                    new ConnectedPredictionFixture.Vector(fixtureBefore.x, fixtureBefore.y, fixtureBefore.z),
                    new ConnectedPredictionFixture.Vector(entity.getX(), entity.getY(), entity.getZ()), actualFixtureReplay, poseLast, poseLogical));
            connectedPredictionFixture.complete();
        }
        reportConnectedPredictionFixture(minecraft);
        if (groundedCharacter) {
            try {
                Vec3 finalPosition = entity.position();
                latestReconciliationDiagnostic = "status="+result.status()+" tick="+snapshot.serverGameTick()+
                        " ack="+snapshot.acknowledgedAppliedSequence()+" replay="+result.replayFrames().size()+
                        " before="+diagnosticBefore+" server="+diagnosticServerPosition+" final="+finalPosition+
                        " correction="+finalPosition.subtract(diagnosticBefore);
            } catch (RuntimeException ignored) { latestReconciliationDiagnostic = "status="+result.status()+
                    " tick="+snapshot.serverGameTick()+" ack="+snapshot.acknowledgedAppliedSequence()+
                    " replay="+result.replayFrames().size()+" geometry=unavailable"; }
        }
        if (wallCapture.jumpActive()) {
            try {
                if (wallCapture.record(activeEpoch, entity.getId(), entity.getUUID(), cadenceTick, false))
                    MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase=SNAPSHOT epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} serverTick={} arrivalTick={} status={} replayFrames={} before={} authoritative={} final={} beforeVelocity={} finalVelocity={} authoritativeVelocityBlocksPerSecond=({},{},{}) grounded={} frame={} framePoint={} rebase={} reconcile={}",
                            activeEpoch,entity.getUUID(),entity.getId(),wallCapture.triggerSequence(),sentSequence,
                            snapshot.acknowledgedAppliedSequence(),snapshot.serverGameTick(),entity.level().getGameTime(),
                            result.status(),result.replayFrames().size(),vector(clientBeforeAuthority.x,clientBeforeAuthority.y,clientBeforeAuthority.z),
                            vector(authoritativePosition.x,authoritativePosition.y,authoritativePosition.z),
                            vector(entity.getX(),entity.getY(),entity.getZ()),
                            vector(entity.getDeltaMovement().x,entity.getDeltaMovement().y,entity.getDeltaMovement().z),
                            vector(snapshot.velocityX(),snapshot.velocityY(),snapshot.velocityZ()),snapshot.onGround(),
                            snapshot.frameState().map(f->f.frameId().toString()).orElse("WORLD"),
                            snapshot.frameState().map(f->vector(f.x(),f.y(),f.z())).orElse("world"),
                            snapshot.movementRebaseRevision(),latestReconciliationDiagnostic);
            } catch(RuntimeException | LinkageError diagnosticFailure) { wallCapture.stop(); }
        }
        // Preserve the local view; movement replay still uses each intent's quantized yaw.
        if (entity instanceof GhostMobHarnessEntity ghost) setOwnedBodyLook(ghost, visualYaw, visualPitch);
        else if (entity instanceof Mob mob) setOwnedBodyLook(mob, visualYaw, visualPitch);
    }

    private static void restoreSnapshotBody(Entity entity, GhostControlPayloads.Snapshot snapshot, Vec3 position) {
        entity.setPos(position);
        if (entity instanceof Mob mob) mob.setOnGround(snapshot.onGround());
        entity.setDeltaMovement(entity instanceof GhostMobHarnessEntity || isCharacterContainedMode() ? Vec3.ZERO
                : new Vec3(snapshot.velocityX() / 20d, snapshot.velocityY() / 20d, snapshot.velocityZ() / 20d));
        if (entity instanceof PlayerCharacterHarnessEntity character)
            character.setAirMotionReference(snapshot.airMotionReference().orElse(null));
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

    private static void cadenceEarly(String reason, Minecraft minecraft, LocalPlayer player) {
        if (harnessKind != MobHarnessKind.CHARACTER || pendingEpoch <= 0 || cadenceTick % 20 != 0) return;
        long epoch = activeEpoch > 0 ? activeEpoch : pendingEpoch;
        Entity camera = minecraft.getCameraEntity();
        cadenceLog(epoch, false, "EARLY reason="+reason+" active="+activeEpoch+" pending="+pendingEpoch+
                " entityId="+ghostEntityId+" sent="+sentSequence+" ack="+
                (predictionHistory==null?-1:predictionHistory.lastAcknowledgedSequence())+" camera="+
                (camera==null?"none":camera.getId())+" screen="+
                (minecraft.screen==null?"none":minecraft.screen.getClass().getName())+" focused="+
                minecraft.isWindowActive()+" player="+(player==null?"none":player.getId()));
    }

    private static void cadenceLog(long epoch, boolean important, String message) {
        if (epoch <= 0 || harnessKind != MobHarnessKind.CHARACTER) return;
        try {
            if (characterCadence.allow(epoch, System.nanoTime(), important))
                MoonStation14.LOGGER.info("[CHARACTER_CADENCE] CLIENT epoch={} entityId={} sent={} ack={} {}",
                        epoch, ghostEntityId, sentSequence,
                        predictionHistory == null ? -1 : predictionHistory.lastAcknowledgedSequence(), message);
            else cadenceSamplesSuppressed++;
        } catch (RuntimeException ignored) { }
    }

    private static void cadenceSnapshotStatus(String status, Vec3 before) {
        if (status.equals(lastCadenceSnapshotStatus)) return;
        lastCadenceSnapshotStatus = status;
        if (before == null) latestReconciliationDiagnostic = "status="+status+" geometry=unavailable";
    }

    private static void cadenceSendLog(String message) {
        if (cadenceTick % 20 != 0 || lastClientSendTraceTick == cadenceTick) return;
        lastClientSendTraceTick = cadenceTick;
        cadenceLog(activeEpoch, false, message+" maxTickGapMillis="+maxClientTickGapMillis+" reconciliation={"+
                latestReconciliationDiagnostic+"} samplesSuppressed="+cadenceSamplesSuppressed);
        maxClientTickGapMillis = 0;
    }

    private static void onSnapshotAuthorityBarrier() {
        Entity body = ghostEntityId < 0 ? null : currentBody(Minecraft.getInstance(), ghostEntityId, harnessKind);
        resetOwnedSimulationTimeline(body);
        if (predictionHistory == null) return;
        if (characterPredictionStopped) predictionHistory.onPredictionFailureBarrier();
        else predictionHistory.onAuthorityBarrier();
    }

    /** Clears only this committed character's published interval and local SDK clock; shared frame history stays intact. */
    private static void resetOwnedSimulationTimeline(Entity body) {
        if (body == null || !OwnedSimulationTimelineIdentity.matches(committed, activeEpoch, pendingEpoch,
                harnessKind, ghostEntityId, committedBodyUuid, ownerLevel,
                body.getId(), body.getUUID(), body.level())) return;
        ownedSimulationClock.reset();
        com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.reset(body.level(), body.getUUID());
    }

    private static NativeFrameInterval.PosePair currentFramePosePair(Level level, java.util.UUID frameId) {
        if (level == null || frameId == null) return null;
        SubLevel sub = SubLevelContainer.getContainer(level).getSubLevel(frameId);
        if (sub == null || sub.isRemoved() || sub.getLevel() != level
                || SubLevelContainer.getContainer(level).getSubLevel(frameId) != sub) return null;
        var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.activeExact(level, sub);
        if (temporal != null && temporal.ready()) {
            var interval = temporal.requireInterval(level, sub);
            return NativeFrameInterval.PosePair.capture(interval.from(), interval.to());
        }
        return NativeFrameInterval.PosePair.capture(sub.lastPose(), sub.logicalPose());
    }

    private record AngularSupport(java.util.UUID frameId, NativeFrameInterval.PosePair poses,
                                  org.joml.Quaterniond last, org.joml.Quaterniond logical) { }

    /** Measures only an exact currently grounded, native-tracked frame support witness. */
    private static AngularSupport angularSupport(Mob body) {
        if (!(body instanceof PlayerCharacterHarnessEntity) || !body.onGround() || body.level() == null
                || !body.level().isClientSide || harnessKind != MobHarnessKind.CHARACTER
                || characterMovementMode != GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                || clientFrameState.paused()) return null;
        var basis = clientFrameState.velocityBasis();
        var witness = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
        if (basis == null || basis.frameId() == null || witness == null || !basis.frameId().equals(witness.frameId())
                || LoadedFrameRayQuery.pointAtWorldPosition(body.level(), basis.frameId(), body.position()) == null) return null;
        if (!(body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement)) return null;
        var collision = movement.sable$getCollisionInfo();
        if (collision == null || !collision.verticalCollision || !collision.verticalCollisionBelow
                || collision.trackingSubLevel == null || !basis.frameId().equals(collision.trackingSubLevel.getUniqueId())) return null;
        SubLevel sub = SubLevelContainer.getContainer(body.level()).getSubLevel(basis.frameId());
        if (sub == null || sub.isRemoved() || sub.getLevel() != body.level()
                || SubLevelContainer.getContainer(body.level()).getSubLevel(basis.frameId()) != sub) return null;
        var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.activeExact(body.level(), sub);
        dev.ryanhcode.sable.companion.math.Pose3dc from, to;
        if (temporal != null && temporal.ready()) {
            var interval = temporal.requireInterval(body.level(), sub);
            from = interval.from(); to = interval.to();
        } else {
            var committed = com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore
                    .presentationEndpoint(body.level(), body.getUUID(), basis.frameId(), false);
            if (committed == null) return null;
            from = committed; to = committed;
        }
        var last = from.orientation(); var logical = to.orientation();
        return new AngularSupport(basis.frameId(), NativeFrameInterval.PosePair.capture(from, to),
                new org.joml.Quaterniond(last.x(), last.y(), last.z(), last.w()).normalize(),
                new org.joml.Quaterniond(logical.x(), logical.y(), logical.z(), logical.w()).normalize());
    }

    /** Admit SDK carry once per selected interval in replay and live reconstruction alike. */
    private static boolean allowIntervalNativeCarrier(Mob body,
            com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope temporal) {
        if (harnessKind != MobHarnessKind.CHARACTER || clientFrameState.paused() || temporal == null || !temporal.ready()) return false;
        var basis = clientFrameState.velocityBasis();
        if (basis == null) return false;
        Level level = body.level();
        if (LoadedFrameRayQuery.pointAtWorldPosition(level, basis.frameId(), body.position()) == null) {
            clientFrameState.pause();
            if (predictionHistory != null) predictionHistory.onAuthorityBarrier();
            return false;
        }
        var actualBefore = LoadedFrameRayQuery.currentActorPoint(level, body);
        if (actualBefore == null) {
            clientFrameState.pause();
            if (predictionHistory != null) predictionHistory.onAuthorityBarrier();
            return false;
        }
        NativeFrameInterval.PosePair selectedPair = null;
        if (basis.frameId() != null) {
            SubLevel frame = SubLevelContainer.getContainer(level).getSubLevel(basis.frameId());
            if (frame == null) return false;
            var selected = temporal.requireInterval(level, frame);
            selectedPair = NativeFrameInterval.PosePair.capture(selected.from(), selected.to());
        }
        return clientFrameState.allowNativeCarrier(activeEpoch, actualBefore, selectedPair);
    }

    private static void markCurrentFrameInterval(Level level, long epoch,
                                                  LoadedFrameRayQuery.ActorPoint point) {
        if (point == null || point.frameId() == null) return;
        NativeFrameInterval.PosePair poses = currentFramePosePair(level, point.frameId());
        if (poses != null) clientFrameState.interval().consumeIfChanged(epoch, point.frameId(), poses);
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
            resetOwnedSimulationTimeline(entity);
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
        if (harnessKind != MobHarnessKind.CHARACTER || body.level() == null) return;
        if (characterPolicy(body) == null) return;
        var interval = ownedSimulationClock.prepare(activeEpoch, intent.sequence());
        if (interval == null) return;
        Vec3 intervalBefore = body.position();
        Vec3 fixtureBefore = connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING
                ? intervalBefore : null;
        Vec3 fixtureBeforeVelocity = fixtureBefore == null ? null : body.getDeltaMovement();
        org.joml.Quaternionf angularBefore;
        org.joml.Quaternionf angularAfter;
        try (var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope
                .open(body.level(), body, interval.from(), interval.to())) {
            if (!temporal.ready()) throw new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException(temporal.failure());
            angularBefore=angularPose(body);
            predictCharacterInTemporalScope(body, intent, animate, interval);
            angularAfter=angularPose(body);
            if (characterPredictionStopped || clientFrameState.paused()) {
                ownedSimulationClock.reset();
                return;
            }
            if (!ownedSimulationClock.commit(interval, activeEpoch, intent.sequence())) {
                ownedSimulationClock.reset();
                clientFrameState.pause();
                onSnapshotAuthorityBarrier();
                return;
            }
            // Camera and body renderer interpolate the same accepted body interval as the published SDK poses.
            setOwnedBodyRenderFrom(body, intervalBefore);
            if (connectedPredictionFixture.state() == ConnectedPredictionFixture.State.RUNNING
                    && connectedFixtureReconciliationGeneration > 0) {
                connectedPredictionFixture.step(new ConnectedPredictionFixture.Step(interval.to(),
                        connectedFixtureReconciliationGeneration, false));
                var observation = connectedIntervalObservation(body, temporal, interval, intent.sequence(),
                        !animate, fixtureBefore, fixtureBeforeVelocity);
                if (observation == null) {
                    connectedPredictionFixture.authorityFailure("CONNECTED_INTERVAL_OBSERVATION",temporal.frameId(),
                            interval.from(),interval.to(),temporal.ready()?"OBSERVATION_DATA_UNAVAILABLE":temporal.failure().name(),
                            "scope-frame-selection-unavailable");
                    connectedPredictionFixture.abort(ConnectedPredictionFixture.Failure.UNAVAILABLE_AUTHORITY);
                    reportConnectedPredictionFixture(Minecraft.getInstance());
                } else connectedPredictionFixture.interval(observation);
            }
            var airFrame = body instanceof PlayerCharacterHarnessEntity character
                    ? character.airMotionReference().map(com.juicyslew.moonstation14.ms14.movement.AirMotionReference::frameId).orElse(null) : null;
            com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.publish(
                    body.level(), activeEpoch, body.getUUID(), interval.from(), interval.to(),
                    temporal.presentationSnapshot(airFrame),
                    com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair.capture(
                            angularBefore,angularAfter));
        } catch (com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException unavailable) {
            UUID frameId=body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension ext
                    && ext.sable$getTrackingSubLevel()!=null?ext.sable$getTrackingSubLevel().getUniqueId():null;
            String residency=frameId==null?"WORLD_OR_UNTRACKED":(SubLevelContainer.getContainer(body.level()).getSubLevel(frameId)==null
                    ?"frame-not-resident":"frame-resident");
            connectedPredictionFixture.authorityFailure("PREDICTION_SCOPE_INTERVAL",frameId,interval.from(),interval.to(),
                    unavailable.reason().name(),residency);
            ownedSimulationClock.reset();
            clientFrameState.pause();
            onSnapshotAuthorityBarrier();
            connectedPredictionFixture.abort(ConnectedPredictionFixture.Failure.UNAVAILABLE_AUTHORITY);
            reportConnectedPredictionFixture(Minecraft.getInstance());
            cadenceSnapshotStatus("temporal-unavailable-" + unavailable.reason(), null);
        }
    }

    private static void setOwnedBodyRenderFrom(Entity body, Vec3 from) {
        body.xo = from.x; body.yo = from.y; body.zo = from.z;
        body.xOld = from.x; body.yOld = from.y; body.zOld = from.z;
    }

    /** Inertial pose at a movement boundary; absent normal-posture state is explicit identity. */
    private static org.joml.Quaternionf angularPose(Entity body) {
        var state=body instanceof PlayerCharacterHarnessEntity character
                ?character.angularRecoveryState().orElse(null):null;
        if(state==null)return new org.joml.Quaternionf();
        var q=state.orientation();
        return com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularOrientation.capture(
                new org.joml.Quaternionf((float)q.x(),(float)q.y(),(float)q.z(),(float)q.w())).copy();
    }

    private static Vec3 ownedBodyRenderPosition(Entity body, float partialTick) {
        return new Vec3(net.minecraft.util.Mth.lerp(partialTick, body.xOld, body.getX()),
                net.minecraft.util.Mth.lerp(partialTick, body.yOld, body.getY()),
                net.minecraft.util.Mth.lerp(partialTick, body.zOld, body.getZ()));
    }

    /** Copies only already-published/read-only render observations; selected reference is capture-owned. */
    private static ConnectedPredictionFixture.Render observeConnectedRender(Minecraft mc,Entity body,Vec3 camera,float partialTick,UUID referenceFrame){
        UUID tracking=null;
        if(body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension ext){var sub=ext.sable$getTrackingSubLevel();if(sub!=null)tracking=sub.getUniqueId();}
        UUID selected=ConnectedPredictionFixture.Render.selectObservationFrame(tracking,referenceFrame);
        var published=com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.publishedInterval(mc.level,body.getUUID());
        var pose=selected==null?null:com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.presentation(mc.level,body.getUUID(),selected,partialTick);
        var timeline=selected==null?null:com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.timeline(mc.level,selected);
        Long newest=timeline==null||timeline.newestTick()==Long.MIN_VALUE?null:timeline.newestTick();
        Long gap=newest==null||published==null?null:newest-published.to();
        var q=mc.gameRenderer.getMainCamera().rotation();
        return new ConnectedPredictionFixture.Render(cadenceTick,partialTick,System.nanoTime(),fixtureVector(camera),fixtureVector(ownedBodyRenderPosition(body,partialTick)),fixtureVector(body.position()),
                selected,referenceFrame,pose==null?List.of():java.util.Arrays.stream(NativeFrameInterval.PoseValue.capture(pose).matrix()).boxed().toList(),published==null?-1:published.to(),
                List.of((double)q.x(),(double)q.y(),(double)q.z(),(double)q.w()),(double)body.getYRot(),(double)body.getXRot(),body.onGround(),tracking,
                published==null?null:published.from(),published==null?null:published.to(),lastReconcileSnapshotTick<0?null:lastReconcileSnapshotTick,lastReconcileAck<0?null:lastReconcileAck,lastReconcileReplayFrames<0?null:lastReconcileReplayFrames,
                pose==null?"UNAVAILABLE":"COMMITTED_PRESENTATION",newest,gap,characterMovementRebaseRevision,"UNAVAILABLE_NO_SAFE_PRESENTATION_HEADING_ACCESSOR");
    }

    private record FixturePose(String kind, String sources, List<Double> matrix,
            com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.Interval interval) { }

    private static ConnectedPredictionFixture.Interval connectedIntervalObservation(Mob body,
            com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope temporal,
            ClientOwnedSimulationClock.Interval clock, long sequence, boolean replay,
            Vec3 before, Vec3 beforeVelocity) {
        try {
            return connectedIntervalObservationAvailable(body, temporal, clock, sequence, replay, before, beforeVelocity);
        } catch (com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException unavailable) {
            connectedPredictionFixture.authorityFailure("OBSERVATION_SCOPE_INTERVAL",connectedPredictionFixture.startFrame(),
                    clock.from(),clock.to(),unavailable.reason().name(),"requested-frame-history-unavailable");
            return null;
        }
    }

    private static ConnectedPredictionFixture.Interval connectedIntervalObservationAvailable(Mob body,
            com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope temporal,
            ClientOwnedSimulationClock.Interval clock, long sequence, boolean replay,
            Vec3 before, Vec3 beforeVelocity) {
        UUID launchFrame = connectedPredictionFixture.startFrame();
        if (launchFrame == null) { connectedPredictionFixture.authorityFailure("LAUNCH_SUPPORT_FRAME_ID",null,
                clock.from(),clock.to(),"NOT_AVAILABLE","unknown"); return null; }
        if (before == null || beforeVelocity == null) { connectedPredictionFixture.authorityFailure("BODY_PRE_MOVE_OBSERVATION",
                launchFrame,clock.from(),clock.to(),"NOT_AVAILABLE","body-state-unavailable"); return null; }
        FixturePose launch = fixturePose(body.level(), temporal, launchFrame,clock.from(),clock.to());
        if (launch == null) return null;
        UUID selectedFrame = temporal.frameId();
        FixturePose selected = selectedFrame == null ? null : fixturePose(body.level(), temporal, selectedFrame,clock.from(),clock.to());
        if (selectedFrame != null && selected == null) return null;
        var air = body instanceof PlayerCharacterHarnessEntity character
                ? character.airMotionReference().orElse(null) : null;
        var nativeMove = lastFramedNativeMove;
        boolean collisionsAvailable = nativeMove != null && nativeMove.collisionInfoFresh()
                && nativeMove.beforePoint() != null && nativeMove.afterPoint() != null;
        Vec3 intervalTranslation = uniformTranslationVelocity(selected == null ? launch.interval() : selected.interval());
        boolean uniform = intervalTranslation != null;
        if (uniform && connectedFixtureTranslationVelocity == null) connectedFixtureTranslationVelocity = intervalTranslation;
        else if (uniform && connectedFixtureTranslationVelocity.distanceToSqr(intervalTranslation) > 1e-12)
            connectedFixtureTranslationConsistent = false;
        if (!uniform) connectedFixtureTranslationConsistent = false;
        uniform &= connectedFixtureTranslationConsistent;
        return new ConnectedPredictionFixture.Interval(clock.from(), clock.to(), sequence,
                connectedFixtureReconciliationGeneration, replay, selectedFrame,
                selected == null ? "UNKNOWN" : selected.kind(), selected == null ? null : selected.sources(),
                fixtureVector(before), fixtureVector(body.position()), fixtureVector(beforeVelocity),
                fixtureVector(body.getDeltaMovement()), air == null ? null : new ConnectedPredictionFixture.Vector(
                        air.pointVelocity().x(), air.pointVelocity().y(), air.pointVelocity().z()),
                selected == null ? List.of() : selected.matrix(), collisionsAvailable, launchFrame,
                launch.matrix(), body.onGround(), uniform, launch.kind(), launch.sources());
    }

    private static FixturePose fixturePose(Level level,
            com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope temporal, UUID id,
            long fromStep,long toStep) {
        SubLevel frame = SubLevelContainer.getContainer(level).getSubLevel(id);
        if (frame == null || frame.isRemoved() || frame.getLevel() != level
                || SubLevelContainer.getContainer(level).getSubLevel(id) != frame) {
            connectedPredictionFixture.authorityFailure("OBSERVATION_FRAME_RESIDENCY",id,fromStep,toStep,
                    "NOT_SAMPLED","not-resident-or-removed"); return null;
        }
        com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.Interval sample;
        try { sample=temporal.requireInterval(level, frame); }
        catch(com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException missing) {
            connectedPredictionFixture.authorityFailure("OBSERVATION_FRAME_INTERVAL_HISTORY",id,fromStep,toStep,
                    missing.reason().name(),"frame-resident"); throw missing;
        }
        String kind = fixtureHistoryKind(sample.motionFrom(), sample.motionTo());
        String sources = "from=" + sample.motionFrom().sourceBefore() + ":" + sample.motionFrom().sourceAfter()
                + ";to=" + sample.motionTo().sourceBefore() + ":" + sample.motionTo().sourceAfter();
        var matrix = java.util.Arrays.stream(NativeFrameInterval.PoseValue.capture(sample.to()).matrix())
                .boxed().toList();
        return new FixturePose(kind, sources, matrix, sample);
    }

    private static String fixtureHistoryKind(
            com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline.Result from,
            com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline.Result to) {
        if (from.kind() == com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline.Kind.PREDICTED
                || to.kind() == com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline.Kind.PREDICTED)
            return "PREDICTED";
        if (from.kind() == com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline.Kind.EXACT
                && to.kind() == com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline.Kind.EXACT)
            return "EXACT";
        return "INTERPOLATED";
    }

    private static Vec3 uniformTranslationVelocity(
            com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.Interval interval) {
        var from = interval.motionFrom(); var to = interval.motionTo();
        if (from == null || to == null || !from.motionKnown() || !to.motionKnown()
                || from.linearVelocity() == null || to.linearVelocity() == null
                || from.angularVelocity() == null || to.angularVelocity() == null) return null;
        var linearDelta = from.linearVelocity().subtract(to.linearVelocity());
        if (linearDelta.lengthSqr() > 1e-12 || from.angularVelocity().lengthSqr() > 1e-12
                || to.angularVelocity().lengthSqr() > 1e-12) return null;
        var a = interval.from().orientation(); var b = interval.to().orientation();
        return Math.abs(a.dot(b)) >= 1 - 1e-9 ? from.linearVelocity() : null;
    }

    private static ConnectedPredictionFixture.Vector fixtureVector(Vec3 value) {
        return value == null ? null : new ConnectedPredictionFixture.Vector(value.x, value.y, value.z);
    }

    private static void predictCharacterInTemporalScope(Mob body, GhostControlPayloads.Intent intent, boolean animate,
                                                        ClientOwnedSimulationClock.Interval simulationInterval) {
        lastFramedNativeMove = null;
        CharacterMovementPolicy policy = characterPolicy(body);
        if (policy == null) return;
        Vec3 initialVelocity = body.getDeltaMovement();
        boolean liveTick = animate;
        MovementVector[] acceptedOwn = {MovementVector.ZERO};
        MovementVector[] frameVelocityCorrection = {MovementVector.ZERO};
        boolean[] basisUnavailable = {false};
        SableOwnedBodyMotion.MoveResult[] nativeDiagnostic = {null};
        // GroundedHarnessMotor's resolver calls this native mover once per tick; capture the
        // exact native result rather than attempting to reconstruct contact from later geometry.
         NativeNormalContactPolicy.Target[] normalTarget = {null};
        GhostPredictionHistory.AngularSupportEvidence[] angularEvidence = {null};
        com.juicyslew.moonstation14.ms14.movement.AirMotionReference[] pendingAirReference = {null};
         AngularSupport[] transitionOrigin = {null};
         boolean[] angularStepEnabled = {false};
        int[] nativeMoveCount = {0};
        com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint[] contactBefore = {null};
        com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint[] contactAfter = {null};
        Vec3[] jumpPrePosition = {null}, jumpPreVelocity = {null};
        double[] jumpPreXo = {0}, jumpPreYo = {0}, jumpPreZo = {0};
        boolean[] jumpPreGround = {false};
        MovementVector[] jumpRequested = {null};
        NativeFrameInterval.PosePair[] jumpPrePoses = {null};
        LoadedFrameRayQuery.ActorPoint[] jumpBasisBefore = {null};
        Vec3[] jumpPreFrameVelocity = {null};
        String[] jumpReference = {"unavailable"};
        UUID[] jumpPreFrame = {null};
        boolean[] jumpStarted = {false};
        CharacterMovementState state = new CharacterMovementState(
                new MovementVector(body.getX(), body.getY(), body.getZ()),
                new MovementVector(initialVelocity.x * 20d, initialVelocity.y * 20d, initialVelocity.z * 20d),
                body.onGround(), body instanceof PlayerCharacterHarnessEntity character
                        ? character.airMotionReference().orElse(null) : null);
        boolean wasSupported = body.onGround();
        float surface = Math.max(0f, Math.min(64f, latestSurfaceFactor));
        float voluntary = Math.max(0f, Math.min(8f, latestVoluntaryFactor));
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(GroundedHarnessPhysics.TICK_SECONDS,
                GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED, GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND,
                GroundedHarnessPhysics.VERTICAL_DRAG, MovementVector.ZERO, 32d,
                  body.maxUpStep(), MovementCollisionResolver.withMotorContext(
                     (before, requested, wasOnGround, idleSupportProbe, motorJumpInitiated) -> {
                      Vec3 old = body.position();
                       boolean wasGrounded = body.onGround();
                         AngularSupport angularWitness = angularSupport(body);
                       var angularPolicy = CharacterIdentitySystem.projectForActor(body)
                               .flatMap(com.juicyslew.moonstation14.component.codec.json.CharacterData::angularRecoveryPolicy).orElse(null);
                        if (wasGrounded && body instanceof PlayerCharacterHarnessEntity
                               && angularPolicy != null && body.isAlive()
                               && !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(body))
                           transitionOrigin[0] = angularWitness;
                      var priorAngular = body instanceof PlayerCharacterHarnessEntity character
                              ? character.angularRecoveryState().orElse(null) : null;
                         var currentBasis = clientFrameState.velocityBasis();
                          var sampledOmega = angularPrediction.sample(activeEpoch, body.getUUID(),
                                 angularWitness == null ? null : angularWitness.frameId(), angularWitness != null,
                                 angularWitness == null ? null : angularWitness.poses(),
                                 angularWitness == null ? null : angularWitness.last(),
                                  angularWitness == null ? null : angularWitness.logical(), GroundedHarnessPhysics.TICK_SECONDS);
                        if (!liveTick) {
                            var evidence = predictionHistory == null ? java.util.Optional.<GhostPredictionHistory.AngularSupportEvidence>empty()
                                    : predictionHistory.angularEvidence(intent.sequence());
                            sampledOmega = AngularRecoveryPrediction.omegaForReplayDeparture(evidence,
                                    currentBasis == null ? null : currentBasis.frameId(), wasGrounded, sampledOmega);
                        }
                       if (liveTick) angularEvidence[0] = new GhostPredictionHistory.AngularSupportEvidence(
                               angularWitness == null ? null : angularWitness.frameId(), angularWitness != null, sampledOmega);
                     var preMoveBounds = body.getBoundingBox();
                    var authorityBasis = clientFrameState.velocityBasis();
                    var freshBasis = authorityBasis == null ? null : LoadedFrameRayQuery.pointAtWorldPosition(
                            body.level(), authorityBasis.frameId(), old);
                    if (harnessKind == MobHarnessKind.CHARACTER && characterMovementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                            && (clientFrameState.paused() || freshBasis == null)) {
                        basisUnavailable[0] = true;
                        return new MovementCollisionResolver.CollisionResult(MovementVector.ZERO, body.onGround());
                    }
                     var temporalScope = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.activeFor(body.level());
                     boolean applyNativeCarry = allowIntervalNativeCarrier(body, temporalScope);
                    var movementExtension = body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension extension
                            ? extension : null;
                    var nativeTracking = movementExtension == null ? null : movementExtension.sable$getTrackingSubLevel();
                    boolean exactTrackedFrame = freshBasis != null && freshBasis.frameId() != null
                            && nativeTracking != null && freshBasis.frameId().equals(nativeTracking.getUniqueId());
                     var collisionPose = clientFrameState.collisionPosePolicy(exactTrackedFrame,
                            applyNativeCarry);
                     boolean matchingJumpFrame = freshBasis != null && currentBasis != null
                             && freshBasis.frameId() != null && freshBasis.frameId().equals(currentBasis.frameId());
                     try {
                         if (wallCapture.qualify(activeEpoch, body.getId(), body.getUUID(), cadenceTick,
                                 intent.sequence(), liveTick, wasGrounded, matchingJumpFrame,
                                 motorJumpInitiated, matchingJumpFrame ? freshBasis.frameId() : null)) {
                             jumpStarted[0] = true;
                             MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase=BEGIN epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} tick={} frame={} live=true grounded={} matchingFrame={} motorJumpInitiated=true",
                                     activeEpoch, body.getUUID(), body.getId(), intent.sequence(), intent.sequence(),
                                     predictionHistory == null ? -1 : predictionHistory.lastAcknowledgedSequence(), cadenceTick,
                                     freshBasis.frameId(), wasGrounded, matchingJumpFrame);
                             for (ConnectedWallCapture.Prelaunch row : wallCapture.takePrelaunchRows())
                                 MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase=RENDER epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} tick={} stage=AFTER_PARTICLES prelaunch=true {}",
                                         activeEpoch, body.getUUID(), body.getId(), intent.sequence(), intent.sequence(),
                                         predictionHistory == null ? -1 : predictionHistory.lastAcknowledgedSequence(), row.tick(), row.row());
                         }
                     } catch(RuntimeException | LinkageError diagnosticFailure) { wallCapture.stop(); }
                     if (wallCapture.jumpActive()) {
                          try {
                              jumpPrePosition[0]=old; jumpPreVelocity[0]=body.getDeltaMovement();
                              jumpPreXo[0]=body.xo; jumpPreYo[0]=body.yo; jumpPreZo[0]=body.zo;
                              jumpPreGround[0]=wasGrounded; jumpRequested[0]=requested;
                             jumpBasisBefore[0]=currentBasis;
                             jumpPreFrameVelocity[0]=frameVelocity(body,currentBasis);
                              jumpPreFrame[0]=freshBasis == null ? null : freshBasis.frameId();
                              jumpReference[0]=String.valueOf(intent.supportedMovementReference());
                              if (jumpStarted[0] && jumpPrePoses[0] == null)
                                  jumpPrePoses[0]=currentFramePosePair(body.level(),jumpPreFrame[0]);
                          } catch(RuntimeException | LinkageError diagnosticFailure) { wallCapture.stop(); }
                      }
                     var bodyPose = harnessKind == MobHarnessKind.CHARACTER
                            && characterMovementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                            ? SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER
                            : SableOwnedBodyMotion.BodyPosePolicy.STOCK_SDK;
                       // Both reconstruction modes query the interval-selected geometry in this scope.
                          boolean observeNativeGeometry = wallCapture.observesGeometry(activeEpoch, body.getId(), body.getUUID());
                          var moved = SableOwnedBodyMotion.move(body, requested,
                                new SableOwnedBodyMotion.MoveOptions(applyNativeCarry, freshBasis, collisionPose,
                                        bodyPose, observeNativeGeometry, true),
                                   idleSupportProbe, motorJumpInitiated);
                         if (moved.collisionInfoFresh())
                             pendingAirReference[0] = com.juicyslew.moonstation14.ms14.player_body_control.movement.AirMotionReferenceTransition.departure(
                                     moved.collisionInfoFresh(), wasGrounded, body.onGround(), moved.beforePoint(),
                                     moved.afterPoint(), moved.frameVelocityDeltaPerSecond());
                        if (body instanceof PlayerCharacterHarnessEntity character) {
                            if (angularPolicy == null || !body.isAlive()
                                    || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(body)) {
                                character.setPredictedAngularRecoveryState(java.util.Optional.empty());
                                angularPrediction.reset();
                             } else {
                                  var next = angularPrediction.afterMove(activeEpoch, body.getUUID(), wasGrounded,
                                        body.onGround(), priorAngular, sampledOmega,
                                        new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0,1,0),
                                        GroundedHarnessPhysics.TICK_SECONDS, angularPolicy.graceSeconds(), angularPolicy.recoverySeconds());
                                 if (next != null) {
                                     character.setPredictedAngularRecoveryState(java.util.Optional.of(next));
                                      angularStepEnabled[0] = true;
                                 }
                            }
                        }
                       if (liveTick && harnessKind == MobHarnessKind.CHARACTER && body.level().isClientSide) {
                           for (var door : com.juicyslew.moonstation14.ms14.doors.AirlockBumpSystem.clientPredictionCandidates(
                                   body, preMoveBounds, new Vec3(requested.x(), requested.y(), requested.z()), moved)) {
                               if (door.predictClientBump(body.level().getGameTime())) airlockPredictions.activate(door);
                               else if (door.hasClientPrediction()) airlockPredictions.activate(door);
                           }
                       }
                      nativeMoveCount[0]++;
                      contactBefore[0] = moved.beforePoint();
                      contactAfter[0] = moved.afterPoint();
                    var collisionResult = moved.toCollisionResult(body.onGround());
                    lastFramedNativeMove = moved;
                     nativeDiagnostic[0] = moved;
                     normalTarget[0] = moved.normalTarget();
                    acceptedOwn[0] = acceptedOwn[0].add(collisionResult.actualDisplacement());
                    frameVelocityCorrection[0] = frameVelocityCorrection[0].add(moved.frameVelocityDeltaPerSecond());
                    if (moved.collisionInfoFresh()) {
                        if (moved.afterPoint() == null) {
                            basisUnavailable[0] = true;
                        } else {
                            clientFrameState.acceptFreshMove(moved.afterPoint());
                            markCurrentFrameInterval(body.level(), activeEpoch, moved.afterPoint());
                        }
                    }
                    return collisionResult;
                 }), surface, voluntary, tetherImpulse(body, state));
        float fallbackMovementYaw = body instanceof PlayerCharacterHarnessEntity character
                ? com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook.worldPlanarYaw(
                        character.angularRecoveryState().orElse(null), intent.yaw()) : intent.yaw();
        var movementReference = intent.supportedMovementReference().orElse(null);
        java.util.UUID actualSupportId = null;
        float currentFrameYaw = 0f;
        if (wasSupported && body instanceof PlayerCharacterHarnessEntity) {
            {
                AngularSupport support = angularSupport(body);
                if (support != null) {
                    actualSupportId = support.frameId();
                    currentFrameYaw = com.juicyslew.moonstation14.ms14.standing.WorldUpTwist.degrees(support.logical());
                }
            }
        }
        float movementYaw = com.juicyslew.moonstation14.ms14.player_body_control.character.SupportedMovementYaw.resolve(
                movementReference, fallbackMovementYaw, wasSupported, actualSupportId, currentFrameYaw);
        var airReference = state.airMotionReference();
        MovementVector motorBaseline = airReference == null ? null : airReference.pointVelocity();
        if (airReference != null && body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                && movement.sable$getTrackingSubLevel() != null) {
            var currentFrame = movement.sable$getTrackingSubLevel();
            var currentPoint = LoadedFrameRayQuery.pointAtWorldPosition(body.level(), currentFrame.getUniqueId(), body.position());
            Vec3 currentPointVelocity = frameVelocity(body, currentPoint);
            if (currentPointVelocity == null) {
                clientFrameState.pause(); predictionHistory.onAuthorityBarrier(); return;
            }
            motorBaseline = com.juicyslew.moonstation14.ms14.player_body_control.movement.AirMotionReferenceTransition
                    .baselineInMotorBasis(airReference,
                            new MovementVector(currentPointVelocity.x, currentPointVelocity.y, currentPointVelocity.z));
        }
        final MovementVector motorBaselineForStep = motorBaseline;
        CharacterMovementState result = CharacterPredictionFailurePolicy.run(() ->
                new GroundedHarnessMotor(policy).tick(state, intent.wishX(), intent.wishZ(),
                        (intent.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0,
                        (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0,
                        movementYaw, environment, latestMovementBlocked, externalFlightPrediction.advanceFrame(),
                        motorBaselineForStep), predictionHistory);
        if (result == null) {
            if (wallCapture.jumpActive() && jumpPrePosition[0] != null) try {
                if (wallCapture.record(activeEpoch,body.getId(),body.getUUID(),cadenceTick,false))
                    MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase=MOVE epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} tick={} replay={} worldStepAccepted=false moveCount={} prePos={} observedPos={} preVelocityBlocksPerTick={} observedVelocityBlocksPerTick={} preGround={} observedGround={} frame={} native={}",
                            activeEpoch,body.getUUID(),body.getId(),wallCapture.triggerSequence(),intent.sequence(),
                            predictionHistory==null?-1:predictionHistory.lastAcknowledgedSequence(),cadenceTick,!animate,nativeMoveCount[0],
                            vector(jumpPrePosition[0].x,jumpPrePosition[0].y,jumpPrePosition[0].z),
                            vector(body.getX(),body.getY(),body.getZ()),
                            vector(jumpPreVelocity[0].x,jumpPreVelocity[0].y,jumpPreVelocity[0].z),
                            vector(body.getDeltaMovement().x,body.getDeltaMovement().y,body.getDeltaMovement().z),
                            jumpPreGround[0],body.onGround(),jumpPreFrame[0],
                            com.juicyslew.moonstation14.ms14.player_body_control.ConnectedJumpNativeFields.describe(nativeDiagnostic[0]));
            } catch(RuntimeException | LinkageError diagnosticFailure) { wallCapture.stop(); }
            // The resolver may already have moved the entity. Never retry, roll back, or replay this frame.
            characterPredictionStopped = true;
            characterPredictionFailedSequence = Math.max(characterPredictionFailedSequence, intent.sequence());
            var rejectedMove = nativeDiagnostic[0];
            boolean captureDiagnostic = wallCapture.mode() != ConnectedWallCapture.Mode.OFF;
            var rejectedScope = rejectedMove == null ? null : rejectedMove.collisionScope();
            var rejectedTrace = rejectedScope == null ? null : rejectedScope.nativeCollisionTrace();
            boolean fullRejectedTrace = wallCapture.takeFullRejectionTrace(activeEpoch, body.getId(), body.getUUID(), cadenceTick, System.nanoTime(),
                    rejectedScope != null && rejectedScope.nativeCollisionTraceComplete(),
                    rejectedTrace == null ? 0 : rejectedTrace.size());
            String nativeRejection = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic.rejectionSummary(
                    rejectedMove == null ? null : rejectedMove.requestedOwnDisplacement(),
                    rejectedMove == null ? null : rejectedMove.acceptedOwnDisplacement(), body.onGround(),
                    null, rejectedMove);
            String nativeDetail = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic
                    .shouldDescribeRejection(captureDiagnostic)
                     ? com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic.describe(body, rejectedMove, fullRejectedTrace)
                    : nativeRejection;
            MoonStation14.LOGGER.error("[ghost control client] CHARACTER prediction stopped after post-move collision rejection; epoch={} entityId={} sequence={} phase={} before={} after={} requested={} native={} onGround={} ack={}; awaiting authoritative snapshots",
                    activeEpoch, ghostEntityId, intent.sequence(), animate ? "tick" : "replay", state.position(),
                    body.position(), nativeDiagnostic[0] == null ? null : nativeDiagnostic[0].acceptedOwnDisplacement(),
                    nativeDetail,
                    body.onGround(), predictionHistory == null ? -1 : predictionHistory.lastAcknowledgedSequence());
            return;
        }
        if (basisUnavailable[0]) {
            clientFrameState.pause();
            predictionHistory.onAuthorityBarrier();
            return;
        }
        if (liveTick && angularStepEnabled[0] && nativeDiagnostic[0] != null
                && nativeDiagnostic[0].collisionInfoFresh() && nativeDiagnostic[0].beforePoint() != null
                && nativeDiagnostic[0].afterPoint() != null) {
            if (wasSupported && !body.onGround() && transitionOrigin[0] != null
                    && transitionOrigin[0].frameId().equals(nativeDiagnostic[0].beforePoint().frameId())) {
                var witness = transitionOrigin[0];
                supportedHeading.transition(activeEpoch, body.getUUID(), witness.frameId(), body.tickCount);
            }
        }
        // A landing changes the support basis only after the completed live native move. Bind
        // that pose as the next interval's baseline; replay moves must never touch live carry.
        if (liveTick && !wasSupported && body.onGround() && body instanceof Mob
                && nativeDiagnostic[0] != null && nativeDiagnostic[0].collisionInfoFresh()
                && nativeDiagnostic[0].afterPoint() != null) {
            Minecraft minecraft = Minecraft.getInstance();
            var support = angularSupport(body);
            if (support != null && support.frameId().equals(nativeDiagnostic[0].afterPoint().frameId()))
                supportedHeading.seed(activeEpoch, body.getUUID(), support.frameId(), support.logical(), true);
            if (angularStepEnabled[0] && support != null
                    && support.frameId().equals(nativeDiagnostic[0].afterPoint().frameId()))
                supportedHeading.transition(activeEpoch, body.getUUID(), support.frameId(), body.tickCount);
        }
        if (nativeDiagnostic[0] != null && body.level() != null) {
            lastNativeObservationClientTick = body.level().getGameTime();
            lastNativeObservationIntentSeq = intent.sequence();
            lastNativeObservationPhase = animate ? "tick" : "replay";
        }
        NativeNormalContactPolicy.Target admittedTarget = com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer
                .targetForStep(normalTarget[0], nativeMoveCount[0], contactBefore[0], contactAfter[0]);
        if (liveTick && admittedTarget != null && predictionHistory != null)
            predictionHistory.recordNormalTarget(intent.sequence(), admittedTarget);
        if (liveTick && angularEvidence[0] != null && predictionHistory != null)
            predictionHistory.recordAngularEvidence(intent.sequence(), angularEvidence[0]);
        NativeNormalContactPolicy.Target finalTarget = admittedTarget;
        MovementVector finalVelocity = com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer
                .finalizeWorld(result.velocity(), frameVelocityCorrection[0], finalTarget,
                        nativeDiagnostic[0] == null ? null : NativeNormalContactFinalizer.landingForStep(
                                nativeDiagnostic[0].landingWitness(), nativeMoveCount[0],
                                nativeDiagnostic[0].beforePoint(), nativeDiagnostic[0].afterPoint()));
        body.setDeltaMovement(finalVelocity.x() / 20d, finalVelocity.y() / 20d,
                finalVelocity.z() / 20d);
        if (body instanceof PlayerCharacterHarnessEntity character) {
            var finalAirReference = com.juicyslew.moonstation14.ms14.player_body_control.movement.AirMotionReferenceTransition.afterSuccessfulMotor(body.onGround(),
                    result.airMotionReference(), pendingAirReference[0]);
            character.setAirMotionReference(finalAirReference);
        }
        if (animate) OwnedHarnessWalkAnimation.update(body, new Vec3(acceptedOwn[0].x(), acceptedOwn[0].y(), acceptedOwn[0].z()));
        if (wallCapture.jumpActive() && jumpPrePosition[0] != null) {
            try {
                if (wallCapture.record(activeEpoch, body.getId(), body.getUUID(), cadenceTick, false)) {
                    var nativeMove=nativeDiagnostic[0];
                    String carrier=com.juicyslew.moonstation14.ms14.player_body_control.ConnectedJumpNativeFields.actualCarrier(
                            nativeMove,nativeMoveCount[0],jumpPrePosition[0].x,jumpPrePosition[0].y,jumpPrePosition[0].z,
                            body.getX(),body.getY(),body.getZ());
                    var afterBasis=clientFrameState.velocityBasis();
                    Vec3 postFrameVelocity=frameVelocity(body,nativeMove==null?null:nativeMove.afterPoint());
                    MoonStation14.LOGGER.info("[CONNECTED_JUMP] side=CLIENT phase={} epoch={} body={} entity={} triggerSeq={} currentSeq={} ack={} serverTick={} tick={} replay={} moveCount={} inputWish={},{},buttons={} reference={} prePos={} postPos={} preOld={} postOld={} preVelocityBlocksPerTick={} postVelocityBlocksPerTick={} preGround={} postGround={} preFrame={} postFrame={} prePoint={} beforePoint={} afterPoint={} basisBefore={} basisAfter={} actualCarrier={} native={}",
                            animate?"MOVE":"REPLAY",activeEpoch,body.getUUID(),body.getId(),wallCapture.triggerSequence(),intent.sequence(),
                            predictionHistory==null?-1:predictionHistory.lastAcknowledgedSequence(),
                            predictionHistory==null?-1:predictionHistory.lastServerGameTick(),cadenceTick,!animate,nativeMoveCount[0],
                            intent.wishX(),intent.wishZ(),intent.buttons(),jumpReference[0],
                            vector(jumpPrePosition[0].x,jumpPrePosition[0].y,jumpPrePosition[0].z),
                            vector(body.getX(),body.getY(),body.getZ()),
                            vector(jumpPreXo[0],jumpPreYo[0],jumpPreZo[0]),vector(body.xo,body.yo,body.zo),
                            vector(jumpPreVelocity[0].x,jumpPreVelocity[0].y,jumpPreVelocity[0].z),
                            vector(body.getDeltaMovement().x,body.getDeltaMovement().y,body.getDeltaMovement().z),
                            jumpPreGround[0],body.onGround(),jumpPreFrame[0],afterBasis==null?"unavailable":afterBasis.frameId(),
                            posePairDescription(jumpPrePoses[0]),pointDescription(nativeMove==null?null:nativeMove.beforePoint()),
                            pointDescription(nativeMove==null?null:nativeMove.afterPoint()),
                            pointDescription(jumpBasisBefore[0])+";velocity="+vectorOrUnavailable(jumpPreFrameVelocity[0]),
                            pointDescription(afterBasis)+";velocity="+vectorOrUnavailable(postFrameVelocity),carrier,
                            com.juicyslew.moonstation14.ms14.player_body_control.ConnectedJumpNativeFields.describe(nativeMove));
                }
            } catch (RuntimeException | LinkageError diagnosticFailure) { wallCapture.stop(); }
        }
    }

    private static void logFramedAuthorityDisagreement(Entity entity, GhostControlPayloads.Snapshot snapshot,
            Vec3 clientBefore, Vec3 projectedAuthority, LoadedFrameRayQuery.ActorPoint basis,
            boolean barrier, int replayCount) {
        double correction = clientBefore.distanceTo(entity.position());
        if (!ClientSnapshotAuthority.shouldReportCorrection(clientBefore, entity.position(), barrier)) return;
        long now = System.currentTimeMillis();
        if (lastFramedDiagnosticEpoch != activeEpoch) {
            lastFramedDiagnosticEpoch = activeEpoch;
            lastFramedDiagnosticMillis = 0;
            framedDiagnosticCount = 0;
        }
        if (framedDiagnosticCount >= 40 || now - lastFramedDiagnosticMillis < 1000) return;
        lastFramedDiagnosticMillis = now;
        framedDiagnosticCount++;
        MoonStation14.LOGGER.warn("[ghost control client] framed prediction correction epoch={} entityId={} ack={} barrier={} replayCount={} serverWorldPoint={} frameStoragePoint={} clientBeforeAuthority={} clientAfterReplay={} correctionMagnitude={} onGround={}",
                activeEpoch, entity.getId(), snapshot.acknowledgedAppliedSequence(), barrier, replayCount,
                new Vec3(snapshot.x(), snapshot.y(), snapshot.z()), basis == null ? null : basis.storagePoint(),
                clientBefore, entity.position(), correction, entity.onGround());
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
