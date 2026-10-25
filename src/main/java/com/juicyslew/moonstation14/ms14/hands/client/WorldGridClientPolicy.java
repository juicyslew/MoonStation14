package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridClose;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldWorldCrossAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldWorldCrossActionResult;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Client-side correlation and proof gate for world storage observations and intents. */
public final class WorldGridClientPolicy {
    public static final int MAX_SESSIONS = 3;
    public static final long MAX_AGE_TICKS = 100;

    /** Reasons proving the observed target can no longer host this world session. */
    public static boolean materiallyUnavailable(BodyWorldGridSnapshot.Reason reason) {
        return reason == BodyWorldGridSnapshot.Reason.UNAVAILABLE
                || reason == BodyWorldGridSnapshot.Reason.NO_AUTHORITY;
    }

    private UUID accountId, bodyId;
    private long epoch, nextSequence = 1, nextActionSequence = 1;
    private PendingAction action;
    private long settledHandRevision = -1;
    private BodyWorldGridClose rejectedGrantClose;
    private final Map<Long, Request> pending = new HashMap<>();
    private final Map<Long, Request> canceled = new HashMap<>();
    private final Map<UUID, GridView> sessions = new LinkedHashMap<>();

    public void bind(UUID accountId, UUID bodyId, long epoch) {
        if (accountId == null || bodyId == null || epoch <= 0) throw new IllegalArgumentException("Invalid binding");
        if (!accountId.equals(this.accountId) || !bodyId.equals(this.bodyId) || epoch != this.epoch) {
            this.accountId = accountId;
            this.bodyId = bodyId;
            this.epoch = epoch;
            pending.clear();
            canceled.clear();
            sessions.clear();
            action = null;
            settledHandRevision = -1;
        }
    }

    /** Start a session for a distinct target, or request a refresh for an existing session. */
    public Optional<BodyWorldGridQuery> query(UUID target, long handRevision) {
        return query(target, handRevision, 0, 0);
    }

    /** Count non-world windows against the same three visible slots, including unconfirmed world opens. */
    public Optional<BodyWorldGridQuery> query(UUID target, long handRevision, int otherWindows, long tick) {
        if (accountId == null || target == null || handRevision < 0 || pendingTarget(target)) return Optional.empty();
        GridView current = sessions.get(target);
        if (current == null) {
            long opening = pending.values().stream().filter(r -> r.session == null).count();
            if (otherWindows < 0 || sessions.size() + opening + otherWindows >= MAX_SESSIONS) return Optional.empty();
        }
        long sequence = allocateSequence();
        UUID session = current == null ? null : current.session;
        pending.put(sequence, new Request(target, session, tick, sequence));
        return Optional.of(new BodyWorldGridQuery(epoch, sequence, target, handRevision, session));
    }

    private long allocateSequence() {
        if (nextSequence <= 0 || nextSequence == Long.MAX_VALUE) throw new IllegalStateException("Query sequence exhausted");
        return nextSequence++;
    }

    private boolean pendingTarget(UUID target) { return pending.values().stream().anyMatch(r -> r.target.equals(target)); }

    public boolean hasPendingOpen(UUID target) {
        return target != null && pending.values().stream().anyMatch(r -> r.target.equals(target) && r.session == null);
    }

    /** True only when this response identifies the currently pending open, not merely its entity. */
    public boolean matchesPendingOpen(BodyWorldGridSnapshot snapshot) {
        return pendingOpenTarget(snapshot).isPresent();
    }

    public Optional<UUID> pendingOpenTarget(BodyWorldGridSnapshot snapshot) {
        if (snapshot == null || accountId == null || snapshot.requestedEpoch() != epoch) return Optional.empty();
        Request request = pending.get(snapshot.sequence());
        if (request == null || request.session != null) return Optional.empty();
        if (snapshot.reason() == BodyWorldGridSnapshot.Reason.OK && (!request.target.equals(snapshot.entityId())
                || snapshot.epoch() != epoch || !accountId.equals(snapshot.accountId()) || !bodyId.equals(snapshot.bodyId())))
            return Optional.empty();
        return Optional.of(request.target);
    }

