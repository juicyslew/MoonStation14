package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactPolicy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Bounded, session-local history of quantized ghost inputs for future prediction replay.
 * ACKs must reflect applied authority; never bridge discarded gaps. See
 * docs/shuttle-relocation-sprint/CHARACTER-INPUT-CADENCE.md.
 */
public final class GhostPredictionHistory {
    public static final int CAPACITY = GhostControlPayloads.MAX_PENDING_INTENTS;
    public static final int MAX_REPLAY_TICKS = CAPACITY;

    private final ArrayDeque<GhostControlPayloads.Intent> pending = new ArrayDeque<>(CAPACITY);
    private final Map<Long, PredictionEvidence> evidence = new HashMap<>();
    private long epoch;
    private int harnessEntityId;
    private MobHarnessKind harnessKind;
    private long lastRecordedSequence;
    private long lastAcknowledgedSequence;
    private long lastSettledThroughSequence;
    private long lastServerGameTick = -1;
    private long lastRecordedIntendedTick;
    private long lastAppliedReferenceSequence;
    private Optional<GhostControlPayloads.SupportedMovementReference> lastAppliedReference = Optional.empty();

    public GhostPredictionHistory(long epoch, int ghostEntityId) {
        this(epoch, ghostEntityId, MobHarnessKind.GHOST);
    }

    public GhostPredictionHistory(long epoch, int harnessEntityId, MobHarnessKind harnessKind) {
        setSession(epoch, harnessEntityId, harnessKind);
    }

    /** Records the exact validated wire intent; sequence gaps are retained as evidence of lost history. */
    public RecordResult record(GhostControlPayloads.Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.epoch() != epoch || intent.sequence() <= lastRecordedSequence
                || intent.intendedSimulationTick() < lastRecordedIntendedTick)
            return RecordResult.REJECTED;

