package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostPredictionHistoryTest {
    private static final long EPOCH = 7;
    private static final int BODY = 23;

    @Test
    void acknowledgesAndReturnsOrderedImmutableReplayFrames() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        GhostControlPayloads.Intent first = intent(1);
        GhostControlPayloads.Intent second = intent(2);
        GhostControlPayloads.Intent third = intent(3);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(first));
        history.record(second);
        history.record(third);

        GhostPredictionHistory.SnapshotResult result = history.reconcile(snapshot(EPOCH, BODY, 10, 1));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
        assertEquals(List.of(second, third), result.replayFrames());
        assertThrows(UnsupportedOperationException.class, () -> result.replayFrames().clear());
        assertEquals(2, history.pendingCount());
    }

    @Test
    void acceptsServerSkippedSequencesWhenAcknowledgementCoversThem() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        GhostControlPayloads.Intent third = intent(3);
        history.record(third);

        GhostPredictionHistory.SnapshotResult result = history.reconcile(snapshot(EPOCH, BODY, 1, 2));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
        assertEquals(List.of(third), result.replayFrames());
    }

    @Test
    void settledThroughAcknowledgementDiscardsSkippedIntentsAndReplaysOnlyNewerFrames() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        List<GhostControlPayloads.Intent> intents = List.of(intent(1), intent(2), intent(3), intent(4), intent(5));
        intents.forEach(history::record);

        // Authoritative x=14 represents only the accepted seq1 + seq4 updates; seq2 and seq3 were skipped.
        GhostControlPayloads.Snapshot authoritative = snapshot(EPOCH, BODY, 2, 4, 14, 0, 0);
        assertEquals(14, authoritative.x());
        GhostPredictionHistory.SnapshotResult result = history.reconcile(authoritative);

        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
        assertEquals(List.of(intents.get(4)), result.replayFrames());
        assertTrue(result.replayFrames().stream().noneMatch(frame -> frame.sequence() == 2 || frame.sequence() == 3));
        assertEquals(1, history.pendingCount());
    }

    @Test
    void detectsSequenceGapThatWasNotAcknowledged() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(2));
        GhostPredictionHistory.SnapshotResult result = history.reconcile(snapshot(EPOCH, BODY, 1, 0));
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST, result.status());
        assertEquals(0, history.pendingCount());
    }

    @Test
    void rejectsDuplicateRegressionAndMismatchedRecordings() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(3)));
        assertEquals(GhostPredictionHistory.RecordResult.REJECTED, history.record(intent(3)));
        assertEquals(GhostPredictionHistory.RecordResult.REJECTED, history.record(intent(2)));
        assertEquals(GhostPredictionHistory.RecordResult.REJECTED, history.record(intent(EPOCH + 1, 4)));
        assertEquals(1, history.pendingCount());
        assertEquals(3, history.lastRecordedSequence());
    }

    @Test
    void rejectsMismatchedFutureRegressingAndOutOfOrderSnapshotsWithoutMutation() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        assertRejected(history, snapshot(EPOCH + 1, BODY, 1, 0));
        assertRejected(history, snapshot(EPOCH, BODY + 1, 1, 0));
        assertRejected(history, new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.CHARACTER,
                1, 0, 0, 0, 0, 0f, 0f, 0, 0, 0, false, 1, 1, false));
        assertRejected(history, snapshot(EPOCH, BODY, 1, 2));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 1, 0)).status());
        assertRejected(history, snapshot(EPOCH, BODY, 1, 0)); // same tick
        assertEquals(1, history.pendingCount());
        assertEquals(0, history.lastAcknowledgedSequence());

        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 2, 1)).status());
        history.record(intent(2));
        assertRejected(history, snapshot(EPOCH, BODY, 3, 0)); // ack regression
        assertEquals(1, history.pendingCount());
        assertEquals(1, history.lastAcknowledgedSequence());
    }

    @Test
    void legacyGhostSessionContinuesToAcceptGhostSnapshotsOnly() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        assertEquals(MobHarnessKind.GHOST, history.harnessKind());
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 1, 0)).status());
        assertRejected(history, new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.CHARACTER,
                2, 1, 0, 0, 0, 0f, 0f, 0, 0, 0, false, 1, 1, false));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 2, 0)).status());
    }

    @Test
    void allowsIdleSnapshotsWithRepeatedAcknowledgementOnlyAtNewTick() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 4, 0)).status());
        assertRejected(history, snapshot(EPOCH, BODY, 4, 0));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 5, 0)).status());
    }

    @Test
    void overflowFallsBackInsteadOfReplayingIncompleteHistory() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        for (long sequence = 1; sequence <= GhostPredictionHistory.CAPACITY + 1; sequence++)
            history.record(intent(sequence));
        assertEquals(GhostPredictionHistory.CAPACITY, history.pendingCount());

        GhostPredictionHistory.SnapshotResult result = history.reconcile(snapshot(EPOCH, BODY, 1, 0));
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST, result.status());
        assertTrue(result.replayFrames().isEmpty());
        assertEquals(0, history.pendingCount());
    }

    @Test
    void clearAndResetStartFreshHistoryAndIdentity() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        history.clear();
        assertEquals(0, history.pendingCount());
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(1)));

        history.reset(EPOCH + 1, BODY + 1);
        assertEquals(EPOCH + 1, history.epoch());
        assertEquals(BODY + 1, history.ghostEntityId());
        assertEquals(0, history.pendingCount());
        assertEquals(GhostPredictionHistory.RecordResult.REJECTED, history.record(intent(1)));
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(EPOCH + 1, 1)));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH + 1, BODY + 1, 0, 0)).status());
    }

    @Test
    void authorityBarrierClearsPendingWithoutResettingSessionTracking() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        history.record(intent(2));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 10, 1)).status());

        history.onAuthorityBarrier();
        assertEquals(0, history.pendingCount());
        assertEquals(EPOCH, history.epoch());
        assertEquals(BODY, history.ghostEntityId());
        assertEquals(2, history.lastRecordedSequence());
        assertEquals(1, history.lastAcknowledgedSequence());
        assertRejected(history, snapshot(EPOCH, BODY, 10, 1)); // server tick was not reset

        GhostPredictionHistory.SnapshotResult stale = history.reconcile(snapshot(EPOCH, BODY, 11, 1));
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST, stale.status());
        assertTrue(stale.replayFrames().isEmpty());
        assertEquals(0, history.pendingCount());

        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(3)));
        assertEquals(3, history.lastRecordedSequence());
        GhostPredictionHistory.SnapshotResult caughtUp = history.reconcile(snapshot(EPOCH, BODY, 12, 3));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, caughtUp.status());
        assertTrue(caughtUp.replayFrames().isEmpty());
    }

    @Test
    void intentSchemaRejectsNonfiniteValuesBeforeHistoryCanStoreThem() {
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Intent(
                EPOCH, 1, (short) 0, (short) 0, (byte) 0, 0, Float.NaN, 0));
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        assertFalse(history.reconcile(snapshot(EPOCH, BODY, 0, 0)).replayFrames().isEmpty());
    }

    private static void assertRejected(GhostPredictionHistory history, GhostControlPayloads.Snapshot snapshot) {
        assertEquals(GhostPredictionHistory.SnapshotStatus.REJECTED, history.reconcile(snapshot).status());
    }

    private static GhostControlPayloads.Intent intent(long sequence) {
        return intent(EPOCH, sequence);
    }

    private static GhostControlPayloads.Intent intent(long epoch, long sequence) {
        return new GhostControlPayloads.Intent(epoch, sequence, (short) 125, (short) -250,
                (byte) 1, GhostControlPayloads.BUTTON_JUMP, 30f, -5f);
    }

    private static GhostControlPayloads.Snapshot snapshot(long epoch, int body, long tick, long acknowledged) {
        return snapshot(epoch, body, tick, acknowledged, 0, 0, 0);
    }

    private static GhostControlPayloads.Snapshot snapshot(long epoch, int body, long tick, long acknowledged,
                                                          double x, double y, double z) {
        return new GhostControlPayloads.Snapshot(epoch, body, tick, acknowledged, x, y, z, 0, 0);
    }
}