    public void close(UUID target) {
        if (action != null && (action.target.equals(target)
                || action.dual && (action.sourceTarget.equals(target) || action.destinationTarget.equals(target)))) {
            PendingAction closing = action;
            invalidateActionViews();
            action = null;
            if (closing.dual) {
                cancelPendingFor(closing.sourceTarget);
                cancelPendingFor(closing.destinationTarget);
            }
        }
        sessions.remove(target);
        cancelPendingFor(target);
    }

    private void cancelPendingFor(UUID target) {
        pending.entrySet().removeIf(e -> {
            if (!e.getValue().target.equals(target)) return false;
            canceled.put(e.getKey(), e.getValue());
            return true;
        });
    }

    public int pendingOpens() { return (int) pending.values().stream().filter(r -> r.session == null).count(); }

    public void cancelPendingOpens() {
        pending.entrySet().removeIf(e -> {
            if (e.getValue().session != null) return false;
            canceled.put(e.getKey(), e.getValue());
            return true;
        });
    }

    /** Release a server grant that arrived after its client-side open was canceled. */
    public Optional<BodyWorldGridClose> lateClose(BodyWorldGridSnapshot snapshot) {
        if (snapshot == null) return Optional.empty();
        Request request = canceled.remove(snapshot.sequence());
        if (request == null || request.session != null || snapshot.reason() != BodyWorldGridSnapshot.Reason.OK
                || snapshot.requestedEpoch() != epoch || snapshot.epoch() != epoch
                || !accountId.equals(snapshot.accountId()) || !bodyId.equals(snapshot.bodyId())
                || !request.target.equals(snapshot.entityId())) return Optional.empty();
        return Optional.of(new BodyWorldGridClose(epoch, request.target, snapshot.session()));
    }

    public Optional<BodyWorldGridClose> rejectedGrantClose() {
        var close = rejectedGrantClose;
        rejectedGrantClose = null;
        return Optional.ofNullable(close);
    }

    /** Correlates a response and returns true only when it changed displayed state. */
    public boolean accept(BodyWorldGridSnapshot snapshot, long tick) {
        if (snapshot == null || accountId == null) return false;
        Request request = pending.get(snapshot.sequence());
        if (request == null || snapshot.requestedEpoch() != epoch) return false;
        if (snapshot.reason() == BodyWorldGridSnapshot.Reason.OK
                && (!accountId.equals(snapshot.accountId()) || !bodyId.equals(snapshot.bodyId()) || snapshot.epoch() != epoch
                || !request.target.equals(snapshot.entityId()) || snapshot.session() == null)) return false;
        pending.remove(snapshot.sequence());
        if (snapshot.reason() != BodyWorldGridSnapshot.Reason.OK) {
            if (materiallyUnavailable(snapshot.reason()))
                close(request.target);
            else {
                GridView view = sessions.get(request.target);
                if (view != null) sessions.put(request.target, view.withActionable(false));
            }
            return true;
        }
        GridView current = sessions.get(request.target);
        if (request.session == null) {
            long opening = pending.values().stream().filter(r -> r.session == null).count();
            if (current != null || sessions.size() + opening >= MAX_SESSIONS) {
                rejectedGrantClose = new BodyWorldGridClose(epoch, request.target, snapshot.session());
                return false;
            }
        } else if (current == null || !request.session.equals(current.session) || !request.session.equals(snapshot.session())) return false;
        try {
            GridGeometry.Snapshot<String> geometry = GridGeometry.Snapshot.empty(GridGeometry.StorageMask.rectangle(snapshot.width(), snapshot.height()));
            Map<String, BodyWorldGridSnapshot.Entry> entries = new LinkedHashMap<>();
            for (var entry : snapshot.entries()) {
                var id = ResourceLocation.tryParse(entry.itemId());
                if (id == null) return false;
                var mutation = geometry.place(entry.token(), ItemFootprints.clientForPresentation(id, entry.presentation()), new GridGeometry.Cell(entry.x(), entry.y()), GridGeometry.Rotation.values()[entry.rotation() / 90]);
                if (!mutation.accepted()) return false;
                geometry = mutation.snapshot(); entries.put(entry.token(), entry);
            }
            if (current != null && snapshot.hostRevision() < current.hostRevision) return false;
            if (current != null && snapshot.hostRevision() == current.hostRevision
                    && !(action != null && action.resultRevision >= 0 && request.sequence >= action.querySequenceCutoff)
                    && (!snapshot.kind().equals(current.kind) || snapshot.width() != current.width()
                    || snapshot.height() != current.height() || !new ArrayList<>(entries.values()).equals(current.entries))) return false;
            boolean pendingThisAction = action != null && action.target.equals(request.target)
                    && action.session.equals(snapshot.session());
            boolean refreshAfterAction = action != null && action.target.equals(request.target)
                    && action.session.equals(snapshot.session()) && action.resultRevision >= 0
                    && request.sequence >= action.querySequenceCutoff && snapshot.hostRevision() >= action.resultRevision;
            if (pendingThisAction && !refreshAfterAction) {
                if (current != null) sessions.put(request.target, current.withFresh(false));
                return false;
            }
            boolean dualSource = action != null && action.dual && action.sourceTarget.equals(request.target)
                    && action.sourceSession.equals(snapshot.session()) && action.resultRevision >= 0
                    && request.sequence >= action.querySequenceCutoff && snapshot.hostRevision() >= action.sourceRevision;
            boolean dualDestination = action != null && action.dual && action.destinationTarget.equals(request.target)
                    && action.destinationSession.equals(snapshot.session()) && action.resultRevision >= 0
                    && request.sequence >= action.querySequenceCutoff && snapshot.hostRevision() >= action.destinationRevision;
            boolean pendingDual = action != null && action.dual && (action.sourceTarget.equals(request.target)
                    || action.destinationTarget.equals(request.target));
            if (pendingDual && !dualSource && !dualDestination) {
                if (current != null) sessions.put(request.target, current.withFresh(false));
                return false;
            }
            sessions.put(request.target, new GridView(request.target, snapshot.session(), snapshot.kind(), geometry, new ArrayList<>(entries.values()),
                    snapshot.handRevision(), snapshot.hostRevision(), tick, true, true));
            if (refreshAfterAction) {
                action.worldSettled = true;
                settleAction();
            }
            if (dualSource) { action.sourceSettled = true; settleAction(); }
            if (dualDestination) { action.destinationSettled = true; settleAction(); }
            return true;
        } catch (RuntimeException invalid) { return false; }
    }

