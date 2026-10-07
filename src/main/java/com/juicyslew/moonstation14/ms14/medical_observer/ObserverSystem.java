package com.juicyslew.moonstation14.ms14.medical_observer;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.GhostOnMoveComponent;
import com.juicyslew.moonstation14.ms14.character.components.IncorporealComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateChangedEvent;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server authority for observer sessions. The native player and corpse stay in place. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class ObserverSystem {
    private static final ResourceLocation OBSERVER = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "observer");
    private static final Map<UUID, ObserverSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ObserverIntent> INTENTS = new ConcurrentHashMap<>();
    private static long epoch;
    private static final Map<UUID, ObserverPayloads.Snapshot> SENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_EPOCH = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> LAST_BODY = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> STAGING_GHOST_OWNERS = new ConcurrentHashMap<>();

    private ObserverSystem() { }

    private static CharacterData policy(ServerPlayer owner) {
        return PrototypeRuntime.serverCharacters().get(OBSERVER);
    }

    @SubscribeEvent
    public static void stateChanged(MobStateChangedEvent event) {
        if (!(event.body() instanceof ServerPlayer owner)) {
            if (event.current() != MobStateComponent.State.ALIVE && event.current() != MobStateComponent.State.CRITICAL) return;
            for (ObserverSession session : SESSIONS.values()) {
                if (session.mode() != ObserverSession.Mode.CORPSE || !session.corpseBodyId().equals(event.body().getUUID())
                        || session.boundAuthority() == null) continue;
                ServerPlayer boundOwner = event.body().level().getServer() == null ? null
                        : event.body().level().getServer().getPlayerList().getPlayer(session.ownerId());
                if (boundOwner != null) tryRecoverBoundCorpse(boundOwner);
            }
            return;
        }
        if (event.current() == MobStateComponent.State.ALIVE || event.current() == MobStateComponent.State.CRITICAL) {
            ObserverSession session = SESSIONS.get(owner.getUUID());
            if (session != null && session.mode() == ObserverSession.Mode.CORPSE) discardSession(session, owner);
            return;
        }
        if (event.current() != MobStateComponent.State.DEAD || event.previous() == MobStateComponent.State.DEAD) return;
        var state = owner.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        long revision = state == null ? 0 : state.data().revision();
        SESSIONS.put(owner.getUUID(), new ObserverSession(owner.getUUID(), owner.getUUID(), null,
                Math.max(++epoch, revision), ObserverSession.Mode.CORPSE, false));
        refreshCarrierTracking(owner);
        rememberEpoch(owner.getUUID(), SESSIONS.get(owner.getUUID()).epoch());
        MoonStation14.LOGGER.info("[observer] CORPSE session admitted owner={} body={} epoch={} state={} revision={}",
                owner.getUUID(), owner.getUUID(), SESSIONS.get(owner.getUUID()).epoch(), medicalState(owner), revision);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isRemoved()) return;
        MobStateAttachment state = player.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        if (state != null && state.data().state() == MobStateComponent.State.DEAD) {
            long prior = Math.max(state.data().revision(), LAST_EPOCH.getOrDefault(player.getUUID(), 0L));
            long next = Math.max(++epoch, prior + 1);
            epoch = Math.max(epoch, next);
            ObserverSession corpse = new ObserverSession(player.getUUID(), player.getUUID(), null,
                    next, ObserverSession.Mode.CORPSE, false);
            SESSIONS.put(player.getUUID(), corpse);
            refreshCarrierTracking(player);
            rememberEpoch(player.getUUID(), next);
        }
        sendSnapshot(player, true);
    }

    /** Called by the server transport after authenticating its connection owner. */
    public static boolean requestGhost(ServerPlayer owner, long requestedEpoch) {
        if (owner == null) {
            MoonStation14.LOGGER.debug("[observer] ghost request denied owner=null requestedEpoch={} reason=owner-null", requestedEpoch);
            return false;
        }
        ObserverSession session = SESSIONS.get(owner.getUUID());
        if (!(owner.level() instanceof ServerLevel level) || level.getEntity(owner.getUUID()) != owner) {
            MoonStation14.LOGGER.debug("[observer] ghost request denied owner={} requestedEpoch={} sessionMode={} sessionEpoch={} reason=owner-not-exact-world-player",
                    owner.getUUID(), requestedEpoch, session == null ? null : session.mode(), session == null ? -1 : session.epoch());
            return false;
        }
        LivingEntity body = session == null ? owner : resolveBody(owner, session);
        if (body == null) {
            MoonStation14.LOGGER.debug("[observer] ghost request denied owner={} requestedEpoch={} sessionMode={} sessionEpoch={} authorityGeneration={} body={} state=null reason=body-unresolved",
                    owner.getUUID(), requestedEpoch, session.mode(), session.epoch(), session.authorityGeneration(), session.corpseBodyId());
            return false;
        }
        CharacterData resolved = ActiveCharacterPolicy.resolveActor(body).orElse(null);
        GhostOnMoveComponent ghostPolicy = resolved == null ? null : resolved.component(GhostOnMoveComponent.class).orElse(null);
        CharacterData observer = policy(owner);
        ObserverMovementComponent movement = observer == null ? null : observer.component(ObserverMovementComponent.class).orElse(null);
        boolean incorporeal = observer != null && observer.component(IncorporealComponent.class).isPresent();
        if (session == null || session.epoch() != requestedEpoch || session.mode() != ObserverSession.Mode.CORPSE
                || owner.isRemoved()
                || ghostPolicy == null || (ghostPolicy.mustBeDead() && !isDead(body))
                || movement == null || !incorporeal) {
            MoonStation14.LOGGER.debug("[observer] ghost request denied owner={} body={} requestedEpoch={} sessionMode={} sessionEpoch={} authorityGeneration={} state={} ghostPolicy={} mustBeDead={} canReturn={} observerMovement={} incorporeal={} reason={}",
                    owner.getUUID(), body.getUUID(), requestedEpoch, session == null ? null : session.mode(),
                    session == null ? -1 : session.epoch(), session == null ? -1 : session.authorityGeneration(),
                    medicalState(body), ghostPolicy != null, ghostPolicy != null && ghostPolicy.mustBeDead(),
                    ghostPolicy != null && ghostPolicy.canReturn(), movement != null, incorporeal,
                    session == null ? "no-session" : session.epoch() != requestedEpoch ? "epoch-mismatch"
                            : session.mode() != ObserverSession.Mode.CORPSE ? "not-corpse"
                            : owner.isRemoved() ? "owner-removed" : ghostPolicy == null ? "no-ghost-policy"
                            : ghostPolicy.mustBeDead() && !isDead(body) ? "body-not-dead"
                            : movement == null ? "no-observer-movement" : "not-incorporeal");
            return false;
        }
        if (session.boundAuthority() != null) {
            if (body == null || !session.boundAuthority().valid(owner, body, session)) {
                MoonStation14.LOGGER.debug("[observer] ghost request denied owner={} body={} requestedEpoch={} sessionMode={} sessionEpoch={} authorityGeneration={} state={} reason=bound-authority-invalid",
                        owner.getUUID(), body == null ? session.corpseBodyId() : body.getUUID(), requestedEpoch,
                        session.mode(), session.epoch(), session.authorityGeneration(), body == null ? null : medicalState(body));
                return false;
            }
            BoundObserverAuthority.GhostStartResult staged = session.boundAuthority().startGhost(owner, body,
                    session.epoch(), movement);
            if (staged == null || staged.ghost().isRemoved()) {
                MoonStation14.LOGGER.debug("[observer] ghost request failed owner={} body={} requestedEpoch={} sessionEpoch={} authorityGeneration={} state={} reason=bound-stage-failed",
                        owner.getUUID(), body.getUUID(), requestedEpoch, session.epoch(), session.authorityGeneration(), medicalState(body));
                clearBoundGhostAdmission(owner.getUUID()); return false;
            }
            ObserverSession next = new ObserverSession(owner.getUUID(), body.getUUID(), staged.ghost().getUUID(),
                    session.epoch(), ObserverSession.Mode.GHOST,
                    ghostPolicy.canReturn(),
                    session.boundAuthority(), staged.authorityGeneration());
            boolean committed = SESSIONS.replace(owner.getUUID(), session, next);
            finishBoundGhostAdmission(owner.getUUID(), staged.ghost().getUUID());
            boolean admitted = committed && SESSIONS.get(owner.getUUID()) == next;
            if (admitted) refreshCarrierTracking(owner);
            if (admitted) MoonStation14.LOGGER.info("[observer] GHOST admitted owner={} body={} ghost={} epoch={} authorityGeneration={} state={}",
                    owner.getUUID(), body.getUUID(), staged.ghost().getUUID(), session.epoch(), staged.authorityGeneration(), medicalState(body));
            else MoonStation14.LOGGER.debug("[observer] ghost request failed owner={} body={} ghost={} requestedEpoch={} sessionEpoch={} authorityGeneration={} state={} reason=session-commit-race",
                    owner.getUUID(), body.getUUID(), staged.ghost().getUUID(), requestedEpoch, session.epoch(),
                    staged.authorityGeneration(), medicalState(body));
            return admitted;
        }
        if (!session.corpseBodyId().equals(owner.getUUID()) || !isDead(body)) {
            MoonStation14.LOGGER.debug("[observer] ghost request denied owner={} body={} requestedEpoch={} sessionMode={} sessionEpoch={} state={} reason={}",
                    owner.getUUID(), body.getUUID(), requestedEpoch, session.mode(), session.epoch(), medicalState(body),
                    !session.corpseBodyId().equals(owner.getUUID()) ? "unbound-body-not-owner" : "body-not-dead");
            return false;
        }
        GhostMobHarnessEntity ghost = GhostMobHarnessRegistration.getEntityType().create(owner.level());
        if (ghost == null) {
            MoonStation14.LOGGER.debug("[observer] ghost request failed owner={} body={} requestedEpoch={} sessionEpoch={} state={} reason=ghost-construction-failed",
                    owner.getUUID(), body.getUUID(), requestedEpoch, session.epoch(), medicalState(body));
            return false;
        }
        ghost.moveTo(owner.position());
        ObserverSession ghostSession = new ObserverSession(owner.getUUID(), owner.getUUID(), ghost.getUUID(),
                session.epoch(), ObserverSession.Mode.GHOST, ghostPolicy.canReturn());
        SESSIONS.put(owner.getUUID(), ghostSession);
        refreshCarrierTracking(owner);
        // Tracking is evaluated during admission, so establish authorization before publishing the entity.
        if (!owner.level().addFreshEntity(ghost)) {
            SESSIONS.replace(owner.getUUID(), ghostSession, session);
            refreshCarrierTracking(owner);
            MoonStation14.LOGGER.debug("[observer] ghost request failed owner={} body={} requestedEpoch={} sessionEpoch={} state={} reason=ghost-entity-admission-rejected",
                    owner.getUUID(), body.getUUID(), requestedEpoch, session.epoch(), medicalState(body));
            return false;
        }
        MoonStation14.LOGGER.info("[observer] GHOST admitted owner={} body={} ghost={} epoch={} state={}",
                owner.getUUID(), body.getUUID(), ghost.getUUID(), session.epoch(), medicalState(body));
        return true;
    }

    /** True only for ghosts belonging to an active medical GHOST session. */
    public static boolean isMedicalGhost(Entity entity) {
        if (entity == null) return false;
        UUID stagedOwner = STAGING_GHOST_OWNERS.get(entity.getUUID());
        if (stagedOwner != null && entity instanceof GhostMobHarnessEntity) return true;
        for (ObserverSession session : SESSIONS.values()) {
            if (session.mode() == ObserverSession.Mode.GHOST && entity.getUUID().equals(session.ghostId())) return true;
        }
        return false;
    }

    /** Medical ghosts are visible only to their owner and active ghost observers. */
    public static boolean mayTrackMedicalGhost(ServerPlayer viewer, Entity ghost) {
        if (!isMedicalGhost(ghost)) return true;
        UUID stagedOwner = ghost instanceof GhostMobHarnessEntity ? STAGING_GHOST_OWNERS.get(ghost.getUUID()) : null;
        if (stagedOwner != null && stagedOwner.equals(viewer.getUUID())) return true;
        ObserverSession ownSession = query(viewer);
        if (ownSession != null && ownSession.mode() == ObserverSession.Mode.GHOST) return true;
        for (ObserverSession session : SESSIONS.values()) {
            if (session.mode() == ObserverSession.Mode.GHOST && ghost.getUUID().equals(session.ghostId())
                    && session.ownerId().equals(viewer.getUUID())) return true;
        }
        return false;
    }

    public static ObserverSession query(ServerPlayer owner) { return SESSIONS.get(owner.getUUID()); }

    /** Native player entities are carriers for observers, not the separate corpse body. */
    public static boolean mustHideObserverCarrier(ServerPlayer viewer, ServerPlayer carrier) {
        if (viewer == null || carrier == null || viewer.getUUID().equals(carrier.getUUID())) return false;
        return mustHideObserverCarrier(viewer.getUUID(), carrier.getUUID(), SESSIONS.get(carrier.getUUID()));
    }

    /** Pure policy shared with tests; a null session represents END/no active observer session. */
    public static boolean mustHideObserverCarrier(UUID viewerId, UUID carrierId, ObserverSession session) {
        if (viewerId == null || carrierId == null || viewerId.equals(carrierId)) return false;
        return session != null && (session.mode() == ObserverSession.Mode.CORPSE || session.mode() == ObserverSession.Mode.GHOST)
                && session.ownerId().equals(carrierId) && !session.corpseBodyId().equals(carrierId);
    }

    /** Re-evaluate the owner's tracked entity immediately after a visibility policy transition. */
    private static void refreshCarrierTracking(ServerPlayer owner) {
        if (owner != null && owner.level() instanceof ServerLevel && !owner.isRemoved())
            owner.serverLevel().getChunkSource().chunkMap.move(owner);
    }

    /** Temporary visibility authorization immediately before trusted bound-ghost world admission. */
    public static boolean beforeBoundGhostAdmission(ServerPlayer owner, Entity ghost) {
        if (owner == null || !(ghost instanceof GhostMobHarnessEntity)
                || !(owner.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || level.getEntity(owner.getUUID()) != owner || ghost.level() != owner.level()) return false;
        ObserverSession session = SESSIONS.get(owner.getUUID());
        LivingEntity body = session == null ? null : resolveBody(owner, session);
        if (session == null || session.mode() != ObserverSession.Mode.CORPSE || session.boundAuthority() == null
                || body == null || !session.boundAuthority().valid(owner, body, session)) return false;
        STAGING_GHOST_OWNERS.put(ghost.getUUID(), owner.getUUID());
        return true;
    }

    public static void finishBoundGhostAdmission(UUID owner, UUID ghostId) {
        if (owner != null && ghostId != null) STAGING_GHOST_OWNERS.remove(ghostId, owner);
    }
    public static void clearBoundGhostAdmission(UUID owner) {
        if (owner != null) STAGING_GHOST_OWNERS.entrySet().removeIf(entry -> entry.getValue().equals(owner));
    }

    /** Trusted lifecycle integration point; never infer ownership from proximity or death events. */
    public static boolean beginBoundCorpse(ServerPlayer owner, LivingEntity body, long corpseEpoch,
                                           BoundObserverAuthority authority) {
        String rejection = null;
        ServerLevel level = owner != null && owner.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        if (owner == null) rejection = "owner-null";
        else if (body == null) rejection = "body-null";
        else if (authority == null) rejection = "authority-null";
        else if (corpseEpoch <= 0) rejection = "invalid-epoch";
        else if (owner.isRemoved()) rejection = "owner-removed";
        else if (level == null || level.getEntity(owner.getUUID()) != owner) rejection = "owner-not-exact-world-entity";
        else if (body.level() != owner.level() || body.isRemoved() || level.getEntity(body.getUUID()) != body)
            rejection = "body-not-exact-world-entity";
        else if (SESSIONS.containsKey(owner.getUUID())) rejection = "owner-already-has-session";
        else if (!isDead(body)) rejection = "body-not-dead";
        if (rejection != null) {
            MoonStation14.LOGGER.info("[observer] bound CORPSE admission denied owner={} body={} epoch={} authorityGeneration={} state={} reason={}",
                    owner == null ? null : owner.getUUID(), body == null ? null : body.getUUID(), corpseEpoch,
                    corpseEpoch, medicalState(body), rejection);
            return false;
        }
        ObserverSession candidate = new ObserverSession(owner.getUUID(), body.getUUID(), null, corpseEpoch,
                ObserverSession.Mode.CORPSE, false, authority, corpseEpoch);
        if (!authority.valid(owner, body, candidate)) {
            MoonStation14.LOGGER.info("[observer] bound CORPSE admission denied owner={} body={} epoch={} authorityGeneration={} state={} reason=authority-invalid",
                    owner.getUUID(), body.getUUID(), corpseEpoch, corpseEpoch, medicalState(body));
            return false;
        }
        if (SESSIONS.putIfAbsent(owner.getUUID(), candidate) != null) {
            MoonStation14.LOGGER.info("[observer] bound CORPSE admission denied owner={} body={} epoch={} authorityGeneration={} state={} reason=session-race",
                    owner.getUUID(), body.getUUID(), corpseEpoch, corpseEpoch, medicalState(body));
            return false;
        }
        refreshCarrierTracking(owner);
        LAST_BODY.put(owner.getUUID(), body.getUUID());
        rememberEpoch(owner.getUUID(), corpseEpoch);
        MoonStation14.LOGGER.info("[observer] bound CORPSE admitted owner={} body={} epoch={} authorityGeneration={} state={}",
                owner.getUUID(), body.getUUID(), corpseEpoch, corpseEpoch, medicalState(body));
        return true;
    }

    /** Recovery path shared by authenticated state events and the server tick fallback. */
    public static boolean tryRecoverBoundCorpse(ServerPlayer owner) {
        if (owner == null || !(owner.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || level.getEntity(owner.getUUID()) != owner) return false;
        ObserverSession session = SESSIONS.get(owner.getUUID());
        if (session == null || session.mode() != ObserverSession.Mode.CORPSE || session.boundAuthority() == null)
            return false;
        LivingEntity body = resolveBody(owner, session);
        if (body == null || !isAliveOrCritical(body) || !session.boundAuthority().valid(owner, body, session)
                || !session.boundAuthority().returnToBody(owner, body, session)) return false;
        discardSession(session, owner);
        return true;
    }

    private static LivingEntity resolveBody(ServerPlayer owner, ObserverSession session) {
        if (!(owner.level() instanceof ServerLevel level)) return null;
        Entity body = level.getEntity(session.corpseBodyId());
        return body instanceof LivingEntity living && !living.isRemoved() ? living : null;
    }

    private static boolean isDead(LivingEntity body) {
        MobStateAttachment state = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state != null && state.data().state() == MobStateComponent.State.DEAD;
    }

    private static MobStateComponent.State medicalState(LivingEntity body) {
        MobStateAttachment state = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state == null ? null : state.data().state();
    }

    public static void sendSnapshot(ServerPlayer player) { sendSnapshot(player, false); }

    public static void sendSnapshot(ServerPlayer player, boolean force) {
        ObserverSession s = query(player);
        ObserverPayloads.Snapshot snapshot;
        if (s == null) {
            UUID bodyId = LAST_BODY.getOrDefault(player.getUUID(), player.getUUID());
            Entity body = player.level() instanceof ServerLevel level ? level.getEntity(bodyId) : null;
            snapshot = new ObserverPayloads.Snapshot(ObserverPayloads.Mode.END,
                    LAST_EPOCH.getOrDefault(player.getUUID(), 0L), bodyId,
                    body == null ? player.getId() : body.getId(), null, -1, false);
        }
        else {
            rememberEpoch(player.getUUID(), s.epoch());
            Entity ghost = s.ghostId() == null ? null : ((ServerLevel)player.level()).getEntity(s.ghostId());
            Entity body = ((ServerLevel) player.level()).getEntity(s.corpseBodyId());
            LivingEntity medicalBody = s.boundAuthority() == null ? player : resolveBody(player, s);
            MobStateAttachment state = medicalBody == null ? null : medicalBody.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
            boolean bodyRevived = state != null && (state.data().state() == MobStateComponent.State.ALIVE || state.data().state() == MobStateComponent.State.CRITICAL);
            boolean authorityValid = s.boundAuthority() == null || medicalBody != null && s.boundAuthority().valid(player, medicalBody, s);
            boolean available = s.mode() == ObserverSession.Mode.GHOST && bodyRevived && authorityValid && s.returnAvailable();
            snapshot = new ObserverPayloads.Snapshot(s.mode() == ObserverSession.Mode.GHOST ? ObserverPayloads.Mode.GHOST : ObserverPayloads.Mode.CORPSE, s.epoch(), s.corpseBodyId(), body == null ? player.getId() : body.getId(), s.ghostId(), ghost == null ? -1 : ghost.getId(), available);
        }
        if (force || !snapshot.equals(SENT.get(player.getUUID()))) {
            ObserverNetworking.send(snapshot, player);
            SENT.put(player.getUUID(), snapshot);
        }
    }

    /** Accept bounded semantic input only for the authenticated native session owner. */
    public static boolean submitIntent(ServerPlayer owner, long requestedEpoch, ObserverIntent intent) {
        ObserverSession session = SESSIONS.get(owner.getUUID());
        if (session == null || session.epoch() != requestedEpoch || session.mode() != ObserverSession.Mode.GHOST
                || session.ghostId() == null || intent == null) return false;
        if (session.boundAuthority() != null) {
            LivingEntity body = resolveBody(owner, session);
            if (body == null || !session.boundAuthority().valid(owner, body, session)) return false;
            Entity ghost = owner.level() instanceof ServerLevel level ? level.getEntity(session.ghostId()) : null;
            if (com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl
                    .ownsPreparedGhostTransport(owner, ghost)) return false;
        } else if (!session.corpseBodyId().equals(owner.getUUID()) || !isDead(owner)) return false;
        INTENTS.put(owner.getUUID(), intent);
        return true;
    }

    public static boolean requestReturn(ServerPlayer owner, long requestedEpoch) {
        ObserverSession session = SESSIONS.get(owner.getUUID());
        if (session == null || session.epoch() != requestedEpoch || session.mode() != ObserverSession.Mode.GHOST
                || !session.returnAvailable()
                || owner.isRemoved()) return false;
        if (session.boundAuthority() != null) {
            LivingEntity body = resolveBody(owner, session);
            if (body == null || !session.boundAuthority().valid(owner, body, session)
                    || !session.boundAuthority().returnToBody(owner, body, session)) return false;
            discardSession(session, owner);
            return true;
        }
        if (!session.corpseBodyId().equals(owner.getUUID())) return false;
        var saved = owner.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        if (saved == null || (saved.data().state() != MobStateComponent.State.ALIVE
                && saved.data().state() != MobStateComponent.State.CRITICAL)) return false;
        discardSession(session, owner);
        return true;
    }

    private static boolean isDead(ServerPlayer player) {
        MobStateAttachment state = player.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state != null && state.data().state() == MobStateComponent.State.DEAD;
    }

    private static void rememberEpoch(UUID owner, long value) {
        LAST_EPOCH.put(owner, value);
        if (LAST_EPOCH.size() > 4096) {
            UUID oldest = LAST_EPOCH.keySet().stream().findAny().orElse(null);
            if (oldest != null && !oldest.equals(owner)) LAST_EPOCH.remove(oldest);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            sendSnapshot(player);
            ObserverTracking.updateChunkCenter(player);
        }
        for (var entry : SESSIONS.entrySet()) {
            ObserverSession session = entry.getValue();
            if (session.mode() == ObserverSession.Mode.CORPSE && session.boundAuthority() != null) {
                ServerPlayer boundOwner = event.getServer().getPlayerList().getPlayer(entry.getKey());
                LivingEntity body = boundOwner == null ? null : resolveBody(boundOwner, session);
                if (body == null || boundOwner == null || !session.boundAuthority().valid(boundOwner, body, session)) {
                    discardSession(session, boundOwner);
                } else if (isAliveOrCritical(body)) tryRecoverBoundCorpse(boundOwner);
                continue;
            }
            if (session.mode() != ObserverSession.Mode.GHOST) continue;
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (owner == null) { discardSession(session, null); continue; }
            Entity found = ((ServerLevel) owner.level()).getEntity(session.ghostId());
            if (session.boundAuthority() != null) {
                LivingEntity body = resolveBody(owner, session);
                boolean valid = body != null && session.boundAuthority().valid(owner, body, session);
                if (!valid) {
                    MoonStation14.LOGGER.warn("[observer] GHOST discarded owner={} body={} ghost={} epoch={} authorityGeneration={} reason={} bodyState={} ghostFound={} ghostType={}",
                            session.ownerId(), session.corpseBodyId(), session.ghostId(), session.epoch(),
                            session.authorityGeneration(), body == null ? "body-unresolved" : "bound-authority-invalid",
                            medicalState(body), found != null, found == null ? null : found.getType());
                    discardSession(session, owner); continue;
                }
            }
            if (!(found instanceof GhostMobHarnessEntity ghost)) {
                MoonStation14.LOGGER.warn("[observer] GHOST discarded owner={} body={} ghost={} epoch={} authorityGeneration={} reason=ghost-entity-missing-or-wrong-type found={} type={}",
                        session.ownerId(), session.corpseBodyId(), session.ghostId(), session.epoch(),
                        session.authorityGeneration(), found != null, found == null ? null : found.getType());
                discardSession(session, owner); continue;
            }
            if (!useLegacyMovement(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl
                    .ownsPreparedGhostTransport(owner, ghost))) continue;
            CharacterData character = policy(owner);
            ObserverMovementComponent movement = character == null ? null : character.component(ObserverMovementComponent.class).orElse(null);
            if (movement == null || character.component(IncorporealComponent.class).isEmpty()) continue;
            ObserverIntent intent = INTENTS.remove(owner.getUUID()); // A missing fresh wish stops movement.
            ghost.setDeltaMovement(Vec3.ZERO);
            if (intent == null) continue;
            double speed = (intent.sprint() ? movement.sprintSpeed() : movement.walkSpeed()) / 20.0;
            double yaw = Math.toRadians(intent.yaw());
            double strafe = intent.strafe() / 1000.0, forward = intent.forward() / 1000.0, vertical = intent.vertical();
            double magnitude = Math.sqrt(strafe * strafe + forward * forward + vertical * vertical);
            if (magnitude > 1) { strafe /= magnitude; forward /= magnitude; vertical /= magnitude; }
            double x = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * speed;
            double z = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * speed;
            double y = vertical * speed;
            ObserverGhostLook.apply(ghost, intent.yaw(), ghost.getXRot());
            ServerLevel level = (ServerLevel) ghost.level();
            if (level.getChunkSource().getChunkNow((int) Math.floor(ghost.getX() + x) >> 4,
                    (int) Math.floor(ghost.getZ() + z) >> 4) != null) ghost.move(MoverType.SELF, new Vec3(x, y, z));
            ghost.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Production movement-route decision, factored so lifecycle transport ownership cannot be tested as a fixture-only convention. */
    static boolean useLegacyMovement(boolean lifecycleTransportOwnsGhost) {
        return !lifecycleTransportOwnsGhost;
    }

    private static boolean isAliveOrCritical(LivingEntity body) {
        MobStateAttachment state = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state != null && (state.data().state() == MobStateComponent.State.ALIVE
                || state.data().state() == MobStateComponent.State.CRITICAL);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            discardSession(SESSIONS.get(player.getUUID()), player);
            SENT.remove(player.getUUID()); LAST_EPOCH.remove(player.getUUID());
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (ObserverSession session : SESSIONS.values()) {
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(session.ownerId());
            if (owner != null) discardSession(session, owner);
            else {
                if (session.ghostId() != null) for (ServerLevel level : event.getServer().getAllLevels()) {
                    Entity ghost = level.getEntity(session.ghostId());
                    if (ghost != null) { ghost.discard(); break; }
                }
                discardSession(session, null);
            }
        }
        SESSIONS.clear(); INTENTS.clear();
        STAGING_GHOST_OWNERS.clear();
    }

    private static void discardSession(ObserverSession session, ServerPlayer owner) {
        if (session == null) return;
        if (owner != null && session.ghostId() != null && owner.level() instanceof ServerLevel level) {
            Entity ghost = level.getEntity(session.ghostId()); if (ghost != null) ghost.discard();
        }
        SESSIONS.remove(session.ownerId(), session); INTENTS.remove(session.ownerId());
        refreshCarrierTracking(owner);
        clearBoundGhostAdmission(session.ownerId());
        rememberEpoch(session.ownerId(), session.epoch());
    }
}
