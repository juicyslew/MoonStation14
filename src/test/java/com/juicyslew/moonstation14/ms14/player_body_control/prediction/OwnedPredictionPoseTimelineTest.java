package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.animals.flight.FlightState;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Composes the production clock, history, one-shot schedule, and pure ghost motor for pose timelines. */
class OwnedPredictionPoseTimelineTest {
    private static final long EPOCH = 52;
    private static final int BODY = 17;
    private static final long MS = 1_000_000L;
    private static final GhostControlState FORWARD = new GhostControlState((short) 0, (short) 1000,
            (byte) 0, 0, 0, 0);

    @Test
    void snapshotAt228MsAppliesOneRebuiltTickAt247AndNextAt303WithoutInputDispatch() {
        PoseTimeline timeline = new PoseTimeline();
        timeline.record(1, 4407);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, timeline.accept(snapshot(4406, 0), 228 * MS));

        MovementVector beforeArrival = timeline.current;
        assertEquals(beforeArrival, timeline.current); // packet callback caches authority; it never changes pose
        assertEquals(4407, timeline.target(247 * MS));

        timeline.clientTick(247 * MS); // vanilla copied current->previous; apply authority, then one replay step
        assertEquals(beforeArrival, timeline.previous);
        assertEquals(4406 * 0.1, timeline.basePosition().x(), 1e-9);
        assertTrue(timeline.current.z() > timeline.basePosition().z());
        assertEquals(1, timeline.appliedTickCount);
        MovementVector firstPose = timeline.current;

        timeline.clientTick(247 * MS); // same wall instant, no second scheduled snapshot and no accumulated replay
        assertEquals(firstPose, timeline.current);
        assertEquals(1, timeline.appliedTickCount);

        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                timeline.accept(snapshot(4407, 1), 280 * MS)); // ACK settles seq 1; snapshot held state remains forward
        assertEquals(0, timeline.history.pendingCount());
        assertEquals(firstPose, timeline.current); // .280 packet still cannot rewrite the presented pose
        assertEquals(4408, timeline.target(303 * MS));
        timeline.clientTick(303 * MS); // no new input dispatch: held authoritative controls produce next fixed step
        assertEquals(firstPose, timeline.previous);
        assertTrue(timeline.current.z() > firstPose.z());
        assertEquals(2, timeline.appliedTickCount);
        assertEquals(FORWARD, timeline.base.heldControlState());
    }

    @Test
    void historyLossAndStoppedReplayApplyAuthorityWithoutRunningMotor() {
        PoseTimeline timeline = new PoseTimeline();
        timeline.record(1, 4407);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, timeline.accept(snapshot(4406, 0), 228 * MS));
        timeline.schedule.defer(true, true); // production schedule gates a stopped prediction session
        double authoritativeX = timeline.basePosition().x();
        timeline.clientTick(247 * MS);
        assertEquals(authoritativeX, timeline.current.x(), 1e-9);
        assertEquals(0, timeline.appliedTickCount);

        PoseTimeline historyLost = new PoseTimeline();
        historyLost.record(2, 4407); // missing sequence 1 makes the actual production replay check fail
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST,
                historyLost.accept(snapshot(4406, 0), 228 * MS));
        historyLost.clientTick(247 * MS);
        assertEquals(4406 * 0.1, historyLost.current.x(), 1e-9);
        assertEquals(0, historyLost.appliedTickCount);
        assertEquals(OwnedPredictionApplicationSchedule.Work.NONE, historyLost.schedule.take());
    }

    private static GhostControlPayloads.Intent intent(long sequence, long intendedTick) {
        return new GhostControlPayloads.Intent(EPOCH, sequence, intendedTick,
                FORWARD.wishX(), FORWARD.wishZ(), FORWARD.verticalWish(), FORWARD.buttons(), FORWARD.yaw(), FORWARD.pitch());
    }

    private static GhostControlPayloads.Snapshot snapshot(long tick, long acknowledged) {
        return new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.GHOST, tick, acknowledged,
                440.6, 64, (tick - 4406) * GhostMovementMotor.WALK_SPEED_PER_SECOND * 0.05,
                0, 0, GhostMovementMotor.WALK_SPEED_PER_SECOND, 0, 0, false, 1, 1, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, Optional.empty(), FORWARD, FlightState.GROUNDED);
    }

    private static final class PoseTimeline {
        private final GhostPredictionClock clock = new GhostPredictionClock();
        private final GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        private final OwnedPredictionApplicationSchedule schedule = new OwnedPredictionApplicationSchedule();
        private final GhostMovementMotor motor = new GhostMovementMotor();
        private final MovementCollisionResolver unobstructed = (position, displacement, wasOnGround) ->
                new MovementCollisionResolver.CollisionResult(displacement, false);
        private GhostControlPayloads.Snapshot base;
        private MovementVector current = new MovementVector(440.5, 64, 0);
        private MovementVector previous = current;
        private int appliedTickCount;

        void record(long sequence, long intendedTick) {
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(sequence, intendedTick)));
        }

        GhostPredictionHistory.SnapshotStatus accept(GhostControlPayloads.Snapshot snapshot, long receiptNanos) {
            GhostPredictionHistory.SnapshotResult result = history.reconcile(snapshot);
            base = snapshot;
            assertTrue(clock.anchor(snapshot.serverGameTick(), receiptNanos));
            schedule.defer(result.status() == GhostPredictionHistory.SnapshotStatus.REPLAY, false);
            return result.status();
        }

        long target(long nowNanos) { return clock.targetTick(nowNanos, 1); }
        MovementVector basePosition() { return new MovementVector(base.x(), base.y(), base.z()); }

        void clientTick(long nowNanos) {
            previous = current; // mirrors the normal client pose-copy boundary before post-tick work
            OwnedPredictionApplicationSchedule.Work work = schedule.take();
            if (work == OwnedPredictionApplicationSchedule.Work.NONE) return;
            current = basePosition(); // reset from authoritative base before every rebuild
            if (work != OwnedPredictionApplicationSchedule.Work.APPLY_AND_ALLOW_REPLAY) return;
            var plan = history.planReplayTicks(base, target(nowNanos));
            assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, plan.status());
            for (GhostControlInputAccumulator.TickInput input : plan.ticks()) {
                GhostControlState state = input.effectiveState();
                CharacterMovementState moved = motor.tick(current, state.wishX(), state.wishZ(), state.verticalWish(),
                        (state.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, state.yaw(), unobstructed);
                current = moved.position();
                appliedTickCount++;
            }
        }
    }
}