    public void advance(long tick) {
        if (action != null && tick - action.sentAt >= MAX_AGE_TICKS) {
            invalidateActionViews();
            action = null;
        }
        canceled.values().removeIf(r -> tick - r.sentAt >= MAX_AGE_TICKS * 2);
        pending.entrySet().removeIf(e -> {
            if (tick - e.getValue().sentAt < MAX_AGE_TICKS) return false;
            canceled.put(e.getKey(), e.getValue());
            return true;
        });
        for (var e : new ArrayList<>(sessions.entrySet())) {
            long age = tick - e.getValue().refreshedAt;
            if (age >= MAX_AGE_TICKS / 2) sessions.put(e.getKey(), e.getValue().withFresh(false));
        }
    }

    public Collection<GridView> views() { return List.copyOf(sessions.values()); }
    public GridView view(UUID target) { return sessions.get(target); }
    public boolean canAct(UUID target) {
        var view = sessions.get(target);
        return view != null && view.actionsEnabled() && action == null && !pendingTarget(target)
                && settledHandRevision == view.handRevision;
    }

    public void clear() {
        accountId = bodyId = null;
        epoch = 0;
        pending.clear(); canceled.clear(); sessions.clear();
        action = null;
        settledHandRevision = -1;
    }

    public void settledHand(BodyHandStateSnapshot proof) {
        settledHandRevision = proof != null && accountId != null && accountId.equals(proof.accountId())
                && bodyId.equals(proof.bodyId()) && epoch == proof.epoch() ? proof.revision() : -1;
        if (action != null && action.resultRevision >= 0 && action.handQueryIssued
                && handQueryGeneration > action.handQueryGeneration
                && settledHandRevision >= action.handRevision) {
            action.handSettled = true;
            settleAction();
        }
    }

    private long handQueryGeneration;
    public void handQueryIssued() {
        handQueryGeneration++;
        if (action != null && action.resultRevision >= 0) action.handQueryIssued = true;
    }

