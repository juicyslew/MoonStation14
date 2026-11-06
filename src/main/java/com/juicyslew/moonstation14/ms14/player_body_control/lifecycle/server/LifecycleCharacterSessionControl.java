package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData;
import com.juicyslew.moonstation14.ms14.chat.network.LocalCharacterIdentityPayload;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechNetworking;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.CharacterCadenceDiagnostics;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.player_body_control.network.CharacterMovementModeResolver;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.CommittedSpectatorGuard;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.hands.quarantine.BodyCarrierIsolationBootstrap;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearance;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.util.Optional;
import java.util.Objects;

/** Durable-ACTIVE CHARACTER controller; production activation is limited to the first-account join transaction. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleCharacterSessionControl {
    private static final long READY_TIMEOUT_TICKS = 100;
    private static final int MAX_INTENTS_PER_TICK = 8;
    private static final GroundedHarnessWorldStep CHARACTER_WORLD_STEP = new GroundedHarnessWorldStep();
    private static final Map<MinecraftServer, Map<UUID, Session>> SERVERS = new IdentityHashMap<>();
    private LifecycleCharacterSessionControl() { }

    public enum StartResult {
        INVALID_PLAYER,
        DEBUG_SESSION_CONFLICT,
        INVALID_ACTIVE_BODY,
        CONTROLLER_INTEGRATION_REQUIRED,
        PREPARED
    }

    /**
     * Checks that a caller's already-durable ACTIVE profile could be considered for a session.
     * This deliberately does not mutate the carrier, body, registry, handler, or camera: current
     * shared camera retention recognizes debug sessions only, so beginning a lifecycle camera here
     * could silently fall back to the vanilla spectator camera.
     */
    public static StartResult start(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                     PlayerLifecycleRegistry lifecycle, long epoch) {
        StartResult preflight = preflight(player, body, lifecycle, epoch);
        if (preflight != null) return preflight;
        return StartResult.CONTROLLER_INTEGRATION_REQUIRED;
    }

    private static StartResult preflight(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                         PlayerLifecycleRegistry lifecycle, long epoch) {
        if (player == null || body == null || lifecycle == null || epoch <= 0
                || !(player.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()
                || player instanceof FakePlayer || player.isRemoved() || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || player.level().isClientSide
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            return StartResult.INVALID_PLAYER;
        }
        if (GhostMobHarnessControl.ownsDebugSession(player)) return StartResult.DEBUG_SESSION_CONFLICT;

        var profile = lifecycle.profile(player.getUUID()).orElse(null);
        var binding = body.playerCharacterBinding();
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        MobHarnessId bodyId = new MobHarnessId(body.getUUID());
        if (profile == null || !profile.active() || profile.state() != PlayerLifecycleRegistry.LifecycleState.ACTIVE
                || profile.connectionGeneration() != epoch || !profile.bodyId().equals(bodyId)
                || binding == null || !binding.accountId().equals(player.getUUID())
                || !binding.profileKey().equals(profile.profileId())
                || !binding.mindId().equals(profile.mindId().value())
                || identity == null || !identity.isBound() || !ModCharacters.HUMAN_ID.equals(identity.characterId())
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel() || body.isPassenger()
                || body.level() != level || level.getEntity(body.getUUID()) != body || !body.isNoAi()
                || CharacterIdentitySystem.resolve(body).isEmpty()
                || !lifecycle.authorizes(player.getUUID(), epoch, bodyId,
                        harness -> harness.id().equals(bodyId)
                                && harness.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER)) {
            return StartResult.INVALID_ACTIVE_BODY;
        }

        return null;
    }

    /** Prepares the authenticated Begin/Ready/Commit handshake after durable ACTIVE promotion. */
    static StartResult beginPrepared(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                     PlayerLifecycleRegistry lifecycle, long epoch) {
        StartResult rejection = preflight(player, body, lifecycle, epoch);
        if (rejection != null) return rejection;
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        Map<UUID, Session> sessions = SERVERS.computeIfAbsent(server, ignored -> new java.util.HashMap<>());
        if (sessions.containsKey(player.getUUID())) return StartResult.DEBUG_SESSION_CONFLICT;
        Session session = new Session(player, body, lifecycle, epoch);
        sessions.put(player.getUUID(), session);
        LifecycleSessionPacketRouter.instance().installCharacterHandler(LifecycleCharacterSessionControl::onPayload,
                LifecycleCharacterSessionControl::ownsAny);
        // Login callbacks can precede client player/level binding. The first server Post tick
        // sends Begin only after rechecking the exact connected owner and body.
        return StartResult.PREPARED;
    }

    /**
     * Reports lifecycle camera ownership only for a committed, still-authorized runtime session.
     */
    public static boolean shouldKeepCharacterCamera(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return false;
        MinecraftServer server = player.level().getServer();
        Session session = session(server, player.getUUID());
        return session != null && session.player == player
                && committedCameraOwnershipMatches(session.committed, connectedExact(server, player),
                authorized(session), player.getCamera() == session.body,
                player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, player.isPassenger());
    }

    /** Exact committed owner, independent of the current camera; never changes registry state. */
    public static Optional<Entity> committedControlledEntity(ServerPlayer player) {
        return Optional.ofNullable(committedControlSnapshot(player).owned());
    }

    public static CommittedSpectatorGuard.Ownership committedControlSnapshot(ServerPlayer player) {
        var absent = CommittedSpectatorGuard.Ownership.absent();
        if (player == null || !(player.level() instanceof ServerLevel level)) return absent;
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) return absent;
        Session pinned = session(server, player.getUUID());
        if (pinned == null || pinned.player != player || !pinned.committed) return absent;
        var unavailable = CommittedSpectatorGuard.Ownership.unavailable();
        if (!connectedExact(server, player)
                || !player.isAlive() || player.isPassenger()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || !live(pinned.body, level) || !pinned.bodyId.value().equals(pinned.body.getUUID())
                || pinned.body.playerCharacterBinding() == null
                || !pinned.accountId.equals(pinned.body.playerCharacterBinding().accountId())
                || !pinned.profileId.equals(pinned.body.playerCharacterBinding().profileKey())
                || !pinned.mindId.value().equals(pinned.body.playerCharacterBinding().mindId())
                || !pinned.lifecycle.authorizesActiveCharacterReadOnly(pinned.accountId, pinned.mindId,
                         pinned.epoch, pinned.bodyId)) return unavailable;
        return session(server, player.getUUID()) == pinned
                ? CommittedSpectatorGuard.Ownership.valid(pinned.body)
                : unavailable;
    }

    /** Exact committed controlled body and epoch for read-only queries, independent of camera position. */
    public static Optional<ActiveCharacterBody> committedActorSnapshot(ServerPlayer player) {
        var ownership = committedControlSnapshot(player);
        if (!ownership.committed() || !(ownership.owned() instanceof PlayerCharacterHarnessEntity body)
                || !(player.level() instanceof ServerLevel level)) return Optional.empty();
        Session pinned = session(level.getServer(), player.getUUID());
        if (pinned == null || pinned.player != player || !pinned.committed || pinned.body != body
                || !live(body, level) || !connectedExact(level.getServer(), player)
                || !activeBodyBindingMatches(body.playerCharacterBinding(), pinned.accountId, pinned.profileId, pinned.mindId)
                || !pinned.lifecycle.authorizesActiveCharacterReadOnly(pinned.accountId, pinned.mindId,
                        pinned.epoch, pinned.bodyId)
                || session(level.getServer(), player.getUUID()) != pinned) return Optional.empty();
        return Optional.of(new ActiveCharacterBody(body, pinned.epoch, pinned.mindId, pinned.bodyId));
    }

    /**
     * Returns the exact live body for a committed lifecycle session, not an arbitrary player camera.
     * The immutable result is a point-in-time view; callers must recheck its body and epoch before commit.
     */
    public static Optional<ActiveCharacterBody> activeCharacterBody(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return Optional.empty();
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) return Optional.empty();

        Session pinned = session(server, player.getUUID());
        if (pinned == null || !activeBodySnapshotEligible(pinned.player == player, pinned.committed,
                connectedExact(server, player), server.isSameThread(), player.isAlive(),
                player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, player.isPassenger(),
                player.getCamera() == pinned.body, live(pinned.body, level))
                || !activeBodyBindingMatches(pinned.body.playerCharacterBinding(), pinned.accountId,
                        pinned.profileId, pinned.mindId))
            return Optional.empty();

        if (!pinned.lifecycle.authorizesActiveCharacterReadOnly(pinned.accountId, pinned.mindId,
                pinned.epoch, pinned.bodyId)) return Optional.empty();

        // Keep the same session pinned throughout this snapshot; only cheap identity/entity checks follow.
        if (session(server, player.getUUID()) != pinned || pinned.player != player || pinned.body.level() != level
                || !connectedExact(server, player) || !live(pinned.body, level)
                || !activeBodyBindingMatches(pinned.body.playerCharacterBinding(), pinned.accountId,
                        pinned.profileId, pinned.mindId)) return Optional.empty();
        return Optional.of(new ActiveCharacterBody(pinned.body, pinned.epoch, pinned.mindId, pinned.bodyId));
    }

    /** Read-only Shift state from an intent that this exact committed body successfully applied this tick. */
    public static boolean hasAppliedSneak(ServerPlayer player, PlayerCharacterHarnessEntity body, long epoch) {
        if (player == null || body == null || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        Session session = session(level.getServer(), player.getUUID());
        return session != null && session.player == player && session.body == body && session.epoch == epoch
                && session.committed && com.juicyslew.moonstation14.ms14.player_body_control.action.CharacterAppliedSneak
                        .freshAppliedSneak(session.appliedSneak, session.appliedIntentTick, level.getGameTime())
                && authorized(session)
                && connectedExact(level.getServer(), player) && live(body, level);
    }

    /** Immutable point-in-time authority view for the lifecycle-owned CHARACTER body. */
    public record ActiveCharacterBody(PlayerCharacterHarnessEntity body, long epoch,
                                      MindId mindId, MobHarnessId harnessId) { }

    static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session == null || session.player != player || !connectedExact(server, player)) return;
        if (payload instanceof GhostControlPayloads.Ready ready) {
            if (!session.beginSent || !readyDecision(ready.epoch(), session.epoch, session.committed,
                    level.getGameTime() - session.startedTick, READY_TIMEOUT_TICKS)) return;
            if (preflight(player, session.body, session.lifecycle, session.epoch) != null
                    || !authorized(session)) {
                fail(server, session, FailureReason.PRECOMMIT_INELIGIBLE);
                return;
            }
            Mob mob = session.body;
            if (!(mob instanceof MindControlledMob owner)
                    || !mob.isNoAi() || !live(session.body, level)) {
                fail(server, session, FailureReason.BODY_NOT_INTENT_ELIGIBLE);
                return;
            }
            try {
                owner.moonstation14$setMovementOwned(true);
                if (!owner.moonstation14$isMovementOwned() || !mob.isNoAi()) {
                    fail(server, session, FailureReason.MOVEMENT_OWNERSHIP_REJECTED);
                    return;
                }
            } catch (RuntimeException | Error failure) {
                fail(server, session, FailureReason.MOVEMENT_OWNERSHIP_ERROR);
                return;
            }
            player.setCamera(session.body);
            try {
                if (BodyCarrierIsolationBootstrap.ensure(player, session.body)
                        == BodyCarrierIsolationBootstrap.Result.REJECTED)
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                            "Lifecycle CHARACTER carrier isolation rejected; hand actions remain denied (body={})",
                            session.body.getId());
            } catch (RuntimeException | Error failure) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                        "Lifecycle CHARACTER carrier isolation failed; hand actions remain denied (body={})",
                        session.body.getId(), failure);
            }
            try {
                BodyHandBootstrap.Result hands = BodyHandBootstrap.ensure(session.body);
                if (hands == BodyHandBootstrap.Result.REJECTED)
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                            "Lifecycle CHARACTER hand bootstrap rejected; movement remains eligible (body={})", session.body.getId());
            } catch (RuntimeException | Error failure) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                        "Lifecycle CHARACTER hand bootstrap failed; movement remains eligible (body={})",
                        session.body.getId(), failure);
            }
            session.committed = true;
            GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Commit(session.epoch));
            // Commit reaches the client before this full owner snapshot; its receive path reconciles
            // the just-committed account/body/epoch before applying the strict snapshot policy.
            com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService
                    .publishLifecycleReady(player, session.body, session.epoch);
            sendSavedIdentity(server, session);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "Lifecycle CHARACTER Ready committed (epoch={}, body={})", session.epoch, session.body.getId());
        } else if (payload instanceof GhostControlPayloads.Intent intent) {
            if (!session.committed || intent.epoch() != session.epoch) return;
            if (!authorized(session) || !connectedExact(server, player) || !shouldKeepCharacterCamera(player)
                    || !intentEligible(session, level)) {
                fail(server, session, FailureReason.RUNTIME_INELIGIBLE);
                return;
            }
            long tick = level.getGameTime();
            if (session.intentTick != tick) {
                session.intentTick = tick;
                session.intentCount = 0;
            }
            if (!intentRateAllows(++session.intentCount)) {
                boolean transition = !session.traceRateRejected;
                session.traceRateRejected = true;
                trace(session, "INTAKE", level.getGameTime(), transition, "clientSeq=" + intent.sequence()
                        + " wish=" + intent.wishX() + "," + intent.wishZ() + " buttons=" + intent.buttons()
                        + " rateAccepted=false queueOfferAccepted=false queuedSize=" + session.gate.queuedSize());
                return;
            }
            boolean offered = session.gate.offerQueued(intent.sequence(), intent,
                    com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory.CAPACITY);
            boolean changed = intent.wishX() != session.traceWishX || intent.wishZ() != session.traceWishZ
                    || intent.buttons() != session.traceButtons || (!offered && !session.traceOfferRejected)
                    || session.traceRateRejected;
            session.traceRateRejected = false;
            session.traceOfferRejected = !offered;
            session.traceWishX = intent.wishX(); session.traceWishZ = intent.wishZ(); session.traceButtons = intent.buttons();
            trace(session, "INTAKE", level.getGameTime(), changed,
                    "clientSeq=" + intent.sequence() + " wish=" + intent.wishX() + "," + intent.wishZ()
                            + " buttons=" + intent.buttons() + " rateAccepted=true queueOfferAccepted=" + offered
                            + " queuedSize=" + session.gate.queuedSize());
            if (session.gate.exhausted()) fail(server, session, FailureReason.INTENT_GATE_EXHAUSTED);
        } else if (payload instanceof GhostControlPayloads.Stop stop && stop.epoch() == session.epoch) {
            fail(server, session, FailureReason.CLIENT_STOP);
        }
    }

    /** Only the exact committed owner receives the already-saved identity for this bound body. */
    private static void sendSavedIdentity(MinecraftServer server, Session session) {
        ServerPlayer player = session.player;
        if (!server.isSameThread() || !connectedExact(server, player) || !session.committed
                || session(server, session.accountId) != session || !shouldKeepCharacterCamera(player)
                || !activeBodyBindingMatches(session.body.playerCharacterBinding(), session.accountId,
                        session.profileId, session.mindId)) return;
        try {
            var identity = ChatIdentitySavedData.existing(server.overworld())
                    .flatMap(data -> data.character(new ChatIdentityRegistry.CharacterKey(
                            session.accountId, session.profileId))).orElse(null);
            if (identity == null) return; // No lookup may create or allocate an identity here.
            LocalSpeechNetworking.sendIdentity(player, new LocalCharacterIdentityPayload(
                    identity.name(), identity.rgb(), session.body.level().dimension().location()));
        } catch (RuntimeException failure) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn("Could not load or deliver committed character identity to owner", failure);
        }
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session == null || session.player != player || !session.committed) return;
        if (!connectedExact(server, player) || !authorized(session) || !shouldKeepCharacterCamera(player)) {
            fail(server, session, FailureReason.RUNTIME_INELIGIBLE);
            return;
        }
        Mob body = session.body;
        long traceTick = level.getGameTime();
        long serverTick = server.getTickCount();
        session.traceCallbacks = session.traceCallbackTick == serverTick ? session.traceCallbacks + 1 : 1;
        session.traceCallbackTick = serverTick;
        if (session.movementSuspended) {
            boolean exactHeldOwner = heldOwnerEligible(session, player, body, level);
            var decision = com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementHoldPolicy
                    .evaluate(exactHeldOwner, exactHeldOwner && knownSnapshotPose(session, body, level));
            if (decision == com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementHoldPolicy.Decision.TERMINAL_GUARD) {
                fail(server, session, FailureReason.RUNTIME_INELIGIBLE);
                return;
            }
            clearQueuedMovement(session);
            if (decision == com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementHoldPolicy.Decision.HOLD) {
                body.setDeltaMovement(Vec3.ZERO);
                boolean holdChanged = !session.traceHeld;
                session.traceHeld = true;
                trace(session, "PLAYER_POST_HOLD", traceTick, holdChanged, "callbacks=" + session.traceCallbacks
                        + " queued=" + session.gate.queuedSize() + " ack=" + session.lastAppliedSequence
                        + " pos=" + body.position() + " onGround=" + body.onGround() + " suspended=true reason=HOLD");
                return;
            }
            session.movementSuspended = false;
            session.traceHeld = false;
            session.movementRebaseRevision++;
            session.unsafePoseLogged = false;
            clearQueuedMovement(session);
            body.setDeltaMovement(Vec3.ZERO);
                return;
        }
        if (!intentEligible(session, level)) {
            fail(server, session, FailureReason.RUNTIME_INELIGIBLE);
            return;
        }
        // This is callback dispatch ownership, not a physical-step test: another owner
        // may already have advanced this actor (e.g. flight) on this tick.
        if (session.lastInputDispatchTick == serverTick) return;
        session.lastInputDispatchTick = serverTick; // failed/rejected attempts cannot retry this batch this tick
        GhostControlPayloads.Intent freshIntent = session.movementInput.acceptQueued(session.gate);
        if (freshIntent != null) session.lastAppliedSequence = freshIntent.sequence();
        GhostControlPayloads.Intent intent = session.movementInput.held();
        Boolean physicalConsumedBefore = observedPhysicalStepConsumed(body, level, serverTick);
        Boolean physicalConsumedAfter = physicalConsumedBefore;
        boolean stepAttempted = false;
        boolean stepJump = false;
        String traceOutcome = freshIntent == null && intent != null ? "QUEUEEMPTY_HELD" : intent == null ? "INACTIVE" : "PENDING";
        if (intent != null) {
            try {
                stepAttempted = true;
                stepJump = session.movementInput.jumpForStep();
                var result = session.worldStep.step(body, intent.wishX(), intent.wishZ(),
                        stepJump,
                        (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, intent.yaw(),
                        intent.supportedMovementReference());
                if (result.isEmpty()) {
                    traceOutcome = "REJECTED";
                    recoverWorldStep(session, body, intent, FailureReason.WORLD_STEP_REJECTED, null);
                } else {
                    traceOutcome = "APPLIED";
                    body.setYRot(intent.yaw());
                    body.setXRot(intent.pitch());
                    body.setYHeadRot(intent.yaw());
                    session.appliedSneak = (intent.buttons() & GhostControlPayloads.BUTTON_SNEAK) != 0;
                    session.appliedIntentTick = level.getGameTime();
                }
                session.movementInput.afterStep();
                physicalConsumedAfter = observedPhysicalStepConsumed(body, level, serverTick);
            } catch (RuntimeException | LinkageError failure) {
                traceOutcome = "ERROR";
                recoverWorldStep(session, body, intent, FailureReason.WORLD_STEP_ERROR, failure);
            }
        }
        boolean changed = traceOutcome.equals("REJECTED") || traceOutcome.equals("ERROR")
                || session.traceLastOutcome == null || !traceOutcome.equals(session.traceLastOutcome);
        if (changed || (serverTick % 20 == 0 && session.tracePlayerPostTick != serverTick)) {
            session.tracePlayerPostTick = serverTick;
            trace(session, "PLAYER_POST", traceTick, changed,
                "queued=" + session.gate.queuedSize() + " freshSeq=" + (freshIntent == null ? -1 : freshIntent.sequence())
                        + " ack=" + session.lastAppliedSequence + " held=" + (intent == null ? "none" : intent.wishX()+","+intent.wishZ())
                        + " jump=" + stepJump + " stepAttempted=" + stepAttempted + " physicalStepConsumed="
                        + physicalConsumedBefore + "->" + physicalConsumedAfter
                        + " callbacks=" + session.traceCallbacks + " pos=" + body.position() + " delta=" + body.getDeltaMovement()
                        + " onGround=" + body.onGround() + " suspended=" + session.movementSuspended
                        + " result=" + traceOutcome + " recovery=" + (session.movementSuspended ? "held" : "none")
                        + " reconciliation={"+session.latestSnapshotDiagnostic+"} samplesSuppressed="+session.traceSamplesSuppressed);
        }
        session.traceLastOutcome = traceOutcome;
        // Normal per-tick publication runs in the SERVER_POST fallback, after level-owned passive physics.
    }

    /** Publishes committed character state after the level's physical fallback has completed. */
    public static void publishAfterPhysicalTick(MinecraftServer server) {
        if (server == null || !server.isSameThread()) return;
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null) return;
        for (Session session : java.util.List.copyOf(sessions.values())) {
            ServerPlayer player = session.player;
            if (!session.committed || player == null || !(player.level() instanceof ServerLevel level)
                    || sessions.get(session.accountId) != session) continue;
            if (!connectedExact(server, player) || !authorized(session) || !shouldKeepCharacterCamera(player)) continue;
            // Always route eligible sessions through the sole snapshot producer: it owns
            // unknown-pose suspension/rebase as well as safe snapshot admission.
            sendCharacterSnapshot(server, session, session.body);
        }
    }

    private static boolean heldOwnerEligible(Session session, ServerPlayer player, Mob body, ServerLevel level) {
        var binding = session.body.playerCharacterBinding();
        return session.player == player && player.isAlive()
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR && !player.isPassenger()
                && session.body == body && live(session.body, level)
                && body.isNoAi() && body instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                && session.bodyId.equals(new MobHarnessId(body.getUUID()))
                && activeBodyBindingMatches(binding, session.accountId, session.profileId, session.mindId)
                && CharacterIdentitySystem.resolve(body).isPresent();
    }

    /** Per-session, scoped substitution for owned GameTest fault injection; production always uses the native resolver. */
    @FunctionalInterface
    interface WorldStep {
        java.util.Optional<com.juicyslew.moonstation14.ms14.movement.CharacterMovementState> step(
                Mob body, int wishX, int wishZ, boolean jump, boolean sprint, float yaw,
                java.util.Optional<GhostControlPayloads.SupportedMovementReference> supportedMovementReference);
    }

    @FunctionalInterface
    interface SafePoseObserver {
        boolean hasKnownSafePoint(Mob body, ServerLevel level);
    }

    static AutoCloseable withWorldStepForTesting(ServerPlayer player, WorldStep injected) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(injected, "injected");
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread())
            throw new IllegalStateException("world-step injection requires the server thread");
        Session session = session(level.getServer(), player.getUUID());
        if (session == null || session.player != player || session.worldStepInjected)
            throw new IllegalStateException("exact lifecycle session missing or already has a scoped resolver");
        WorldStep previous = session.worldStep;
        session.worldStep = injected;
        session.worldStepInjected = true;
        return () -> {
            if (session(level.getServer(), player.getUUID()) != session) return;
            if (session.player != player || !session.worldStepInjected || session.worldStep != injected)
                throw new IllegalStateException("scoped lifecycle world-step owner changed before restore");
            session.worldStep = previous;
            session.worldStepInjected = false;
        };
    }

    static AutoCloseable withSafePoseObserverForTesting(ServerPlayer player, SafePoseObserver injected) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(injected, "injected");
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread())
            throw new IllegalStateException("pose observation injection requires the server thread");
        Session session = session(level.getServer(), player.getUUID());
        if (session == null || session.player != player || session.safePoseInjected)
            throw new IllegalStateException("exact lifecycle session missing or already has scoped pose observation");
        SafePoseObserver previous = session.safePoseObserver;
        session.safePoseObserver = injected;
        session.safePoseInjected = true;
        return () -> {
            if (session(level.getServer(), player.getUUID()) != session) return;
            if (!session.safePoseInjected || session.safePoseObserver != injected)
                throw new IllegalStateException("scoped pose observer changed before restore");
            session.safePoseObserver = previous;
            session.safePoseInjected = false;
        };
    }

    private static void recoverWorldStep(Session session, Mob body, GhostControlPayloads.Intent intent,
                                         FailureReason reason, Throwable failure) {
        ServerLevel level = body.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        MinecraftServer server = level == null ? null : level.getServer();
        boolean ownerValid = server != null && connectedExact(server, session.player)
                && session(server, session.accountId) == session && authorized(session)
                && session.body == body && live(session.body, level);
        var outcome = com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterWorldStepRecoveryPolicy
                .afterStep(false, ownerValid, level != null && knownSafePose(body, level));
        if (outcome == com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterWorldStepRecoveryPolicy.Outcome.TERMINAL) {
            fail(server, session, FailureReason.RUNTIME_INELIGIBLE);
            return;
        }
        body.setDeltaMovement(Vec3.ZERO);
        clearQueuedMovement(session);
        session.appliedSneak = false;
        session.appliedIntentTick = Long.MIN_VALUE;
        session.movementRebaseRevision++;
        if (outcome == com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterWorldStepRecoveryPolicy.Outcome.HOLD) {
            suspendMovement(session, body, reason, intent.sequence(), failure);
            return;
        }
        long tick = level.getGameTime();
        if (tick - session.lastMovementDiagnosticTick >= 100) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                    "Lifecycle CHARACTER movement rebased without ending ownership reason={} bodyEntityId={} sequence={} position={} cause={}",
                    reason, body.getId(), intent.sequence(), body.position(), failure == null ? "bounded motor rejection" : failure.getClass().getSimpleName());
            session.lastMovementDiagnosticTick = tick;
        }
    }

    private static boolean knownSafePose(Mob body, ServerLevel level) {
        return com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementSafePose
                .valid(level, body.position());
    }

    private static boolean knownSnapshotPose(Session session, Mob body, ServerLevel level) {
        return knownSafePose(body, level) && session.safePoseObserver.hasKnownSafePoint(body, level);
    }

    private static boolean defaultSafePose(Mob body, ServerLevel level) {
        if (!knownSafePose(body, level)) return false;
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, body);
        return com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementSafePose
                .snapshotAdmissible(level, body.position(), point == null ? null : point.storagePoint(), point != null);
    }

    private static void suspendMovement(Session session, Mob body, FailureReason reason, long sequence,
                                        Throwable failure) {
        body.setDeltaMovement(Vec3.ZERO);
        if (!session.movementSuspended) session.movementRebaseRevision++;
        session.movementSuspended = true;
        clearQueuedMovement(session);
        if (session.unsafePoseLogged) return;
        com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "Lifecycle CHARACTER movement held pending a known safe authority point reason={} bodyEntityId={} sequence={} cause={}",
                reason, body.getId(), sequence, failure == null ? "unknown or invalid current pose" : failure.getClass().getSimpleName());
        session.unsafePoseLogged = true;
        session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Character movement is paused while its safe position is verified. Ownership and inventory are retained; movement will resume automatically when the body is safe."));
    }

    /** Discards real not-yet-applied commands at a movement authority barrier; ACK settles that real skipped range. */
    private static void clearQueuedMovement(Session session) {
        long discardedThrough = session.gate.discardQueuedThroughSequence();
        if (discardedThrough >= 0) session.lastAppliedSequence = Math.max(session.lastAppliedSequence, discardedThrough);
        session.movementInput.clear();
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!connectedExact(server, session.player)) {
                cleanLogout(server, session);
            } else if (!live(session.body, (ServerLevel) session.player.level())) {
                fail(server, session, FailureReason.BODY_NOT_LIVE);
            } else if (!session.beginSent) {
                if (preflight(session.player, session.body, session.lifecycle, session.epoch) != null
                        || !authorized(session)) {
                    fail(server, session, FailureReason.PRECOMMIT_INELIGIBLE);
                    continue;
                }
                if (!delayedBeginDecision(sessions.get(session.accountId) == session,
                        connectedExact(server, session.player), live(session.body, (ServerLevel) session.player.level()),
                        session.beginSent)) continue;
                try {
                    GhostControlNetworking.sendToPlayer(session.player, new GhostControlPayloads.Begin(
                            session.epoch, session.body.getId(), MobHarnessKind.CHARACTER));
                    session.beginSent = true;
                    session.startedTick = session.player.level().getGameTime();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "Lifecycle CHARACTER Begin sent (epoch={}, body={})", session.epoch, session.body.getId());
                } catch (RuntimeException | Error failure) {
                    fail(server, session, FailureReason.BEGIN_SEND_ERROR);
                }
            } else if (timeoutDecision(session.beginSent, session.committed,
                    session.player.level().getGameTime() - session.startedTick, READY_TIMEOUT_TICKS)) {
                boolean exactTimeoutOwner = !session.committed && sessions.get(session.accountId) == session
                        && connectedExact(server, session.player) && authorized(session)
                        && session.player.level() instanceof ServerLevel currentLevel
                        && live(session.body, currentLevel) && session.body.isNoAi();
                if (exactTimeoutOwner) {
                    session.startedTick = session.player.level().getGameTime();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                            "Lifecycle CHARACTER readiness remains pending; retaining exact live session epoch={} bodyEntityId={}",
                            session.epoch, session.body.getId());
                    try {
                        GhostControlNetworking.sendToPlayer(session.player, new GhostControlPayloads.Begin(
                                session.epoch, session.body.getId(), MobHarnessKind.CHARACTER));
                    } catch (RuntimeException | LinkageError failure) {
                        fail(server, session, FailureReason.BEGIN_SEND_ERROR);
                    }
                } else {
                    fail(server, session, FailureReason.READY_TIMEOUT);
                }
            }
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session != null && session.player == player) cleanLogout(server, session);
    }

    /** Drain while level entities and the startup context still exist, ahead of world saving. */
    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions != null)
            for (Session session : sessions.values().toArray(Session[]::new)) cleanLogout(server, session);
    }

    private static boolean cleanLogout(MinecraftServer server, Session session) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.accountId) != session) return false;
        ServerLevel level = session.body.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        var binding = session.body.playerCharacterBinding();
        boolean exactBody = level != null && live(session.body, level) && session.body.isNoAi()
                && binding != null && binding.accountId().equals(session.accountId)
                && binding.profileKey().equals(session.profileId) && binding.mindId().equals(session.mindId.value())
                && session.body.getUUID().equals(session.bodyId.value());
        boolean offline = false;
        long offlineSinceMillis = Math.max(0L, System.currentTimeMillis());
        if (exactBody) {
            var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
            if (context != null) {
                var appearance = Map.of("appearance", session.body.appearance().name(),
                        "bodyShape", session.body.bodyShape().name());
                try {
                    offline = context.disconnectCleanly(session.accountId, session.epoch, session.mindId.value(),
                            session.bodyId.value(), level.dimension().location().toString(),
                            new SavedLifecycleProfile.Location(session.body.getX(), session.body.getY(), session.body.getZ()),
                            appearance, offlineSinceMillis);
                } catch (IOException | RuntimeException failure) {
                    offline = false;
                }
            }
        }
        if (offline && exactBody) session.body.setOfflineSinceMillis(offlineSinceMillis);
        if (!offline) {
            session.lifecycle.suspendActiveSessionForRecovery(session.accountId, session.mindId,
                    session.bodyId, session.epoch);
        }
        sessions.remove(session.accountId);
        if (sessions.isEmpty()) SERVERS.remove(server);
        session.gate.reset();
        session.movementInput.clear();
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
        return offline;
    }

    /** Durably detach an exact live CHARACTER session before allowing an operator into vanilla Creative. */
    static boolean parkForDevelopmentMode(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session == null || session.player != player || !parkPreflight(server, player, session)) return false;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return false;
        long offlineSinceMillis = Math.max(0L, System.currentTimeMillis());
        var appearance = Map.of("appearance", session.body.appearance().name(),
                "bodyShape", session.body.bodyShape().name());
        boolean persisted;
        try {
            persisted = context.disconnectCleanly(session.accountId, session.epoch, session.mindId.value(),
                    session.bodyId.value(), level.dimension().location().toString(),
                    new SavedLifecycleProfile.Location(session.body.getX(), session.body.getY(), session.body.getZ()),
                    appearance, offlineSinceMillis);
        } catch (IOException | RuntimeException failure) {
            persisted = false;
        }
        return finishDevelopmentPark(persisted, () -> {
        session.body.setOfflineSinceMillis(offlineSinceMillis);
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions != null && sessions.get(session.accountId) == session) {
            sessions.remove(session.accountId);
            if (sessions.isEmpty()) SERVERS.remove(server);
        }
        session.gate.reset();
        session.movementInput.clear();
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
        try {
            GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Stop(session.epoch));
        } catch (RuntimeException | Error ignored) {
            // Durable OFFLINE is authoritative; a failed stop packet cannot restore the revoked epoch.
        }
        player.setCamera(player);
        });
    }

    static boolean finishDevelopmentPark(boolean persisted, Runnable revokeSession) {
        if (!persisted) return false;
        revokeSession.run();
        return true;
    }

    private static boolean parkPreflight(MinecraftServer server, ServerPlayer player, Session session) {
        return server != null && server.isSameThread() && connectedExact(server, player)
                && session.player == player && authorized(session)
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                && !player.isPassenger()
                && live(session.body, (ServerLevel) player.level()) && session.body.isNoAi();
    }

    static boolean developmentModeDecision(boolean ownsSession, GameType requested, boolean operator) {
        if (!ownsSession) return true;
        return requested == GameType.SPECTATOR || requested == GameType.CREATIVE && operator;
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions != null) for (Session session : sessions.values().toArray(Session[]::new)) stopSession(server, session);
        SERVERS.remove(server);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
    }

    enum FailureReason {
        CONNECTION_MISMATCH, MODE_MISMATCH, PLAYER_PASSENGER, CAMERA_MISMATCH,
        AUTHORIZATION_REVOKED, BODY_NOT_LIVE, BODY_NOT_INTENT_ELIGIBLE,
        PRECOMMIT_INELIGIBLE, RUNTIME_INELIGIBLE, MOVEMENT_OWNERSHIP_REJECTED,
        MOVEMENT_OWNERSHIP_ERROR, INTENT_GATE_EXHAUSTED, CLIENT_STOP,
        WORLD_STEP_REJECTED, WORLD_STEP_ERROR, BEGIN_SEND_ERROR, READY_TIMEOUT,
        SNAPSHOT_BINDING_MISMATCH, SNAPSHOT_SEND_ERROR, POSE_UNSAFE_HOLD, APPEARANCE_FAILURE, DEATH_CLAIM_FAILURE
    }

    /** Priority is deterministic when more than one prerequisite is false at the failure site. */
    static FailureReason classifyRuntimeFailure(boolean exactConnection, boolean spectator,
                                                boolean passenger, boolean exactCamera,
                                                boolean authorized, boolean bodyLive,
                                                boolean intentEligible) {
        if (!exactConnection) return FailureReason.CONNECTION_MISMATCH;
        if (!spectator) return FailureReason.MODE_MISMATCH;
        if (passenger) return FailureReason.PLAYER_PASSENGER;
        if (!exactCamera) return FailureReason.CAMERA_MISMATCH;
        if (!authorized) return FailureReason.AUTHORIZATION_REVOKED;
        if (!bodyLive) return FailureReason.BODY_NOT_LIVE;
        if (!intentEligible) return FailureReason.BODY_NOT_INTENT_ELIGIBLE;
        return FailureReason.RUNTIME_INELIGIBLE;
    }

    private static void fail(MinecraftServer server, Session session, FailureReason cause) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.player.getUUID()) != session) return;
        ServerPlayer player = session.player;
        ServerLevel level = player.level() instanceof ServerLevel current ? current : null;
        boolean exactConnection = connectedExact(server, player);
        boolean spectator = player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
        boolean passenger = player.isPassenger();
        Entity camera = player.getCamera();
        boolean exactCamera = camera == session.body;
        boolean bodyLive = level != null && live(session.body, level);
        boolean authorized;
        boolean eligible;
        try {
            authorized = authorized(session);
        } catch (RuntimeException | Error diagnosticFailure) {
            authorized = false;
        }
        try {
            eligible = level != null && intentEligible(session, level);
        } catch (RuntimeException | Error diagnosticFailure) {
            eligible = false;
        }
        FailureReason reason = cause == FailureReason.RUNTIME_INELIGIBLE
                ? classifyRuntimeFailure(exactConnection, spectator, passenger, exactCamera,
                        authorized, bodyLive, eligible)
                : cause == FailureReason.PRECOMMIT_INELIGIBLE && !authorized
                    ? FailureReason.AUTHORIZATION_REVOKED : cause;
        // Capture before revoking authority or disconnecting; never log account/profile/mind IDs.
        com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "Lifecycle CHARACTER terminal failure reason={} phase={} epoch={} expectedBodyEntityId={} cameraEntityId={} mode={} passenger={} exactConnection={} authorized={} bodyLive={} intentEligible={}",
                reason, session.committed ? "COMMITTED" : "HANDSHAKE", session.epoch,
                session.body.getId(), camera == null ? -1 : camera.getId(),
                player.gameMode.getGameModeForPlayer(), passenger, exactConnection, authorized, bodyLive, eligible);
        sessions.remove(session.player.getUUID());
        if (sessions.isEmpty()) SERVERS.remove(server);
        session.movementInput.clear();
        session.gate.reset();
        clearOwnedMarkerForSession(session);
        session.lifecycle.suspendActiveSessionForRecovery(session.player.getUUID(), session.mindId,
                session.bodyId, session.epoch);
        if (connectedExact(server, session.player)) session.player.connection.disconnect(
                net.minecraft.network.chat.Component.literal("Character session requires explicit recovery (" + reason + ")."));
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
    }

    /** Server shutdown ends only ephemeral controller state; durable ACTIVE claims are not deleted or rewritten. */
    private static void stopSession(MinecraftServer server, Session session) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.player.getUUID()) != session) return;
        sessions.remove(session.player.getUUID());
        if (sessions.isEmpty()) SERVERS.remove(server);
        session.movementInput.clear();
        session.gate.reset();
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
    }

    private static void clearOwnedMarkerForSession(Session session) {
        Mob body = session.body;
        if (body instanceof MindControlledMob owner
                && session.bodyId.equals(new MobHarnessId(session.body.getUUID()))
                && session.body.playerCharacterBinding() != null
                && session.body.playerCharacterBinding().accountId().equals(session.accountId)
                && session.body.playerCharacterBinding().profileKey().equals(session.profileId)
                && session.body.playerCharacterBinding().mindId().equals(session.mindId.value())) {
            try {
                owner.moonstation14$setMovementOwned(false);
            } catch (RuntimeException | Error ignored) {
                // Fail closed: leave the custom body protected if ownership cannot be safely cleared.
            }
        }
    }

    private static boolean intentEligible(Session session, ServerLevel level) {
        Mob mob = session.body;
        return mob instanceof MindControlledMob owner
                && live(session.body, level) && mob.isNoAi() && owner.moonstation14$isMovementOwned()
                && session.bodyId.equals(new MobHarnessId(session.body.getUUID()));
    }

    private static void sendCharacterSnapshot(MinecraftServer server, Session session, Mob body) {
        long tick = body.level().getGameTime();
        if (tick <= session.lastSnapshotTick) return;
        Vec3 position = body.position();
        if (!knownSafePose(body, (ServerLevel) body.level())
                || !session.safePoseObserver.hasKnownSafePoint(body, (ServerLevel) body.level())) {
            boolean firstDenied = !"POSE_UNSAFE_HOLD".equals(session.traceSnapshotDeniedReason);
            session.traceSnapshotDeniedReason = "POSE_UNSAFE_HOLD";
            trace(session, "SNAPSHOT_DENIED", tick, firstDenied, "reason=POSE_UNSAFE_HOLD ack=" + session.lastAppliedSequence
                    + " pos=" + position + " suspended=" + session.movementSuspended);
            suspendMovement(session, body, FailureReason.POSE_UNSAFE_HOLD, session.lastAppliedSequence, null);
            return; // Never construct a wire snapshot from nonfinite/out-of-bounds or unknown pose data.
        }
        var actorPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(
                (ServerLevel) body.level(), body);
        if (actorPoint == null || !com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementSafePose
                .snapshotAdmissible((ServerLevel) body.level(), position,
                        actorPoint == null ? null : actorPoint.storagePoint(), actorPoint != null)) {
            boolean firstDenied = !"NO_FRAME".equals(session.traceSnapshotDeniedReason);
            session.traceSnapshotDeniedReason = "NO_FRAME";
            trace(session, "SNAPSHOT_DENIED", tick, firstDenied, "reason=NO_FRAME ack=" + session.lastAppliedSequence
                    + " pos=" + position + " suspended=" + session.movementSuspended);
            suspendMovement(session, body, FailureReason.POSE_UNSAFE_HOLD, session.lastAppliedSequence, null);
            return; // unknown tracking is never reinterpreted as a world-frame snapshot
        }
        Vec3 velocity = body.getDeltaMovement().scale(20d);
        float yaw = (float) (body.getYRot() - 360.0 * Math.floor((body.getYRot() + 180.0) / 360.0));
        float pitch = Math.max(-90f, Math.min(90f, body.getXRot()));
        float surface = (float) SlidingFrictionSystem.frictionFactor(body);
        boolean down = com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(body);
        float voluntary = (float) (down
                ? com.juicyslew.moonstation14.ms14.standing.PostureSystem.crawlMultiplier(body)
                : CharacterControlSystem.isKnockedDown(body)
                ? com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR : 1d);
        var owner = BodyContainmentService.forLevel((ServerLevel) body.level()).ownerOf(body).orElse(null);
        var movementMode = CharacterMovementModeResolver.fromExactOwner(owner);
        try {
            var snapshot = new GhostControlPayloads.Snapshot(session.epoch, body.getId(), MobHarnessKind.CHARACTER,
                    tick, session.lastAppliedSequence, position.x, position.y, position.z, yaw, pitch,
                    velocity.x, velocity.y, velocity.z, body.onGround(), surface, voluntary,
                    !CharacterControlSystem.canMove(body), movementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                            ? com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.remainingTicks(body) : 0,
                     movementMode, java.util.Optional.ofNullable(owner).map(com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner::view),
                     (movementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED ? java.util.Optional.ofNullable(actorPoint.frameId()) : java.util.Optional.<java.util.UUID>empty()).map(id -> new GhostControlPayloads.Snapshot.FrameState(
                             id, actorPoint.storagePoint().x, actorPoint.storagePoint().y, actorPoint.storagePoint().z)),
                      session.movementRebaseRevision).withAngularRecoveryState(
                               body instanceof PlayerCharacterHarnessEntity character
                                       && movementMode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                                       ? character.authoritativeAngularRecoveryState() : java.util.Optional.empty())
                     .withServerTemporalMetadata(completedSableStep((ServerLevel) body.level()),
                             body instanceof PlayerCharacterHarnessEntity character ? character.airMotionReference() : java.util.Optional.empty());
            if (!snapshotBoundToSession(snapshot, session.epoch, body.getId())) {
                fail(server, session, FailureReason.SNAPSHOT_BINDING_MISMATCH);
                return;
            }
            session.lastSnapshotTick = tick;
            session.traceSnapshotDeniedReason = null;
            GhostControlNetworking.sendToPlayer(session.player, snapshot);
            String diagnostic = "mode="+snapshot.movementMode()+" frame="+(snapshot.frameState().isPresent()
                    ? "FRAME:"+snapshot.frameState().get().frameId() : "WORLD");
            boolean snapshotTransition = !diagnostic.equals(session.traceSnapshotSignature);
            session.traceSnapshotSignature = diagnostic;
            session.latestSnapshotDiagnostic = diagnostic+" ack="+session.lastAppliedSequence+" pos="+position;
            if (snapshotTransition) trace(session, "SNAPSHOT", tick, true,
                    "epoch=" + snapshot.epoch() + " mode=" + snapshot.movementMode() + " gameTime=" + tick
                            + " serverTick=" + tick + " ack=" + session.lastAppliedSequence + " pos=" + position
                            + " velocity=" + velocity + " movementBlocked=" + snapshot.movementBlocked()
                            + " frameState=" + (snapshot.frameState().isPresent()
                                    ? "FRAME:" + snapshot.frameState().get().frameId() : "WORLD"));
        } catch (RuntimeException failure) {
            fail(server, session, FailureReason.SNAPSHOT_SEND_ERROR);
        }
    }

    private static boolean authorized(Session session) {
        return session.lifecycle.authorizes(session.player.getUUID(), session.epoch, session.bodyId,
                harness -> harness.id().equals(session.bodyId) && harness.kind() == MobHarnessKind.CHARACTER)
                && session.player.getUUID().equals(session.accountId)
                && session.profileId.equals(session.body.playerCharacterBinding() == null ? null
                : session.body.playerCharacterBinding().profileKey());
    }

    private static boolean live(PlayerCharacterHarnessEntity body, ServerLevel level) {
        return body != null && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && body.level() == level && level.getEntity(body.getUUID()) == body && !body.isPassenger();
    }

    private static Session session(MinecraftServer server, UUID account) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        return sessions == null ? null : sessions.get(account);
    }

    private static java.util.OptionalLong completedSableStep(ServerLevel level) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (!(container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer serverContainer))
            return java.util.OptionalLong.empty();
        return com.juicyslew.moonstation14.ms14.player_body_control.server.SableFrameClock
                .completedStep(serverContainer.trackingSystem().getInterpolationTick());
    }

    private static void trace(Session session, String phase, long tick, boolean important, String fields) {
        try {
            if (session.traceDiagnostics.allow(session.epoch, System.nanoTime(), important))
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("{} SERVER phase={} epoch={} body={} serverTick={} gameTime={} {}",
                        CharacterCadenceDiagnostics.MARKER, phase, session.epoch, session.body.getId(),
                        session.player.level().getServer().getTickCount(), tick, fields);
            else session.traceSamplesSuppressed++;
        } catch (RuntimeException ignored) { }
    }

    private static Boolean observedPhysicalStepConsumed(Mob body, ServerLevel level, long serverTick) {
        try {
            return body instanceof MindControlledMob owner
                    ? owner.moonstation14$physicalStepConsumed(level, serverTick) : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static boolean hasSession(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        return server != null && session(server, player.getUUID()) != null;
    }

    /** Exact committed lifecycle possession; appearance self-service is unavailable during handshake. */
    static boolean ownsCommittedAppearanceSession(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                                   PlayerLifecycleRegistry lifecycle, long generation) {
        if (player == null || body == null || lifecycle == null || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        Session session = session(level.getServer(), player.getUUID());
        return session != null && session.player == player && session.body == body && session.lifecycle == lifecycle
                && session.epoch == generation && session.committed && authorized(session)
                && connectedExact(level.getServer(), player);
    }

    /** Post-durable appearance mutation failure invalidates this exact session and fails closed. */
    static void failAppearanceSession(ServerPlayer player, PlayerCharacterHarnessEntity body, long generation) {
        if (player == null || body == null || !(player.level() instanceof ServerLevel level)) return;
        Session session = session(level.getServer(), player.getUUID());
        if (session != null && session.player == player && session.body == body && session.epoch == generation)
            fail(level.getServer(), session, FailureReason.APPEARANCE_FAILURE);
    }

    /** Durably claim an exact connected corpse before dropping its ACTIVE session. */
    static boolean hasExactDeathSession(PlayerCharacterHarnessEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || body.playerCharacterBinding() == null) return false;
        Session session = session(level.getServer(), body.playerCharacterBinding().accountId());
        return session != null && session.body == body && session.bodyId.value().equals(body.getUUID());
    }

    static boolean claimConfirmedDeath(PlayerCharacterHarnessEntity body, LifecycleServerContext context) {
        if (body == null || context == null || !(body.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, body.playerCharacterBinding() == null
                ? new UUID(0, 0) : body.playerCharacterBinding().accountId());
        if (session == null || session.body != body || !session.bodyId.value().equals(body.getUUID())) return false;
        var binding = body.playerCharacterBinding();
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        boolean exact = connectedDeathDecision(binding != null && identity != null && identity.isBound()
                && ModCharacters.HUMAN_ID.equals(identity.characterId()), binding != null
                && session.accountId.equals(binding.accountId()) && session.profileId.equals(binding.profileKey())
                && session.mindId.value().equals(binding.mindId()) && session.player.getUUID().equals(session.accountId)
                && session.body == body && session.bodyId.value().equals(body.getUUID()),
                session.epoch > 0 && session.lifecycle.profile(session.accountId).map(profile -> profile.active()
                && profile.state() == PlayerLifecycleRegistry.LifecycleState.ACTIVE
                && profile.connectionGeneration() == session.epoch && profile.mindId().equals(session.mindId)
                && profile.bodyId().equals(session.bodyId) && profile.profileId().equals(session.profileId)).orElse(false),
                connectedExact(server, session.player), level.getServer().isSameThread());
        boolean claimed = false;
        if (exact) {
            var appearance = Map.of("appearance", body.appearance().name(), "bodyShape", body.bodyShape().name());
            try {
                claimed = context.claimActiveDeath(session.accountId, session.profileId, session.mindId.value(),
                        session.bodyId.value(), level.dimension().location().toString(),
                        new SavedLifecycleProfile.Location(body.getX(), body.getY(), body.getZ()), appearance,
                        session.epoch, candidate -> candidate.equals(new com.juicyslew.moonstation14.ms14.player_body_control.MobHarness(
                                session.bodyId, MobHarnessKind.CHARACTER)) && body.hasConfirmedDeath()
                                && body.getHealth() <= 0 && !body.isRemoved() && body.level() == level
                                && level.getEntity(body.getUUID()) == body
                                && binding.accountId().equals(session.accountId)
                                && binding.profileKey().equals(session.profileId)
                                && binding.mindId().equals(session.mindId.value()));
            } catch (IOException | RuntimeException failure) {
                claimed = false;
            }
        }
        if (claimed) {
            boolean stopSent = finishDeathClaim(server, session);
            if (stopSent) LifecycleMedicalObserverBridge.beginConnectedCorpse(server, session.player,
                    body, session.epoch, session.accountId, session.profileId, session.mindId, session.bodyId);
            else disconnectAfterDeathClaim(server, session.player,
                    "Your character has died, but ghost entry could not be completed safely. Log out and reconnect to retry, or contact an administrator.");
        }
        else fail(server, session, FailureReason.DEATH_CLAIM_FAILURE);
        return claimed;
    }

    private static boolean finishDeathClaim(MinecraftServer server, Session session) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.accountId) != session) return false;
        sessions.remove(session.accountId);
        if (sessions.isEmpty()) SERVERS.remove(server);
        session.gate.reset();
        session.movementInput.clear();
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
        try {
            if (connectedExact(server, session.player)) {
                GhostControlNetworking.sendToPlayer(session.player, new GhostControlPayloads.Stop(session.epoch));
                return true;
            }
        } catch (RuntimeException | Error failure) {
            // The old epoch is revoked; do not start a new handshake if Stop could not be delivered.
        }
        return false;
    }

    private static void disconnectAfterDeathClaim(MinecraftServer server, ServerPlayer player, String message) {
        if (connectedExact(server, player)) player.connection.disconnect(net.minecraft.network.chat.Component.literal(message));
    }

    private static boolean owns(MinecraftServer server, ServerPlayer player) {
        return player != null && player.level().getServer() == server && connectedExact(server, player)
                && session(server, player.getUUID()) != null && session(server, player.getUUID()).player == player;
    }

    static boolean ownsAny(ServerPlayer player) {
        return player != null && owns(player.level().getServer(), player);
    }

    private static boolean connectedExact(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null && !player.isRemoved() && !(player instanceof FakePlayer)
                && player.level().getServer() == server && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    static boolean readyDecision(long readyEpoch, long sessionEpoch, boolean committed, long elapsedTicks,
                                 long timeoutTicks) {
        // Timeout controls Begin retransmission, not the lifetime of an otherwise exact live handshake.
        // A delayed Ready remains admissible while this session/connection/body is still authoritative.
        return !committed && readyEpoch == sessionEpoch && elapsedTicks >= 0;
    }

    static boolean delayedBeginDecision(boolean exactSession, boolean exactConnectedPlayer,
                                        boolean eligibleBody, boolean alreadySent) {
        return exactSession && exactConnectedPlayer && eligibleBody && !alreadySent;
    }

    static boolean timeoutDecision(boolean beginSent, boolean committed, long elapsedTicks, long timeoutTicks) {
        return beginSent && !committed && elapsedTicks >= timeoutTicks;
    }

    /** Shared timeout transition: only a proven current owner may invoke the existing durable clean-logout path. */
    static boolean timeoutCleanupDecision(boolean exactCurrentOwner, java.util.function.BooleanSupplier cleanLogout,
                                          Runnable failClosed, java.util.function.Consumer<Boolean> disconnect) {
        if (!exactCurrentOwner) {
            failClosed.run();
            return false;
        }
        boolean parked = cleanLogout.getAsBoolean();
        disconnect.accept(parked);
        return true;
    }

    private static final class Session {
        final ServerPlayer player; final PlayerCharacterHarnessEntity body; final PlayerLifecycleRegistry lifecycle;
        final long epoch; long startedTick = -1; final UUID accountId; final String profileId;
        final MindId mindId; final MobHarnessId bodyId; boolean committed, beginSent;
        final GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        final CharacterMovementInputState movementInput = new CharacterMovementInputState();
        long intentTick = Long.MIN_VALUE, lastAppliedSequence, lastSnapshotTick = Long.MIN_VALUE;
        long movementRebaseRevision;
        long lastMovementDiagnosticTick = Long.MIN_VALUE / 2;
        boolean movementSuspended, unsafePoseLogged;
        long lastInputDispatchTick = Long.MIN_VALUE;
        WorldStep worldStep = (body, x, z, jump, sprint, yaw, reference) ->
                CHARACTER_WORLD_STEP.step(body, x, z, jump, sprint, yaw, null, reference);
        boolean worldStepInjected;
        SafePoseObserver safePoseObserver = LifecycleCharacterSessionControl::defaultSafePose;
        boolean safePoseInjected;
        long appliedIntentTick = Long.MIN_VALUE; boolean appliedSneak;
        int intentCount;
        final CharacterCadenceDiagnostics traceDiagnostics = new CharacterCadenceDiagnostics();
        int traceWishX = Integer.MIN_VALUE, traceWishZ = Integer.MIN_VALUE, traceButtons = Integer.MIN_VALUE;
        long traceCallbackTick = Long.MIN_VALUE; int traceCallbacks; String traceLastOutcome;
        long tracePlayerPostTick = Long.MIN_VALUE; int traceSamplesSuppressed; boolean traceHeld;
        String latestSnapshotDiagnostic = "none";
        String traceSnapshotSignature = "";
        boolean traceRateRejected;
        boolean traceOfferRejected;
        String traceSnapshotDeniedReason;
        Session(ServerPlayer player, PlayerCharacterHarnessEntity body, PlayerLifecycleRegistry lifecycle,
                 long epoch) {
            this.player = player; this.body = body; this.lifecycle = lifecycle; this.epoch = epoch;
            this.accountId = player.getUUID();
            var profile = lifecycle.profile(accountId).orElseThrow();
            this.profileId = profile.profileId(); this.mindId = profile.mindId(); this.bodyId = profile.bodyId();
        }
    }

    static boolean committedCameraOwnershipMatches(boolean committedSession,
                                                    boolean exactConnectedPlayerOnSameServer,
                                                    boolean currentMindEpochAndBodyAuthorized,
                                                    boolean cameraIsAuthorizedBody,
                                                    boolean spectator,
                                                    boolean hasPassenger) {
        return committedSession && exactConnectedPlayerOnSameServer && currentMindEpochAndBodyAuthorized
                && cameraIsAuthorizedBody && spectator && !hasPassenger;
    }

    static boolean activeBodySnapshotEligible(boolean exactSession, boolean committedSession,
                                               boolean exactConnectedPlayer, boolean serverThread,
                                               boolean playerAlive, boolean spectator, boolean hasPassenger,
                                               boolean cameraIsExactBody, boolean liveBody) {
        return exactSession && committedSession && exactConnectedPlayer && serverThread && playerAlive
                && spectator && !hasPassenger && cameraIsExactBody && liveBody;
    }

    static boolean activeBodyBindingMatches(PlayerCharacterBinding binding, UUID accountId,
                                            String profileId, MindId mindId) {
        return binding != null && accountId.equals(binding.accountId())
                && profileId.equals(binding.profileKey()) && mindId.value().equals(binding.mindId());
    }

    static boolean connectedDeathDecision(boolean exactBindingAndHuman, boolean exactSession,
                                          boolean currentActiveEpoch, boolean exactConnectedOwner,
                                          boolean serverThread) {
        return exactBindingAndHuman && exactSession && currentActiveEpoch && exactConnectedOwner && serverThread;
    }

    static boolean intentRateAllows(int intentCount) {
        return intentCount > 0 && intentCount <= MAX_INTENTS_PER_TICK;
    }

    static boolean snapshotBoundToSession(GhostControlPayloads.Snapshot snapshot, long epoch, int entityId) {
        return snapshot != null && snapshot.epoch() == epoch && snapshot.harnessEntityId() == entityId
                && snapshot.harnessKind() == MobHarnessKind.CHARACTER
                && entityId >= 0;
    }
}
