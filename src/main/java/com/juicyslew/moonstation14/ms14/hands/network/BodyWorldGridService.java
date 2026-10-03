package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.BodyWorldStorageView;
import com.juicyslew.moonstation14.ms14.hands.live.BodyWorldGridTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread world-host sessions. A nonce never substitutes for current authority or reach. */
public final class BodyWorldGridService {
    private BodyWorldGridService() { }
    private static final long LIFETIME_TICKS = 100;
    static final int MAX_SESSIONS = 3;

    static long renewedExpiry(long now) { return now + LIFETIME_TICKS; }
    static boolean expired(long now, long expiresAt) { return now >= expiresAt; }
    static boolean actionLeaseValid(long now, long expiresAt) { return !expired(now, expiresAt); }
    static BodyWorldGridSnapshot.Reason queryEvidenceDenial(boolean stableEvidence, boolean reachable) {
        if (!stableEvidence) return BodyWorldGridSnapshot.Reason.NOT_READY;
        return reachable ? null : BodyWorldGridSnapshot.Reason.UNAVAILABLE;
    }

    /** Server-local admission ledger: nonce is unique per open, never a target-derived capability. */
    static final class SlotLedger {
        private final Map<UUID, UUID> targets = new LinkedHashMap<>();
        boolean available(UUID target) {
            return targets.size() < MAX_SESSIONS && !targets.containsValue(target);
        }
        boolean add(UUID nonce, UUID target) {
            if (!available(target) || targets.containsKey(nonce)) return false;
            targets.put(nonce, target);
            return true;
        }
        boolean close(UUID nonce, UUID target) {
            return targets.remove(nonce, target);
        }
        void revoke(UUID nonce) { targets.remove(nonce); }
        void clear() { targets.clear(); }
    }

    static final class Gate {
        private final LinkedHashMap<Long, Long> sequences = new LinkedHashMap<>();
        private long tick = Long.MIN_VALUE;
        private int queries;

        BodyWorldGridSnapshot.Reason reject(long epoch, long sequence, long currentTick) {
            if (tick != currentTick) { tick = currentTick; queries = 0; }
            if (++queries > 4) return BodyWorldGridSnapshot.Reason.RATE_LIMITED;
            if (sequence <= sequences.getOrDefault(epoch, 0L)) return BodyWorldGridSnapshot.Reason.REPLAY;
            sequences.put(epoch, sequence);
            while (sequences.size() > 8) sequences.remove(sequences.keySet().iterator().next());
            return null;
        }

        boolean admit(long epoch, long sequence, long currentTick) {
            return reject(epoch, sequence, currentTick) == null;
        }
    }

    /** Action admission is independent from query traffic and remains bound to the player identity. */
    static final class ActionGate {
        private final LinkedHashMap<Long, Long> sequences = new LinkedHashMap<>();
        private long tick = Long.MIN_VALUE;
        private int count;

        BodyWorldGridActionResult.Reason reject(long epoch, long sequence, long currentTick) {
            if (tick != currentTick) { tick = currentTick; count = 0; }
            if (++count > 4) return BodyWorldGridActionResult.Reason.RATE_LIMITED;
            if (sequence <= sequences.getOrDefault(epoch, 0L)) return BodyWorldGridActionResult.Reason.REPLAY;
            sequences.put(epoch, sequence);
            while (sequences.size() > 8) sequences.remove(sequences.keySet().iterator().next());
            return null;
        }
    }

    private record Session(ServerPlayer actor, HandActorAuthority.Snapshot authority, ServerLevel level,
                           ItemEntity entity, UUID target, ItemStack reference, ItemStack copy,
                           long expiresAt, long hostRevision) {
        Session {
            copy = copy.copy();
        }
    }

    private static final class ActorState {
        final Gate gate = new Gate();
        final ActionGate actionGate = new ActionGate();
        final Map<UUID, Session> sessions = new LinkedHashMap<>();
        final SlotLedger slots = new SlotLedger();
        HandActorAuthority.Snapshot authority;
        PostDrop postDrop;

        void bind(HandActorAuthority.Snapshot current) {
            if (authority != null && (current == null || authority.body() != current.body()
                    || authority.epoch() != current.epoch() || authority.source() != current.source()
                    || !authority.mindId().equals(current.mindId())
                    || !authority.harnessId().equals(current.harnessId())
                    || !authority.handIds().equals(current.handIds()))) {
                sessions.clear();
                slots.clear();
                postDrop = null;
            }
            authority = current;
        }