    private void settleAction() {
        if (action != null && action.handSettled && (action.dual
                ? action.sourceSettled && action.destinationSettled : action.worldSettled)) action = null;
    }

    public Optional<BodyWorldGridAction> action(UUID target, BodyHandStateSnapshot proof, BodyWorldGridAction.Mode mode,
            String hand, String token, int oldX, int oldY, int oldRotation, int newX, int newY, int newRotation, long tick) {
        GridView view = sessions.get(target);
        if (view == null || !view.actionsEnabled() || action != null || pendingTarget(target) || proof == null
                || !accountId.equals(proof.accountId()) || !bodyId.equals(proof.bodyId()) || epoch != proof.epoch()
                || proof.revision() != settledHandRevision || view.handRevision != settledHandRevision) return Optional.empty();
        long sequence = nextActionSequence++;
        if (sequence <= 0) throw new IllegalStateException("Action sequence exhausted");
        BodyWorldGridAction request = new BodyWorldGridAction(mode, epoch, sequence, target, view.session,
                proof.revision(), view.hostRevision, hand, token, oldX, oldY, oldRotation, newX, newY, newRotation);
        action = new PendingAction(target, view.session, sequence, proof.revision(), tick);
        sessions.put(target, view.withFresh(false));
        return Optional.of(request);
    }

    /** Direct world/body transfer intent; it is not routed through a hand action. */
    public Optional<BodyWorldCrossAction> crossAction(UUID target, BodyHandStateSnapshot proof,
            BodyWorldCrossAction.Direction direction, boolean worn, String bodyHostId, String bodyHostToken,
            String childToken, int oldX, int oldY, int oldRotation, int newX, int newY, int newRotation, long tick) {
        GridView view = sessions.get(target);
        if (accountId == null || view == null || !view.actionsEnabled() || action != null || pendingTarget(target)
                || proof == null || !accountId.equals(proof.accountId()) || !bodyId.equals(proof.bodyId())
                || epoch != proof.epoch() || proof.revision() != settledHandRevision
                || view.handRevision != settledHandRevision) return Optional.empty();
        long sequence = nextActionSequence++;
        if (sequence <= 0) throw new IllegalStateException("Action sequence exhausted");
        var request = new BodyWorldCrossAction(direction, epoch, sequence, target, view.session,
                view.hostRevision, proof.revision(), worn, bodyHostId, bodyHostToken, childToken,
                oldX, oldY, oldRotation, newX, newY, newRotation);
        action = new PendingAction(target, view.session, sequence, proof.revision(), tick);
        sessions.put(target, view.withFresh(false));
        return Optional.of(request);
    }

    /** Transfer a child between two independently open world sessions under the shared action gate. */
    public Optional<BodyWorldWorldCrossAction> worldCrossAction(UUID sourceTarget, UUID destinationTarget,
            BodyHandStateSnapshot proof, String childToken, int oldX, int oldY, int oldRotation,
            int newX, int newY, int newRotation, long tick) {
        GridView source = sessions.get(sourceTarget), destination = sessions.get(destinationTarget);
        if (accountId == null || sourceTarget == null || destinationTarget == null || sourceTarget.equals(destinationTarget)
                || source == null || destination == null || source.session.equals(destination.session)
                || !source.actionsEnabled() || !destination.actionsEnabled() || action != null
                || pendingTarget(sourceTarget) || pendingTarget(destinationTarget) || proof == null
                || !accountId.equals(proof.accountId()) || !bodyId.equals(proof.bodyId()) || epoch != proof.epoch()
                || proof.revision() != settledHandRevision || source.handRevision != settledHandRevision
                || destination.handRevision != settledHandRevision) return Optional.empty();
        long sequence = nextActionSequence++;
        if (sequence <= 0) throw new IllegalStateException("Action sequence exhausted");
        var request = new BodyWorldWorldCrossAction(epoch, sequence, sourceTarget, source.session, source.hostRevision,
                destinationTarget, destination.session, destination.hostRevision, proof.revision(), childToken,
                oldX, oldY, oldRotation, newX, newY, newRotation);
        action = PendingAction.dual(sourceTarget, source.session, destinationTarget, destination.session,
                sequence, proof.revision(), tick);
        sessions.put(sourceTarget, source.withFresh(false));
        sessions.put(destinationTarget, destination.withFresh(false));
        return Optional.of(request);
    }

