package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, session-local history of quantized ghost inputs for future prediction replay. */
public final class GhostPredictionHistory {
    public static final int CAPACITY = 64;

    private final ArrayDeque<GhostControlPayloads.Intent> pending = new ArrayDeque<>(CAPACITY);
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
        if (pending.size() == CAPACITY)
            pending.removeFirst();
        pending.addLast(intent);
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
        while (!pending.isEmpty() && pending.peekFirst().sequence() <= acknowledged)
            pending.removeFirst();

        List<GhostControlPayloads.Intent> replay = List.copyOf(pending);
        if (!isCompleteReplay(replay, acknowledged)) {
            pending.clear();
            return new SnapshotResult(SnapshotStatus.HISTORY_LOST, List.of());
        }
        return new SnapshotResult(SnapshotStatus.REPLAY, replay);
    }

    /** Clears buffered inputs and sequence/tick tracking while retaining this session identity. */
    public void clear() {
        pending.clear();
        lastRecordedSequence = 0;
        lastAcknowledgedSequence = 0;
        lastServerGameTick = -1;
    }

    /** Clears buffered inputs after an authority barrier without resetting this session's tracking. */
    public void onAuthorityBarrier() {
        pending.clear();
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