        boolean available(UUID target) {
            return slots.available(target);
        }
    }

    record PostDrop(ServerPlayer actor, HandActorAuthority.Snapshot authority, ServerLevel level, UUID target,
                            ItemStack reference, ItemStack copy, long revision, long expiresAt) {
        PostDrop { copy = copy.copy(); }
    }

    /** Called only by the authenticated hand-transfer server path after a committed DROP. */
    static void authorizeTransferredOpen(ServerPlayer actor, HandActorAuthority.Snapshot authority, UUID target) {
        ActorState state = ACTORS.computeIfAbsent(actor, ignored -> new ActorState());
        state.bind(authority);
        if (target == null || !HandActorAuthority.revalidate(actor, authority)
                || !BodyHandStateService.hasIssued(actor, authority)
                || !(authority.body().level() instanceof ServerLevel level)) return;
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body())) return;
        long revision = hands.revision();
        if (revision == Long.MAX_VALUE - 1) return;
        if (!(level.getEntity(target) instanceof ItemEntity entity) || entity.level() != level
                || entity.isRemoved() || !entity.isAddedToLevel() || level.getEntity(target) != entity
                || !target.equals(entity.getUUID()) || entity.getTarget() != null) return;
        ItemStack reference = entity.getItem(), copy = reference.copy();
        if (!storageHost(copy) || GridContents.read(copy).isEmpty() || entity.getItem() != reference || !ItemStack.matches(copy, reference)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || hands.revision() != revision) return;
        state.postDrop = new PostDrop(actor, authority, level, target, reference, copy, revision, renewedExpiry(actor.getServer().getTickCount()));
    }

    private static final Map<ServerPlayer, ActorState> ACTORS = new IdentityHashMap<>();

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ACTORS.remove(player);
    }

    static BodyWorldGridSnapshot query(ServerPlayer actor, BodyWorldGridQuery q) {
        ActorState state = ACTORS.computeIfAbsent(actor, ignored -> new ActorState());
        var resolved = HandActorAuthority.resolve(actor);
        state.bind(resolved.orElse(null));
        if (resolved.isEmpty()) return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NO_AUTHORITY);
        var authority = resolved.orElseThrow();
        if (authority.epoch() != q.epoch()) return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.STALE);
        var denial = state.gate.reject(q.epoch(), q.sequence(), actor.getServer().getTickCount());
        if (denial != null) return BodyWorldGridSnapshot.rejected(q, denial);
        long now = actor.getServer().getTickCount();
        if (q.session() == null) return open(actor, q, state, authority, now);
        Session session = state.sessions.get(q.session());
        if (session == null) return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.STALE);
        if (session.actor() != actor || session.authority().body() != authority.body()
                || session.authority().epoch() != q.epoch() || !session.target().equals(q.target())
                || !sessionBindingValid(actor, authority, session)) {
            revoke(state, q.session());
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.STALE);
        }
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body())) {
            revoke(state, q.session());
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        }
        long revision = hands.revision();
        ItemStack probedReference = session.entity().getItem();
        ItemStack probedCopy = probedReference.copy();
        if (session.level().getEntity(session.target()) != session.entity() || session.entity().isRemoved()
                || !session.entity().isAddedToLevel() || session.entity().level() != session.level()) {
            revoke(state, q.session());
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.UNAVAILABLE);
        }
        // Refresh projects the current hand revision; only the initial aimed open requires the claimed revision.
        boolean reachable;
        try {
            reachable = BodyItemTargetProbe.reachableStorageHost(session.level(), authority.body(), session.entity(), BodyItemTargetProbe.MAX_REACH)
                    && session.entity().getItem() == probedReference && ItemStack.matches(probedCopy, probedReference)
                    && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                    && hands.revision() == revision;
        } catch (RuntimeException callbackFailure) {
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        }
        if (!reachable) {
            boolean stableEvidence = session.entity().getItem() == probedReference && ItemStack.matches(probedCopy, probedReference)
                    && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                    && hands.revision() == revision;
            var evidenceDenial = queryEvidenceDenial(stableEvidence, false);
            if (evidenceDenial == BodyWorldGridSnapshot.Reason.NOT_READY)
                return BodyWorldGridSnapshot.rejected(q, evidenceDenial);
            revoke(state, q.session());
            return BodyWorldGridSnapshot.rejected(q, evidenceDenial);
        }
        try {
            ItemStack reference = probedReference, copy = probedCopy;
            var contents = storageHost(copy) && sameStorageHost(session.copy(), copy) ? GridContents.read(copy) : java.util.Optional.<GridContents>empty();
            if (contents.isEmpty()) {
                revoke(state, q.session());
                return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.UNAVAILABLE);
            }
            boolean changedEvidence = reference != session.reference() || !ItemStack.matches(copy, session.copy());
            if (changedEvidence && session.hostRevision() == Long.MAX_VALUE) {
                revoke(state, q.session());
                return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.UNAVAILABLE);
            }
            long hostRevision = changedEvidence ? session.hostRevision() + 1 : session.hostRevision();
            var summary = snapshot(q, actor, authority.body(), session.target(), copy, contents.orElseThrow().entries(),
                    revision, q.session(), hostRevision);
            if (!sessionBindingValid(actor, authority, session) || session.entity().getItem() != reference
                    || !ItemStack.matches(copy, reference) || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || hands.revision() != revision || state.sessions.get(q.session()) != session)
                throw new IllegalArgumentException("Changed during projection");
            state.sessions.put(q.session(), new Session(session.actor(), session.authority(), session.level(),
                    session.entity(), session.target(), reference, copy, renewedExpiry(now), hostRevision));
            return summary;
        } catch (RuntimeException invalid) {
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        }
    }

    private static BodyWorldGridSnapshot open(ServerPlayer actor, BodyWorldGridQuery q, ActorState state,
                                               HandActorAuthority.Snapshot authority, long now) {
        if (!state.available(q.target())) return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.LIMIT);
        if (!BodyHandStateService.hasIssued(actor, authority))
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        PostDrop grant = state.postDrop;
        boolean postDrop = grant != null && grant.target().equals(q.target())
                && grantMatches(grant, actor, authority, levelOrNull(authority), q.target(), q.handRevision(), now);
        if (grant != null && (expired(now, grant.expiresAt()) || grant.authority().body() != authority.body()
                || grant.authority().epoch() != authority.epoch()
                || grant.target().equals(q.target()) && !postDrop)) state.postDrop = null;
        if (!(authority.body().level() instanceof ServerLevel level)
                || !(level.getEntity(q.target()) instanceof ItemEntity entity))
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        ItemStack reference = entity.getItem(), copy = reference.copy();
        if (postDrop) {
            LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !hands.compatible(authority.body()) || hands.revision() != q.handRevision()) {
                state.postDrop = null;
                return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.STALE);
            }
        }
        var bodyPosition = authority.body().position();
        var bodyEye = authority.body().getEyePosition();
        java.util.Optional<BodyWorldStorageView.Summary> observed;
        try {
            if (postDrop) {
                boolean allowed = grantMatches(grant, actor, authority, level, q.target(), q.handRevision(), now)
                        && grant.reference() == reference && ItemStack.matches(grant.copy(), reference) && storageHost(copy)
                        && grant.level() == level && BodyItemTargetProbe.reachableStorageHost(level, authority.body(), entity,
                        BodyItemTargetProbe.MAX_REACH);
                if (!allowed) state.postDrop = null;
                observed = allowed ? GridContents.read(copy).map(contents -> new BodyWorldStorageView.Summary(
                        q.target(), BuiltInRegistries.ITEM.getKey(copy.getItem()), copy.is(com.juicyslew.moonstation14.item.ModItems.BAG) ? "bag" : "belt",
                        copy.is(com.juicyslew.moonstation14.item.ModItems.BAG) ? 7 : 8,
                        copy.is(com.juicyslew.moonstation14.item.ModItems.BAG) ? 4 : 2,
                        contents.entries(), q.epoch(), q.handRevision())) : java.util.Optional.empty();
            } else observed = BodyWorldStorageView.inspect(actor, q.target(), q.epoch(), q.handRevision());
        } catch (RuntimeException callbackFailure) {
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        }
        if (observed.isEmpty() || !HandActorAuthority.revalidate(actor, authority)
                || !BodyHandStateService.hasIssued(actor, authority) || entity.getItem() != reference
                || !ItemStack.matches(copy, reference) || entity.level() != level || entity.isRemoved()
                || level.getEntity(q.target()) != entity || !bodyPosition.equals(authority.body().position())
                || !bodyEye.equals(authority.body().getEyePosition()))
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        UUID nonce = UUID.randomUUID();
        try {
            var view = observed.orElseThrow();
            var result = snapshot(q, actor, authority.body(), q.target(), copy, view.entries(),
                    q.handRevision(), nonce, 0);
            if (!HandActorAuthority.revalidate(actor, authority) || entity.getItem() != reference
                    || !ItemStack.matches(copy, reference) || !BodyHandStateService.hasIssued(actor, authority)
                    || !bodyPosition.equals(authority.body().position()) || !bodyEye.equals(authority.body().getEyePosition()))
                return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.STALE);
            if (!state.slots.add(nonce, q.target())) return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.LIMIT);
            if (postDrop) state.postDrop = null;
            state.sessions.put(nonce, new Session(actor, authority, level, entity, q.target(), reference, copy,
                    now + LIFETIME_TICKS, 0));
            return result;
        } catch (RuntimeException invalid) {
            return BodyWorldGridSnapshot.rejected(q, BodyWorldGridSnapshot.Reason.NOT_READY);
        }
    }

    private static ServerLevel levelOrNull(HandActorAuthority.Snapshot authority) {
        return authority.body().level() instanceof ServerLevel level ? level : null;
    }

    static boolean grantMatches(PostDrop grant, ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                ServerLevel level, UUID target, long revision, long now) {
        return grant != null && grantBindingMatches(grant.actor() == actor,
                grant.authority().body() == authority.body(), grant.authority().epoch() == authority.epoch(),
                grant.level() == level, grant.target().equals(target), grant.revision() == revision,
                true, true, true, now, grant.expiresAt())
                && grant.reference() != null && ItemStack.matches(grant.copy(), grant.reference()) && storageHost(grant.copy());
    }

    static boolean grantBindingMatches(boolean actor, boolean body, boolean epoch, boolean level,
                                       boolean target, boolean revision, boolean currentReference,
                                       boolean copyStable, boolean reachable, long now, long expiresAt) {
        return actor && body && epoch && level && target && revision && currentReference
                && copyStable && reachable && !expired(now, expiresAt);
    }

    private static boolean storageHost(ItemStack stack) {
        return stack.is(com.juicyslew.moonstation14.item.ModItems.BAG)
                || stack.is(com.juicyslew.moonstation14.item.ModItems.BELT);
    }

    private static boolean sameStorageHost(ItemStack original, ItemStack current) {
        return original.is(com.juicyslew.moonstation14.item.ModItems.BAG) == current.is(com.juicyslew.moonstation14.item.ModItems.BAG)
                && original.is(com.juicyslew.moonstation14.item.ModItems.BELT) == current.is(com.juicyslew.moonstation14.item.ModItems.BELT);
    }

    private static void revoke(ActorState state, UUID nonce) {
        state.sessions.remove(nonce);
        state.slots.revoke(nonce);
    }

    static BodyWorldGridActionResult action(ServerPlayer actor, BodyWorldGridAction action) {
        ActorState state = ACTORS.computeIfAbsent(actor, ignored -> new ActorState());
        var resolved = HandActorAuthority.resolve(actor);
        state.bind(resolved.orElse(null));
        if (resolved.isEmpty()) return result(action, 0, BodyWorldGridActionResult.Reason.NO_AUTHORITY);
        var authority = resolved.orElseThrow();
        if (authority.epoch() != action.epoch()) return result(action, 0, BodyWorldGridActionResult.Reason.WRONG_EPOCH);
        var admission = state.actionGate.reject(action.epoch(), action.sequence(), actor.getServer().getTickCount());
        if (admission != null) return result(action, 0, admission);
        Session session = state.sessions.get(action.session());
        if (session == null || session.actor() != actor || session.authority().body() != authority.body()
                || session.authority().epoch() != authority.epoch() || !session.target().equals(action.target()))
            return result(action, 0, BodyWorldGridActionResult.Reason.STALE);
        if (!actionLeaseValid(actor.getServer().getTickCount(), session.expiresAt()))
            return result(action, session.hostRevision(), BodyWorldGridActionResult.Reason.STALE);
        if (!valid(actor, authority, session)) {
            revoke(state, action.session());
            return result(action, 0, BodyWorldGridActionResult.Reason.STALE);
        }
        long revision = session.hostRevision();
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (revision == Long.MAX_VALUE || revision != action.hostRevision() || hands == null || !hands.compatible(authority.body())
                || hands.revision() != action.handRevision() || !BodyHandStateService.hasIssued(actor, authority)
                || action.hand() != null && !authority.handIds().contains(action.hand()))
            return result(action, revision, BodyWorldGridActionResult.Reason.STALE);
        boolean reachable;
        try {
            reachable = BodyItemTargetProbe.reachableStorageHost(session.level(), authority.body(), session.entity(),
                    BodyItemTargetProbe.MAX_REACH) && valid(actor, authority, session)
                    && state.sessions.get(action.session()) == session
                    && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                    && hands.revision() == action.handRevision();
        } catch (RuntimeException callbackFailure) { reachable = false; }
        if (!reachable) {
            revoke(state, action.session());
            return result(action, 0, BodyWorldGridActionResult.Reason.STALE);
        }
        BodyWorldGridTransfer.Outcome outcome;
        try {
            outcome = BodyWorldGridTransfer.applyAuthorized(actor, authority, session.entity(), session.reference(),
                    session.copy(), action);
        } catch (RuntimeException callbackFailure) {
            outcome = BodyWorldGridTransfer.Outcome.RECOVERY_REQUIRED;
        }
        if (outcome == BodyWorldGridTransfer.Outcome.SUCCESS) {
            ItemStack published = session.entity().getItem();
            // setItem is allowed to copy its argument, but only this same entity object may own the result.
            if (HandActorAuthority.revalidate(actor, authority) && authority.body().level() == session.level()
                    && session.level().getEntity(authority.body().getUUID()) == authority.body()
                    && session.level().getEntity(session.target()) == session.entity()
                    && !session.entity().isRemoved() && session.entity().isAddedToLevel()
                    && published != null && GridContents.read(published).isPresent()
                    && state.sessions.get(action.session()) == session && revision < Long.MAX_VALUE) {
                ItemStack copy = published.copy();
                if (session.entity().getItem() == published && ItemStack.matches(copy, published)) {
                    state.sessions.put(action.session(), new Session(actor, authority, session.level(), session.entity(),
                            session.target(), published, copy, renewedExpiry(actor.getServer().getTickCount()), revision + 1));
                    BodyHandStateService.requireFreshQuery(actor);
                    return result(action, revision + 1, BodyWorldGridActionResult.Reason.OK);
                }
            }
            outcome = BodyWorldGridTransfer.Outcome.RECOVERY_REQUIRED;
        }
        if (outcome == BodyWorldGridTransfer.Outcome.RECOVERY_REQUIRED || !valid(actor, authority, session)
                || state.sessions.get(action.session()) != session || hands.revision() != action.handRevision()
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands) {
            revoke(state, action.session());
            BodyHandStateService.requireFreshQuery(actor);
            return result(action, 0, BodyWorldGridActionResult.Reason.RECOVERY_REQUIRED);
        }
        return result(action, revision, BodyWorldGridActionResult.Reason.DENIED);
    }

    private static BodyWorldGridActionResult result(BodyWorldGridAction action, long revision,
                                                     BodyWorldGridActionResult.Reason reason) {
        return new BodyWorldGridActionResult(action.epoch(), action.sequence(), action.target(), action.session(), revision, reason);
    }

    static BodyWorldCrossActionResult cross(ServerPlayer actor, BodyWorldCrossAction action) {
        ActorState state = ACTORS.computeIfAbsent(actor, ignored -> new ActorState());
        var resolved = HandActorAuthority.resolve(actor);
        state.bind(resolved.orElse(null));
        if (resolved.isEmpty()) return crossResult(action, 0, BodyWorldCrossActionResult.Reason.NO_AUTHORITY);
        var authority = resolved.orElseThrow();
        if (authority.epoch() != action.epoch())
            return crossResult(action, 0, BodyWorldCrossActionResult.Reason.WRONG_EPOCH);
        var admission = state.actionGate.reject(action.epoch(), action.sequence(), actor.getServer().getTickCount());
        if (admission != null) return crossResult(action, 0, BodyWorldCrossActionResult.Reason.valueOf(admission.name()));
        Session session = state.sessions.get(action.session());
        if (session == null || session.actor() != actor || session.authority().body() != authority.body()
                || session.authority().epoch() != authority.epoch() || !session.target().equals(action.target()))
            return crossResult(action, 0, BodyWorldCrossActionResult.Reason.STALE);
        if (!actionLeaseValid(actor.getServer().getTickCount(), session.expiresAt()))
            return crossResult(action, session.hostRevision(), BodyWorldCrossActionResult.Reason.STALE);
        if (!valid(actor, authority, session)) {
            revoke(state, action.session());
            return crossResult(action, 0, BodyWorldCrossActionResult.Reason.STALE);
        }
        long revision = session.hostRevision();
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (revision == Long.MAX_VALUE || revision != action.worldRevision() || hands == null
                || !hands.compatible(authority.body()) || hands.revision() != action.bodyRevision()
                || !BodyHandStateService.hasIssued(actor, authority))
            return crossResult(action, revision, BodyWorldCrossActionResult.Reason.STALE);
        boolean reachable;
        try {
            reachable = BodyItemTargetProbe.reachableStorageHost(session.level(), authority.body(), session.entity(),
                    BodyItemTargetProbe.MAX_REACH) && valid(actor, authority, session)
                    && state.sessions.get(action.session()) == session
                    && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                    && hands.revision() == action.bodyRevision();
        } catch (RuntimeException callbackFailure) { reachable = false; }
        if (!reachable) {
            revoke(state, action.session());
            return crossResult(action, 0, BodyWorldCrossActionResult.Reason.STALE);
        }
        BodyWorldCrossTransfer.Outcome outcome;
        try { outcome = BodyWorldCrossTransfer.apply(actor, authority, session.entity(), session.reference(), session.copy(), action); }
        catch (RuntimeException callbackFailure) { outcome = BodyWorldCrossTransfer.Outcome.RECOVERY_REQUIRED; }
        if (outcome == BodyWorldCrossTransfer.Outcome.SUCCESS) {
            ItemStack published = session.entity().getItem();
            LiveHands updated = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (HandActorAuthority.revalidate(actor, authority) && authority.body().level() == session.level()
                    && session.level().getEntity(authority.body().getUUID()) == authority.body()
                    && session.level().getEntity(session.target()) == session.entity() && !session.entity().isRemoved()
                    && session.entity().isAddedToLevel() && published != null && GridContents.read(published).isPresent()
                    && updated != null && updated.revision() == action.bodyRevision() + 1 && updated.compatible(authority.body())
                    && state.sessions.get(action.session()) == session) {
                ItemStack copy = published.copy();
                if (session.entity().getItem() == published && ItemStack.matches(copy, published)) {
                    state.sessions.put(action.session(), new Session(actor, authority, session.level(), session.entity(),
                            session.target(), published, copy, renewedExpiry(actor.getServer().getTickCount()), revision + 1));
                    BodyHandStateService.requireFreshQuery(actor);
                    return crossResult(action, revision + 1, BodyWorldCrossActionResult.Reason.OK);
                }
            }
            outcome = BodyWorldCrossTransfer.Outcome.RECOVERY_REQUIRED;
        }
        if (outcome == BodyWorldCrossTransfer.Outcome.RECOVERY_REQUIRED || !valid(actor, authority, session)
                || state.sessions.get(action.session()) != session || hands.revision() != action.bodyRevision()
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands) {
            revoke(state, action.session());
            BodyHandStateService.requireFreshQuery(actor);
            return crossResult(action, 0, BodyWorldCrossActionResult.Reason.RECOVERY_REQUIRED);
        }
        return crossResult(action, revision, BodyWorldCrossActionResult.Reason.DENIED);
    }

    private static BodyWorldCrossActionResult crossResult(BodyWorldCrossAction action, long revision,
            BodyWorldCrossActionResult.Reason reason) {
        return new BodyWorldCrossActionResult(action.epoch(), action.sequence(), action.target(), action.session(), revision, reason);
    }

    static BodyWorldWorldCrossActionResult worldCross(ServerPlayer actor, BodyWorldWorldCrossAction action) {
        ActorState state = ACTORS.computeIfAbsent(actor, ignored -> new ActorState());
        var resolved = HandActorAuthority.resolve(actor);
        state.bind(resolved.orElse(null));
        if (resolved.isEmpty()) return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.NO_AUTHORITY);
        var authority = resolved.orElseThrow();
        if (authority.epoch() != action.epoch()) return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.WRONG_EPOCH);
        var admission = state.actionGate.reject(action.epoch(), action.sequence(), actor.getServer().getTickCount());
        if (admission != null) return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.valueOf(admission.name()));
        Session source = state.sessions.get(action.sourceSession()), destination = state.sessions.get(action.destinationSession());
        if (source == null || destination == null || source == destination || source.actor() != actor || destination.actor() != actor
                || source.authority().body() != authority.body() || destination.authority().body() != authority.body()
                || source.authority().epoch() != authority.epoch() || destination.authority().epoch() != authority.epoch()
                || !source.target().equals(action.sourceTarget()) || !destination.target().equals(action.destinationTarget()))
            return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.STALE);
        var hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        long tick = actor.getServer().getTickCount();
        if (expired(tick, source.expiresAt()) || expired(tick, destination.expiresAt()))
            return worldCrossResult(action, source.hostRevision(), destination.hostRevision(), BodyWorldWorldCrossActionResult.Reason.STALE);
        if (hands == null || !hands.compatible(authority.body()) || hands.revision() != action.bodyHandRevision()
                || !BodyHandStateService.hasIssued(actor, authority)
                || source.hostRevision() == Long.MAX_VALUE || destination.hostRevision() == Long.MAX_VALUE
                || source.hostRevision() != action.sourceHostRevision() || destination.hostRevision() != action.destinationHostRevision()
                || source.level() != destination.level()
                || !valid(actor, authority, source) || !valid(actor, authority, destination)) {
            revoke(state, action.sourceSession()); revoke(state, action.destinationSession());
            return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.STALE);
        }
        boolean reachable;
        try { reachable = BodyItemTargetProbe.reachableStorageHost(source.level(), authority.body(), source.entity(), BodyItemTargetProbe.MAX_REACH)
                    && BodyItemTargetProbe.reachableStorageHost(destination.level(), authority.body(), destination.entity(), BodyItemTargetProbe.MAX_REACH)
                    && valid(actor, authority, source) && valid(actor, authority, destination)
                    && state.sessions.get(action.sourceSession()) == source && state.sessions.get(action.destinationSession()) == destination
                    && hands.revision() == action.bodyHandRevision(); }
        catch (RuntimeException failure) { reachable = false; }
        if (!reachable) { revoke(state, action.sourceSession()); revoke(state, action.destinationSession()); return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.STALE); }
        var sourceReference = source.reference();
        var destinationReference = destination.reference();
        com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransfer.Outcome outcome;
        try {
            outcome = com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransfer.transfer(authority.body(), source.entity(), destination.entity(),
                    action.childToken(), action.oldX(), action.oldY(), rotation(action.oldRotation()),
                    action.newX(), action.newY(), rotation(action.newRotation()),
                    () -> HandActorAuthority.revalidate(actor, authority)
                            && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                            && hands.compatible(authority.body()) && hands.revision() == action.bodyHandRevision()
                            && state.sessions.get(action.sourceSession()) == source && state.sessions.get(action.destinationSession()) == destination
                            && BodyItemTargetProbe.reachableStorageHost(source.level(), authority.body(), source.entity(), BodyItemTargetProbe.MAX_REACH)
                            && BodyItemTargetProbe.reachableStorageHost(destination.level(), authority.body(), destination.entity(), BodyItemTargetProbe.MAX_REACH));
        } catch (RuntimeException callbackFailure) {
            outcome = com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransfer.Outcome.RECOVERY_REQUIRED;
        }
        if (outcome == com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransfer.Outcome.SUCCESS) {
            ItemStack src = source.entity().getItem(), dst = destination.entity().getItem();
            boolean stillValid = HandActorAuthority.revalidate(actor, authority)
                    && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                    && hands.compatible(authority.body()) && hands.revision() == action.bodyHandRevision()
                    && state.sessions.get(action.sourceSession()) == source && state.sessions.get(action.destinationSession()) == destination
                    && source.entity().getItem() == src && destination.entity().getItem() == dst
                    && src != sourceReference && dst != destinationReference
                    && ItemStack.matches(src, source.entity().getItem()) && ItemStack.matches(dst, destination.entity().getItem())
                    && GridContents.read(src).isPresent() && GridContents.read(dst).isPresent()
                    && source.hostRevision() < Long.MAX_VALUE && destination.hostRevision() < Long.MAX_VALUE
                    && source.level().getEntity(source.target()) == source.entity()
                    && destination.level().getEntity(destination.target()) == destination.entity()
                    && !source.entity().isRemoved() && !destination.entity().isRemoved();
            if (stillValid) {
                long newSourceRevision = source.hostRevision() + 1, newDestinationRevision = destination.hostRevision() + 1;
                state.sessions.put(action.sourceSession(), new Session(actor, authority, source.level(), source.entity(), source.target(), src, src.copy(), renewedExpiry(tick), newSourceRevision));
                state.sessions.put(action.destinationSession(), new Session(actor, authority, destination.level(), destination.entity(), destination.target(), dst, dst.copy(), renewedExpiry(tick), newDestinationRevision));
                BodyHandStateService.requireFreshQuery(actor);
                return worldCrossResult(action, newSourceRevision, newDestinationRevision, BodyWorldWorldCrossActionResult.Reason.OK);
            }
            outcome = com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransfer.Outcome.RECOVERY_REQUIRED;
        }
        if (outcome == com.juicyslew.moonstation14.ms14.hands.live.BodyWorldWorldGridTransfer.Outcome.RECOVERY_REQUIRED) {
            revoke(state, action.sourceSession()); revoke(state, action.destinationSession()); BodyHandStateService.requireFreshQuery(actor);
            return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.RECOVERY_REQUIRED);
        }
        if (source.entity().getItem() != sourceReference || destination.entity().getItem() != destinationReference
                || !ItemStack.matches(source.copy(), sourceReference) || !ItemStack.matches(destination.copy(), destinationReference)) {
            revoke(state, action.sourceSession()); revoke(state, action.destinationSession());
            BodyHandStateService.requireFreshQuery(actor);
            return worldCrossResult(action, 0, 0, BodyWorldWorldCrossActionResult.Reason.RECOVERY_REQUIRED);
        }
        return worldCrossResult(action, source.hostRevision(), destination.hostRevision(), BodyWorldWorldCrossActionResult.Reason.DENIED);
    }

    private static BodyWorldWorldCrossActionResult worldCrossResult(BodyWorldWorldCrossAction action, long sourceRevision,
            long destinationRevision, BodyWorldWorldCrossActionResult.Reason reason) {
        return new BodyWorldWorldCrossActionResult(action.epoch(), action.sequence(), action.sourceTarget(), action.sourceSession(),
                sourceRevision, action.destinationTarget(), action.destinationSession(), destinationRevision, reason);
    }

    private static GridGeometry.Rotation rotation(int degrees) {
        return switch (degrees) {
            case 0 -> GridGeometry.Rotation.DEG_0;
            case 90 -> GridGeometry.Rotation.DEG_90;
            case 180 -> GridGeometry.Rotation.DEG_180;
            case 270 -> GridGeometry.Rotation.DEG_270;
            default -> throw new IllegalArgumentException("Invalid rotation");
        };
    }

    private static boolean valid(ServerPlayer actor, HandActorAuthority.Snapshot authority, Session session) {
        return sessionBindingValid(actor, authority, session) && session.entity().getItem() == session.reference()
                && ItemStack.matches(session.copy(), session.reference());
    }

    private static boolean sessionBindingValid(ServerPlayer actor, HandActorAuthority.Snapshot authority, Session session) {
        LivingEntity body = authority.body();
        return HandActorAuthority.revalidate(actor, session.authority())
                && body.level() == session.level() && body.isAddedToLevel() && !body.isRemoved()
                && session.level().getEntity(body.getUUID()) == body
                && session.entity().level() == session.level() && !session.entity().isRemoved()
                && session.entity().isAddedToLevel() && session.level().getEntity(session.target()) == session.entity()
                && session.target().equals(session.entity().getUUID());
    }

    private static BodyWorldGridSnapshot snapshot(BodyWorldGridQuery q, ServerPlayer actor, LivingEntity body,
                                                   UUID target, ItemStack host, List<GridContents.Entry> entries,
                                                   long revision, UUID nonce, long hostRevision) {
        boolean bag = host.is(com.juicyslew.moonstation14.item.ModItems.BAG);
        var projected = entries.stream().map(e -> new BodyWorldGridSnapshot.Entry(e.token(),
                BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString(), e.stack().getCount(),
                e.x(), e.y(), e.rotation().degrees())).toList();
        return new BodyWorldGridSnapshot(q.epoch(), q.sequence(), BodyWorldGridSnapshot.Reason.OK,
                actor.getUUID(), body.getUUID(), q.epoch(), revision, target, bag ? "bag" : "belt",
                bag ? 7 : 8, bag ? 4 : 2, projected, nonce, hostRevision);
    }

    static boolean close(ServerPlayer actor, BodyWorldGridClose close) {
        ActorState state = ACTORS.get(actor);
        if (state == null) return false;
        var resolved = HandActorAuthority.resolve(actor);
        state.bind(resolved.orElse(null));
        Session session = state.sessions.get(close.session());
        if (resolved.isEmpty() || session == null || session.actor() != actor
                || resolved.orElseThrow().body() != session.authority().body()
                || resolved.orElseThrow().epoch() != close.epoch() || session.authority().epoch() != close.epoch()
                || !session.target().equals(close.target())) return false;
        if (!state.slots.close(close.session(), close.target())) return false;
        state.sessions.remove(close.session());
        return true;
    }
}