    public boolean worldCrossResult(BodyWorldWorldCrossActionResult result) {
        if (action == null || !action.dual || result == null || result.epoch() != epoch
                || result.sequence() != action.sequence || !result.sourceTarget().equals(action.sourceTarget)
                || !result.sourceSession().equals(action.sourceSession)
                || !result.destinationTarget().equals(action.destinationTarget)
                || !result.destinationSession().equals(action.destinationSession)) return false;
        if (result.reason() == BodyWorldWorldCrossActionResult.Reason.STALE
                || result.reason() == BodyWorldWorldCrossActionResult.Reason.NO_AUTHORITY
                || result.reason() == BodyWorldWorldCrossActionResult.Reason.WRONG_EPOCH
                || result.reason() == BodyWorldWorldCrossActionResult.Reason.RECOVERY_REQUIRED) {
            closeDual();
        } else if (result.reason() == BodyWorldWorldCrossActionResult.Reason.OK) {
            action.resultRevision = 0;
            action.querySequenceCutoff = nextSequence;
            action.handQueryGeneration = handQueryGeneration;
            action.sourceRevision = result.sourceHostRevision();
            action.destinationRevision = result.destinationHostRevision();
            action.sourceSettled = action.destinationSettled = action.handSettled = false;
            settledHandRevision = -1;
        } else {
            invalidateActionViews(); action = null; settledHandRevision = -1;
        }
        return true;
    }

    private void invalidateActionViews() {
        if (action == null) return;
        var first = sessions.get(action.target); if (first != null) sessions.put(action.target, first.withFresh(false));
        if (action.dual) {
            var second = sessions.get(action.destinationTarget);
            if (second != null) sessions.put(action.destinationTarget, second.withFresh(false));
        }
    }

    private void closeDual() {
        if (action == null || !action.dual) return;
        UUID first = action.sourceTarget, second = action.destinationTarget;
        close(first); close(second);
    }

    /** A correlated result never confirms a placement; both independent queries must settle afterwards. */
    public boolean result(BodyWorldGridActionResult result) {
        if (action == null || result == null || result.epoch() != epoch || result.sequence() != action.sequence
                || !result.target().equals(action.target) || !result.session().equals(action.session)) return false;
        if (result.reason() == BodyWorldGridActionResult.Reason.STALE
                || result.reason() == BodyWorldGridActionResult.Reason.NO_AUTHORITY
                || result.reason() == BodyWorldGridActionResult.Reason.WRONG_EPOCH
                || result.reason() == BodyWorldGridActionResult.Reason.RECOVERY_REQUIRED) {
            close(action.target);
        } else {
            action.resultRevision = result.reason() == BodyWorldGridActionResult.Reason.OK ? result.hostRevision() : -1;
            action.querySequenceCutoff = nextSequence;
            action.handQueryGeneration = handQueryGeneration;
            action.handSettled = false;
            action.worldSettled = false;
            if (action.resultRevision < 0) {
                var old = sessions.get(action.target);
                if (old != null) sessions.put(action.target, old.withFresh(false));
                action = null;
            }
            settledHandRevision = -1;
        }
        return true;
    }

    /** Correlates a cross-transfer result with the same sequence gate and refresh barrier as grid actions. */
    public boolean crossResult(BodyWorldCrossActionResult result) {
        if (action == null || result == null || result.epoch() != epoch || result.sequence() != action.sequence
                || !result.target().equals(action.target) || !result.session().equals(action.session)) return false;
        if (result.reason() == BodyWorldCrossActionResult.Reason.STALE
                || result.reason() == BodyWorldCrossActionResult.Reason.NO_AUTHORITY
                || result.reason() == BodyWorldCrossActionResult.Reason.WRONG_EPOCH
                || result.reason() == BodyWorldCrossActionResult.Reason.RECOVERY_REQUIRED) {
            close(action.target);
        } else if (result.reason() == BodyWorldCrossActionResult.Reason.OK) {
            action.resultRevision = result.worldRevision();
            action.querySequenceCutoff = nextSequence;
            action.handQueryGeneration = handQueryGeneration;
            action.handSettled = false;
            action.worldSettled = false;
            settledHandRevision = -1;
        } else {
            var old = sessions.get(action.target);
            if (old != null) sessions.put(action.target, old.withFresh(false));
            action = null;
            settledHandRevision = -1;
        }
        return true;
    }

