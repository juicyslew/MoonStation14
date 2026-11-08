package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostPredictionHistoryTest {
    private static final long EPOCH = 7;
    private static final int BODY = 23;

    @Test
    void applicationTickAssociationDoesNotLeakAcrossHeldReplayAndFollowsIntentLifecycle() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        var fact = new GhostPredictionHistory.AngularSupportEvidence(null, false,
                new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0, 0, 0));
        history.record(intent(1));
        assertEquals(intent(1), history.pendingIntent(1).orElseThrow());
        assertTrue(history.recordAngularEvidence(1, 10, fact));
        assertEquals(fact, history.angularEvidence(1, 10).orElseThrow());
        assertTrue(history.angularEvidence(1, 11).isEmpty());
        assertFalse(history.recordAngularEvidence(1, 11, fact));
        history.record(intent(2));
        history.reconcile(characterSnapshot(EPOCH, BODY, 2, 1));
        assertTrue(history.angularEvidence(1, 10).isEmpty());
        history.onAuthorityBarrier();
        assertTrue(history.angularEvidence(2, 10).isEmpty());
    }

    @Test
    void angularEvidenceSharesPendingIntentLifecycleAndPreservesRecordedZero() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        var zero = new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0, 0, 0);
        var fact = new GhostPredictionHistory.AngularSupportEvidence(null, false, zero);
        NativeNormalContactPolicy.Target target = NativeNormalContactPolicy.prescribe(
                new Vec3(1, 0, 0), Vec3.ZERO, new Vec3(1, 0, 0), 0);
        history.record(intent(1));
        assertTrue(history.recordAngularEvidence(1, fact));
        assertFalse(history.recordAngularEvidence(1, fact));
        assertFalse(history.recordAngularEvidence(99, fact));
        assertTrue(history.recordNormalTarget(1, target));
        assertEquals(fact, history.angularEvidence(1).orElseThrow());
        assertEquals(target, history.normalTarget(1).orElseThrow());
        history.record(intent(2));
        history.recordAngularEvidence(2, fact);
        assertTrue(history.recordNormalTarget(2, target));
        history.reconcile(characterSnapshot(EPOCH, BODY, 2, 1));
        assertTrue(history.angularEvidence(1).isEmpty());
        assertTrue(history.angularEvidence(2).isPresent());
        assertTrue(history.normalTarget(2).isPresent());
        history.onAuthorityBarrier();
        assertTrue(history.angularEvidence(2).isEmpty());
        assertTrue(history.normalTarget(2).isEmpty());
    }

    @Test
    void capturedDepartureSurvivesUnsettledPrejumpSnapshotsUntilJumpAck() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        for (long seq = 1; seq <= 8; seq++) history.record(intent(seq));
        history.record(intent(9));
        var captured = new GhostPredictionHistory.AngularSupportEvidence(java.util.UUID.randomUUID(), true,
                new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(-1.917993, 0, 0));
        assertTrue(history.recordAngularEvidence(8, captured));
        for (long tick = 1; tick <= 8; tick++) {
            var result = history.reconcile(characterSnapshot(EPOCH, BODY, tick, 7));
            assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
            assertEquals(captured, history.angularEvidence(8).orElseThrow());
        }
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(characterSnapshot(EPOCH, BODY, 9, 8)).status());
        assertTrue(history.angularEvidence(8).isEmpty());
    }

    @Test
    void normalContactEvidenceFollowsExactPendingIntentLifecycle() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        NativeNormalContactPolicy.Target evidence = NativeNormalContactPolicy.prescribe(
                new Vec3(1, 0, 0), Vec3.ZERO, new Vec3(1, 0, 0), 0);
        assertFalse(history.recordNormalTarget(1, evidence));
        history.record(intent(1)); history.record(intent(2));
        assertTrue(history.recordNormalTarget(1, evidence));
        assertTrue(history.recordNormalTarget(2, evidence));
        assertEquals(evidence, history.normalTarget(1).orElseThrow());
        assertFalse(history.recordNormalTarget(2, new NativeNormalContactPolicy.Target(null, null, Double.NaN)));
        assertFalse(history.recordNormalTarget(99, evidence));

        assertRejected(history, snapshot(EPOCH, BODY, 1, 3));
        assertEquals(evidence, history.normalTarget(1).orElseThrow());
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 2, 1)).status());
        assertTrue(history.normalTarget(1).isEmpty());
        assertEquals(evidence, history.normalTarget(2).orElse(null));

        history.onAuthorityBarrier();
        assertTrue(history.normalTarget(2).isEmpty());
        history.record(intent(3)); history.recordNormalTarget(3, evidence);
        history.skip(intent(4));
        assertTrue(history.normalTarget(3).isEmpty());
        history.record(intent(5)); history.recordNormalTarget(5, evidence);
        history.clear();
        assertTrue(history.normalTarget(5).isEmpty());
        history.record(intent(1)); history.recordNormalTarget(1, evidence);
        history.reset(EPOCH + 1, BODY);
        assertTrue(history.normalTarget(1).isEmpty());
    }

    @Test
    void contactEvidenceIsEvictedWithCapacityAndHistoryLoss() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        NativeNormalContactPolicy.Target evidence = NativeNormalContactPolicy.prescribe(
                new Vec3(1, 0, 0), Vec3.ZERO, new Vec3(1, 0, 0), 0);
        for (long sequence = 1; sequence <= GhostPredictionHistory.CAPACITY; sequence++) {
            history.record(intent(sequence));
            history.recordNormalTarget(sequence, evidence);
        }
        history.record(intent(GhostPredictionHistory.CAPACITY + 1));
        assertTrue(history.normalTarget(1).isEmpty());
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST,
                history.reconcile(snapshot(EPOCH, BODY, 1, 0)).status());
        assertEquals(evidence, history.normalTarget(2).orElseThrow());
    }

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
    void predictionFailureBarrierKeepsFailureUnacknowledgedUntilServerCorrection() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        history.onPredictionFailureBarrier();
        assertEquals(0, history.lastAcknowledgedSequence());
        assertEquals(0, history.pendingCount());
        var correction = history.reconcile(snapshot(EPOCH, BODY, 10, 1));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, correction.status());
        assertTrue(correction.replayFrames().isEmpty());
        assertEquals(1, history.lastAcknowledgedSequence());
    }

    @Test
    void authorityBarrierDoesNotCounterfeitServerAcknowledgement() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        history.onAuthorityBarrier();
        assertEquals(0, history.lastAcknowledgedSequence());
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST,
                history.reconcile(snapshot(EPOCH, BODY, 10, 0)).status());
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 11, 1)).status());
    }

    @Test
    void incompleteSnapshotRetainsSuffixUntilDelayedAckCoversGap() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        NativeNormalContactPolicy.Target target = NativeNormalContactPolicy.prescribe(
                new Vec3(1, 0, 0), Vec3.ZERO, new Vec3(1, 0, 0), 0);
        var angular = new GhostPredictionHistory.AngularSupportEvidence(java.util.UUID.randomUUID(), true,
                new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0, 2, 0));
        for (long sequence = 1; sequence <= 8; sequence++) history.record(intent(sequence));
        history.skip(intent(9));
        for (long sequence = 10; sequence <= 14; sequence++) history.record(intent(sequence));
        assertTrue(history.recordNormalTarget(10, target));
        assertTrue(history.recordAngularEvidence(10, angular));

        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST,
                history.reconcile(snapshot(EPOCH, BODY, 1, 0)).status());
        assertEquals(5, history.pendingCount());
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST,
                history.reconcile(snapshot(EPOCH, BODY, 2, 8)).status());
        assertEquals(5, history.pendingCount());
        assertEquals(target, history.normalTarget(10).orElseThrow());
        assertEquals(angular, history.angularEvidence(10).orElseThrow());
        assertRejected(history, snapshot(EPOCH, BODY, 2, 8));
        assertEquals(angular, history.angularEvidence(10).orElseThrow());
        for (long sequence = 15; sequence <= 17; sequence++) history.record(intent(sequence));
        var recovered = history.reconcile(snapshot(EPOCH, BODY, 3, 9));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, recovered.status());
        assertEquals(target, history.normalTarget(10).orElseThrow());
        assertEquals(angular, history.angularEvidence(10).orElseThrow());
        assertEquals(List.of(intent(10), intent(11), intent(12), intent(13), intent(14),
                intent(15), intent(16), intent(17)), recovered.replayFrames());
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
    void higherServerTickMayRepeatSameAcknowledgementForHeldInputTicks() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(1)));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 10, 1)).status());
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 11, 1)).status());
        assertEquals(1, history.lastAcknowledgedSequence(), "quiet server ticks do not fabricate an input sequence");
        assertEquals(0, history.pendingCount());
    }

    @Test
    void detectsSequenceGapThatWasNotAcknowledged() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(2));
        GhostPredictionHistory.SnapshotResult result = history.reconcile(snapshot(EPOCH, BODY, 1, 0));
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST, result.status());
        assertEquals(1, history.pendingCount());
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
        assertEquals(GhostPredictionHistory.CAPACITY, history.pendingCount());
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
    void suspendedInputSequenceProgressResumesWithoutReplayingPreBarrierFrames() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        for (int sequence = 1; sequence <= 10; sequence++)
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.skip(intent(sequence)));
        history.onAuthorityBarrier();
        assertEquals(0, history.pendingCount());
        assertEquals(EPOCH, history.epoch());
        assertEquals(BODY, history.ghostEntityId());
        assertEquals(10, history.lastRecordedSequence());
        assertEquals(0, history.lastAcknowledgedSequence());
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(EPOCH, BODY, 11, 10)).status());
        GhostControlPayloads.Intent resumed = intent(11);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(resumed));
        GhostPredictionHistory.SnapshotResult replay = history.reconcile(snapshot(EPOCH, BODY, 12, 10));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, replay.status());
        assertEquals(List.of(resumed), replay.replayFrames());
        assertTrue(replay.replayFrames().stream().noneMatch(frame -> frame.sequence() <= 10));
    }

    @Test
    void intentSchemaRejectsNonfiniteValuesBeforeHistoryCanStoreThem() {
        assertThrows(IllegalArgumentException.class, () -> new GhostControlPayloads.Intent(
                EPOCH, 1, 0, (short) 0, (short) 0, (byte) 0, 0, Float.NaN, 0));
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(intent(1));
        assertFalse(history.reconcile(snapshot(EPOCH, BODY, 0, 0)).replayFrames().isEmpty());
    }

    @Test
    void replayPlansOneFixedStepPerTickAndFoldsAllDueCommands() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        GhostControlPayloads.Intent first = tickIntent(1, 11, GhostControlPayloads.BUTTON_JUMP, 0);
        GhostControlPayloads.Intent release = tickIntent(2, 11, 0, 4);
        history.record(first); history.record(release);
        GhostControlPayloads.Snapshot base = snapshot(EPOCH, BODY, 10, 0);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, history.reconcile(base).status());
        var result = history.planReplayTicks(base, 13);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
        assertEquals(3, result.ticks().size());
        assertEquals(2, result.ticks().get(0).sequence());
        assertTrue(result.ticks().get(0).receivedInput());
        assertTrue((result.ticks().get(0).effectiveState().buttons() & GhostControlPayloads.BUTTON_JUMP) != 0);
        assertEquals(result.ticks(), history.planReplayTicks(base, 13).ticks());
        assertEquals(2, history.pendingCount());
    }

    @Test void settledHeldControlsUseAppliedReferenceAndNeverForgeSkippedSequence() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        var frame = java.util.UUID.randomUUID();
        var reference = new GhostControlPayloads.SupportedMovementReference(frame, 42f);
        var applied = new GhostControlPayloads.Intent(EPOCH, 1, 1, (short) 100, (short) 0, (byte) 0,
                0, 0, 0, java.util.Optional.of(reference));
        history.record(applied);
        history.record(tickIntent(2, 2, 0, 0));
        var anchor = characterSnapshot(EPOCH, BODY, 10, 1).withInputSettlement(2)
                .withControlState(new com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState(
                                (short) 100, (short) 0, (byte) 0, 0, 0, 0),
                        com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, history.reconcile(anchor).status());
        assertEquals(java.util.Optional.of(reference), history.appliedSupportedMovementReference(1));
        var ticks = history.planReplayTicks(anchor, 12).ticks();
        assertEquals(1, ticks.get(0).sequence()); // actual applied ACK, not settled-through 2
        assertEquals(11, ticks.get(0).logicalTick());
        assertEquals(java.util.Optional.empty(), history.pendingIntent(ticks.get(0).sequence()));
    }

    @Test
    void replayPlanCapsTicksRetainsFutureCommandsAndHeldState() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        history.record(tickIntent(1, 200, 0, 0));
        var anchor = snapshot(EPOCH, BODY, 100, 0);
        history.reconcile(anchor);
        var result = history.planReplayTicks(anchor, Long.MAX_VALUE);
        assertEquals(GhostPredictionHistory.MAX_REPLAY_TICKS, result.ticks().size());
        assertEquals(0, result.ticks().get(0).sequence());
        assertTrue(result.ticks().get(0).receivedInput());
        assertEquals(1, history.pendingCount());
    }

    @Test
    void rejectsDecreasingIntendedTicksButAllowsEqualTicks() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(tickIntent(1, 4, 0, 0)));
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(tickIntent(2, 4, 0, 0)));
        assertEquals(GhostPredictionHistory.RecordResult.REJECTED, history.record(tickIntent(3, 3, 0, 0)));
        history.onAuthorityBarrier();
        assertEquals(4, history.lastRecordedIntendedTick());
    }

    private static GhostControlPayloads.Intent tickIntent(long sequence, long tick, int buttons, int wishX) {
        return new GhostControlPayloads.Intent(EPOCH, sequence, tick, (short) wishX, (short) 0, (byte) 0,
                buttons, 0f, 0f);
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

    private static GhostControlPayloads.Snapshot characterSnapshot(long epoch, int body, long tick, long acknowledged) {
        return new GhostControlPayloads.Snapshot(epoch, body, MobHarnessKind.CHARACTER, tick, acknowledged,
                0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, false);
    }
}