        lastRecordedSequence = intent.sequence();
        if (pending.size() == CAPACITY) {
            GhostControlPayloads.Intent evicted = pending.removeFirst();
            evidence.remove(evicted.sequence());
        }
        lastRecordedIntendedTick = intent.intendedSimulationTick();
        pending.addLast(intent);
        return RecordResult.RECORDED;
    }

    /** Binds valid contact evidence only to an intent still pending in this exact history. */
    public boolean recordNormalTarget(long sequence, NativeNormalContactPolicy.Target target) {
        PredictionEvidence existing = evidence.get(sequence);
        return recordNormalTarget(sequence, existing == null ? intendedTick(sequence) : existing.applicationTick(), target);
    }

    /** Binds valid contact evidence to the exact local application interval that produced it. */
    public boolean recordNormalTarget(long sequence, long applicationTick, NativeNormalContactPolicy.Target target) {
        if (target == null || !target.valid() || !pendingSequence(sequence) || applicationTick < 0
                || !bindApplication(sequence, applicationTick)) return false;
        PredictionEvidence existing = evidence.get(sequence);
        evidence.put(sequence, new PredictionEvidence(target, existing.angularSupport(), applicationTick));
        return true;
    }

    /** Returns evidence only while its exact intent remains pending. */
    public Optional<NativeNormalContactPolicy.Target> normalTarget(long sequence) {
        return pendingSequence(sequence)
                ? Optional.ofNullable(evidence.get(sequence)).map(PredictionEvidence::normalTarget) : Optional.empty();
    }

    public Optional<NativeNormalContactPolicy.Target> normalTarget(long sequence, long applicationTick) {
        PredictionEvidence fact = evidence.get(sequence);
        return pendingSequence(sequence) && fact != null && fact.applicationTick() == applicationTick
                ? Optional.ofNullable(fact.normalTarget()) : Optional.empty();
    }

    /** Binds the first successful prediction application, even when it produced no optional facts. */
    public boolean recordApplication(long sequence, long applicationTick) {
        return pendingSequence(sequence) && applicationTick >= 0 && bindApplication(sequence, applicationTick);
    }

    public OptionalLong associatedApplication(long sequence) {
        PredictionEvidence fact = evidence.get(sequence);
        return pendingSequence(sequence) && fact != null ? OptionalLong.of(fact.applicationTick()) : OptionalLong.empty();
    }

    private boolean pendingSequence(long sequence) {
        return pending.stream().anyMatch(intent -> intent.sequence() == sequence);
    }

    private boolean bindApplication(long sequence, long applicationTick) {
        PredictionEvidence existing = evidence.get(sequence);
        if (existing != null && existing.applicationTick() != applicationTick) return false;
        if (existing == null) evidence.put(sequence, new PredictionEvidence(null, null, applicationTick));
        return true;
    }

    /** Captured angular support fact for this exact pending physical move; null frame means unsupported/world. */
    public boolean recordAngularEvidence(long sequence, AngularSupportEvidence evidence) {
        PredictionEvidence existing = this.evidence.get(sequence);
        return recordAngularEvidence(sequence, existing == null ? intendedTick(sequence) : existing.applicationTick(), evidence);
    }

    /** Binds a native fact to the exact local application interval that produced it. */
    public boolean recordAngularEvidence(long sequence, long applicationTick, AngularSupportEvidence evidence) {
        if (evidence == null || !pendingSequence(sequence)
                || applicationTick < 0
                || this.evidence.containsKey(sequence) && this.evidence.get(sequence).angularSupport() != null) return false;
        if (!bindApplication(sequence, applicationTick)) return false;
        PredictionEvidence existing = this.evidence.get(sequence);
        this.evidence.put(sequence, new PredictionEvidence(existing == null ? null : existing.normalTarget(), evidence, applicationTick));
        return true;
    }

    public Optional<AngularSupportEvidence> angularEvidence(long sequence) {
        return pending.stream().anyMatch(intent -> intent.sequence() == sequence)
                ? Optional.ofNullable(evidence.get(sequence)).map(PredictionEvidence::angularSupport) : Optional.empty();
    }

    public Optional<AngularSupportEvidence> angularEvidence(long sequence, long applicationTick) {
        PredictionEvidence fact = evidence.get(sequence);
        return pending.stream().anyMatch(intent -> intent.sequence() == sequence) && fact != null
                && fact.applicationTick() == applicationTick ? Optional.ofNullable(fact.angularSupport()) : Optional.empty();
    }

    /** Read-only lookup of the original wire intent; held inputs have no fabricated sequence. */
    public Optional<GhostControlPayloads.Intent> pendingIntent(long sequence) {
        return pending.stream().filter(intent -> intent.sequence() == sequence).findFirst();
    }

    private long intendedTick(long sequence) {
        return pendingIntent(sequence).map(GhostControlPayloads.Intent::intendedSimulationTick).orElse(-1L);
    }

    public record AngularSupportEvidence(java.util.UUID frameId, boolean supported,
                                         com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3 omega) {
        public AngularSupportEvidence {
            Objects.requireNonNull(omega, "omega");
            if (supported ? frameId == null : frameId != null || !omega.equals(zeroOmega()))
                throw new IllegalArgumentException("angular support fact is inconsistent");
        }
    }

    private record PredictionEvidence(NativeNormalContactPolicy.Target normalTarget, AngularSupportEvidence angularSupport,
                                      long applicationTick) { }

    private static com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3 zeroOmega() {
        return new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0, 0, 0);
    }

    /** Advances the wire sequence over an intentionally unpredicted input frame. */
    public RecordResult skip(GhostControlPayloads.Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.epoch() != epoch || intent.sequence() <= lastRecordedSequence
                || intent.intendedSimulationTick() < lastRecordedIntendedTick)
            return RecordResult.REJECTED;
        lastRecordedSequence = intent.sequence();
        lastRecordedIntendedTick = intent.intendedSimulationTick();
        pending.clear();
        evidence.clear();
        return RecordResult.RECORDED;
    }

    /**
     * Accepts a newer authoritative snapshot and returns the exact pending intents to replay in sequence order.
     * The snapshot acknowledgement is settled-through: sequences at or below it were applied or permanently
     * skipped by the server, so they are discarded; only newer intents are replayed. Rejected snapshots leave the
     * history untouched.
     */
    public SnapshotResult reconcile(GhostControlPayloads.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        long acknowledged = snapshot.acknowledgedAppliedSequence();
        long settledThrough = snapshot.settledThroughSequence();
        if (snapshot.epoch() != epoch || snapshot.harnessEntityId() != harnessEntityId
                || snapshot.harnessKind() != harnessKind
                || snapshot.serverGameTick() <= lastServerGameTick
                || acknowledged > lastRecordedSequence || settledThrough > lastRecordedSequence
                || acknowledged < lastAcknowledgedSequence || settledThrough < lastSettledThroughSequence)
            return SnapshotResult.rejected();

        lastServerGameTick = snapshot.serverGameTick();
        lastAcknowledgedSequence = acknowledged;
        lastSettledThroughSequence = settledThrough;
        pending.stream().filter(intent -> intent.sequence() == acknowledged).findFirst().ifPresent(intent -> {
            lastAppliedReferenceSequence = acknowledged;
            lastAppliedReference = intent.supportedMovementReference();
        });
        while (!pending.isEmpty() && pending.peekFirst().sequence() <= settledThrough) {
            long removed = pending.removeFirst().sequence();
            evidence.remove(removed);
        }

        List<GhostControlPayloads.Intent> replay = List.copyOf(pending);
        if (!isCompleteReplay(replay, settledThrough)) {
            // The prefix is incomplete, but facts captured for still-pending suffix intents remain valid.
            // They stay bounded by pending and are removed by acknowledgement, eviction, or a real barrier.
            return new SnapshotResult(SnapshotStatus.HISTORY_LOST, List.of());
        }
        return new SnapshotResult(SnapshotStatus.REPLAY, replay);
    }

    /** Clears buffered inputs and sequence/tick tracking while retaining this session identity. */
    public void clear() {
        pending.clear();
        evidence.clear();
        lastRecordedSequence = 0;
        lastAcknowledgedSequence = 0;
        lastSettledThroughSequence = 0;
        lastServerGameTick = -1;
        lastRecordedIntendedTick = 0;
        lastAppliedReferenceSequence = 0;
        lastAppliedReference = Optional.empty();
    }

    /** Clears buffered inputs after an authority barrier without resetting this session's tracking. */
    public void onAuthorityBarrier() {
        onPredictionFailureBarrier();
    }

    /** Drops replay but preserves the last server acknowledgement so a post-failure correction can settle it. */
    public void onPredictionFailureBarrier() {
        pending.clear();
        evidence.clear();
        lastAppliedReferenceSequence = 0;
        lastAppliedReference = Optional.empty();
    }

    /** Starts a fresh session, suitable after stop, teleport, or dimension change. */
    public void reset(long epoch, int ghostEntityId) {
        reset(epoch, ghostEntityId, MobHarnessKind.GHOST);
    }

    public void reset(long epoch, int harnessEntityId, MobHarnessKind harnessKind) {
        setSession(epoch, harnessEntityId, harnessKind);
        clear();
    }

    public long epoch() { return epoch; }
    public int ghostEntityId() { return harnessEntityId; }
    public int harnessEntityId() { return harnessEntityId; }
    public MobHarnessKind harnessKind() { return harnessKind; }
    public long lastRecordedSequence() { return lastRecordedSequence; }
    public long lastAcknowledgedSequence() { return lastAcknowledgedSequence; }
    public long lastServerGameTick() { return lastServerGameTick; }
    public long lastRecordedIntendedTick() { return lastRecordedIntendedTick; }
    public long lastSettledThroughSequence() { return lastSettledThroughSequence; }
    public int pendingCount() { return pending.size(); }
    public Optional<GhostControlPayloads.SupportedMovementReference> appliedSupportedMovementReference(long acknowledgedSequence) {
        return acknowledgedSequence == lastAppliedReferenceSequence ? lastAppliedReference : Optional.empty();
    }

    /** Builds a bounded fixed-step plan from the currently accepted authoritative anchor, without mutating history. */
    public TickReplayResult planReplayTicks(GhostControlPayloads.Snapshot base, long targetTick) {
        Objects.requireNonNull(base, "base");
        if (base.epoch() != epoch || base.harnessEntityId() != harnessEntityId || base.harnessKind() != harnessKind
                || base.serverGameTick() != lastServerGameTick
                || base.acknowledgedAppliedSequence() != lastAcknowledgedSequence
                || base.settledThroughSequence() != lastSettledThroughSequence || targetTick < 0)
            return TickReplayResult.rejected();
        List<GhostControlPayloads.Intent> frames = List.copyOf(pending);
        if (!isCompleteReplay(frames, lastSettledThroughSequence))
            return new TickReplayResult(SnapshotStatus.HISTORY_LOST, List.of());
        if (targetTick <= base.serverGameTick())
            return new TickReplayResult(SnapshotStatus.REPLAY, List.of());
        long count = Math.min((long) MAX_REPLAY_TICKS, targetTick - base.serverGameTick());
        GhostControlInputAccumulator accumulator = new GhostControlInputAccumulator();
        // The held state belongs to the applied acknowledgement, not a later settled
        // sequence that may include permanently skipped inputs.
        accumulator.seed(base.heldControlState(), base.acknowledgedAppliedSequence());
        ArrayList<GhostControlInputAccumulator.TickInput> plan = new ArrayList<>((int) count);
        int frameIndex = 0;
        for (long offset = 1; offset <= count; offset++) {
            long tick = base.serverGameTick() + offset;
            while (frameIndex < frames.size() && frames.get(frameIndex).intendedSimulationTick() <= tick) {
                GhostControlPayloads.Intent frame = frames.get(frameIndex++);
                accumulator.accept(frame.toControlState(), frame.sequence());
            }
            plan.add(accumulator.consumeForTick(tick));
        }
        return new TickReplayResult(SnapshotStatus.REPLAY, plan);
    }

    public record TickReplayResult(SnapshotStatus status, List<GhostControlInputAccumulator.TickInput> ticks) {
        public TickReplayResult { Objects.requireNonNull(status); ticks = List.copyOf(ticks); }
        private static TickReplayResult rejected() { return new TickReplayResult(SnapshotStatus.REJECTED, List.of()); }
    }

    private boolean isCompleteReplay(List<GhostControlPayloads.Intent> replay, long acknowledged) {
        if (acknowledged == lastRecordedSequence)
            return replay.isEmpty();
        if (replay.isEmpty() || acknowledged == Long.MAX_VALUE)
            return false;

        long expected = acknowledged + 1;
        for (GhostControlPayloads.Intent intent : replay) {
            if (intent.sequence() != expected)
                return false;
            if (expected != lastRecordedSequence)
                expected++;
        }
        return replay.get(replay.size() - 1).sequence() == lastRecordedSequence;
    }

    private void setSession(long epoch, int harnessEntityId, MobHarnessKind harnessKind) {
        if (epoch <= 0)
            throw new IllegalArgumentException("epoch must be positive");
        if (harnessEntityId < 0)
            throw new IllegalArgumentException("harness entity id must be nonnegative");
        Objects.requireNonNull(harnessKind, "harnessKind");
        this.epoch = epoch;
        this.harnessEntityId = harnessEntityId;
        this.harnessKind = harnessKind;
    }

    public enum RecordResult { RECORDED, REJECTED }

    public enum SnapshotStatus { REPLAY, HISTORY_LOST, REJECTED }

    public record SnapshotResult(SnapshotStatus status, List<GhostControlPayloads.Intent> replayFrames) {
        public SnapshotResult {
            Objects.requireNonNull(status, "status");
            replayFrames = List.copyOf(Objects.requireNonNull(replayFrames, "replayFrames"));
            if (status != SnapshotStatus.REPLAY && !replayFrames.isEmpty())
                throw new IllegalArgumentException("only replay results may contain frames");
        }

        private static SnapshotResult rejected() {
            return new SnapshotResult(SnapshotStatus.REJECTED, List.of());
        }
    }
}