    private static final class PendingAction {
        final UUID target, session;
        final long sequence, handRevision, sentAt;
        long resultRevision = -1;
        long querySequenceCutoff, handQueryGeneration;
        final boolean dual;
        final UUID sourceTarget, sourceSession, destinationTarget, destinationSession;
        long sourceRevision = -1, destinationRevision = -1;
        boolean sourceSettled, destinationSettled;
        boolean handSettled, worldSettled, handQueryIssued;
        PendingAction(UUID target, UUID session, long sequence, long handRevision, long sentAt) {
            this.target = target; this.session = session; this.sequence = sequence;
            this.handRevision = handRevision; this.sentAt = sentAt;
            dual = false; sourceTarget = sourceSession = destinationTarget = destinationSession = null;
        }
        private PendingAction(UUID sourceTarget, UUID sourceSession, UUID destinationTarget, UUID destinationSession,
                              long sequence, long handRevision, long sentAt) {
            target=sourceTarget; session=sourceSession; this.sequence=sequence; this.handRevision=handRevision; this.sentAt=sentAt;
            dual=true; this.sourceTarget=sourceTarget; this.sourceSession=sourceSession;
            this.destinationTarget=destinationTarget; this.destinationSession=destinationSession;
        }
        static PendingAction dual(UUID sourceTarget, UUID sourceSession, UUID destinationTarget, UUID destinationSession,
                                  long sequence, long handRevision, long sentAt) {
            return new PendingAction(sourceTarget, sourceSession, destinationTarget, destinationSession, sequence, handRevision, sentAt);
        }
    }

    private record Request(UUID target, UUID session, long sentAt, long sequence) { }

    /** Immutable display projection; deliberately contains no transfer authority. */
    public static final class GridView {
        private final UUID target, session;
        private final String kind;
        private final GridGeometry.Snapshot<String> geometry;
        private final List<BodyWorldGridSnapshot.Entry> entries;
        private final long handRevision, hostRevision, refreshedAt;
        private final boolean confirmed, fresh;
        private GridView(UUID target, UUID session, String kind, GridGeometry.Snapshot<String> geometry,
                          List<BodyWorldGridSnapshot.Entry> entries, long handRevision, long hostRevision,
                         long refreshedAt, boolean confirmed, boolean fresh) {
            this.target=target; this.session=session; this.kind=kind; this.geometry=geometry;
             this.entries=List.copyOf(entries); this.handRevision=handRevision; this.hostRevision=hostRevision;
            this.refreshedAt=refreshedAt; this.confirmed=confirmed; this.fresh=fresh;
        }
        private GridView withFresh(boolean v) { return new GridView(target,session,kind,geometry,entries,handRevision,hostRevision,refreshedAt,confirmed,v); }
        private GridView withActionable(boolean ignored) { return withFresh(false); }
        public UUID target() { return target; } public UUID session() { return session; } public String kind() { return kind; }
        public int width() { return geometry.storage().width(); } public int height() { return geometry.storage().height(); }
        public boolean occupied(int x, int y) { return geometry.isOccupied(new GridGeometry.Cell(x,y)); }
        public BodyWorldGridSnapshot.Entry entryAt(int x, int y) {
            var cell = new GridGeometry.Cell(x,y);
             return geometry.placements().values().stream().filter(p -> p.occupiedCells().contains(cell)).map(p -> entries.stream()
                     .filter(entry -> entry.token().equals(p.token())).findFirst().orElse(null)).findFirst().orElse(null);
         }
         public List<BodyWorldGridSnapshot.Entry> entries() { return entries; }
        public GridGeometry.Snapshot<String> geometry() { return geometry; }
        public long handRevision() { return handRevision; }
        public long hostRevision() { return hostRevision; }
        public long refreshedAt() { return refreshedAt; }
        public boolean confirmed() { return confirmed; } public boolean stale() { return !fresh; }
        public boolean actionsEnabled() { return confirmed && fresh; }
    }
}
