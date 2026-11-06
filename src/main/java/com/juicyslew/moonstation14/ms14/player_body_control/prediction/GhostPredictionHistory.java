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

/**
 * Bounded, session-local history of quantized ghost inputs for future prediction replay.
 * ACKs must reflect applied authority; never bridge discarded gaps. See
 * docs/shuttle-relocation-sprint/CHARACTER-INPUT-CADENCE.md.
 */
public final class GhostPredictionHistory {
    public static final int CAPACITY = 64;

    private final ArrayDeque<GhostControlPayloads.Intent> pending = new ArrayDeque<>(CAPACITY);
    private final Map<Long, PredictionEvidence> evidence = new HashMap<>();
    private long epoch;
    private int harnessEntityId;
    private MobHarnessKind harnessKind;
    private long lastRecordedSequence;
    private long lastAcknowledgedSequence;
    private long lastServerGameTick = -1;

    public GhostPredictionHistory(long epoch, int ghostEntityId) {
        this(epoch, ghostEntityId, MobHarnessKind.GHOST);
    }

    public GhostPredictionHistory(long epoch, int harnessEntityId, MobHarnessKind harnessKind) {
        setSession(epoch, harnessEntityId, harnessKind);
    }

    /** Records the exact validated wire intent; sequence gaps are retained as evidence of lost history. */
    public RecordResult record(GhostControlPayloads.Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.epoch() != epoch || intent.sequence() <= lastRecordedSequence)
            return RecordResult.REJECTED;

        lastRecordedSequence = intent.sequence();
        if (pending.size() == CAPACITY) {
            GhostControlPayloads.Intent evicted = pending.removeFirst();
            evidence.remove(evicted.sequence());
        }
        pending.addLast(intent);
        return RecordResult.RECORDED;
    }

    /** Binds valid contact evidence only to an intent still pending in this exact history. */
    public boolean recordNormalTarget(long sequence, NativeNormalContactPolicy.Target target) {
        if (target == null || !target.valid() || pending.stream().noneMatch(intent -> intent.sequence() == sequence))
            return false;
        PredictionEvidence existing = evidence.get(sequence);
        evidence.put(sequence, new PredictionEvidence(target, existing == null ? null : existing.angularSupport()));
        return true;
    }

    /** Returns evidence only while its exact intent remains pending. */
    public Optional<NativeNormalContactPolicy.Target> normalTarget(long sequence) {
        return pending.stream().anyMatch(intent -> intent.sequence() == sequence)
                ? Optional.ofNullable(evidence.get(sequence)).map(PredictionEvidence::normalTarget) : Optional.empty();
    }

    /** Captured angular support fact for this exact pending physical move; null frame means unsupported/world. */
    public boolean recordAngularEvidence(long sequence, AngularSupportEvidence evidence) {
        if (evidence == null || pending.stream().noneMatch(intent -> intent.sequence() == sequence)
                || this.evidence.containsKey(sequence) && this.evidence.get(sequence).angularSupport() != null) return false;
        PredictionEvidence existing = this.evidence.get(sequence);
        this.evidence.put(sequence, new PredictionEvidence(existing == null ? null : existing.normalTarget(), evidence));
        return true;
    }

    public Optional<AngularSupportEvidence> angularEvidence(long sequence) {
        return pending.stream().anyMatch(intent -> intent.sequence() == sequence)
                ? Optional.ofNullable(evidence.get(sequence)).map(PredictionEvidence::angularSupport) : Optional.empty();
    }

    public record AngularSupportEvidence(java.util.UUID frameId, boolean supported,
                                         com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3 omega) {
        public AngularSupportEvidence {
            Objects.requireNonNull(omega, "omega");
            if (supported ? frameId == null : frameId != null || !omega.equals(zeroOmega()))
                throw new IllegalArgumentException("angular support fact is inconsistent");
        }
    }

    private record PredictionEvidence(NativeNormalContactPolicy.Target normalTarget, AngularSupportEvidence angularSupport) { }

    private static com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3 zeroOmega() {
        return new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0, 0, 0);
    }

    /** Advances the wire sequence over an intentionally unpredicted input frame. */
    public RecordResult skip(GhostControlPayloads.Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.epoch() != epoch || intent.sequence() <= lastRecordedSequence)
            return RecordResult.REJECTED;
        lastRecordedSequence = intent.sequence();
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
        if (snapshot.epoch() != epoch || snapshot.harnessEntityId() != harnessEntityId
                || snapshot.harnessKind() != harnessKind
                || snapshot.serverGameTick() <= lastServerGameTick
                || acknowledged > lastRecordedSequence
                || acknowledged < lastAcknowledgedSequence)
            return SnapshotResult.rejected();

        lastServerGameTick = snapshot.serverGameTick();
        lastAcknowledgedSequence = acknowledged;
        while (!pending.isEmpty() && pending.peekFirst().sequence() <= acknowledged) {
            long removed = pending.removeFirst().sequence();
            evidence.remove(removed);
        }

        List<GhostControlPayloads.Intent> replay = List.copyOf(pending);
        if (!isCompleteReplay(replay, acknowledged)) {
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
        lastServerGameTick = -1;
    }

    /** Clears buffered inputs after an authority barrier without resetting this session's tracking. */
    public void onAuthorityBarrier() {
        onPredictionFailureBarrier();
    }

    /** Drops replay but preserves the last server acknowledgement so a post-failure correction can settle it. */
    public void onPredictionFailureBarrier() {
        pending.clear();
        evidence.clear();
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
    public int pendingCount() { return pending.size(); }

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
