package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.animals.flight.FlightState;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic wall-time traces through the production prediction clock and replay planner. */
class GhostPredictionLoadTimelineTest {
    private static final long EPOCH = 41;
    private static final int BODY = 9;
    private static final long MS = 1_000_000L;
    private static final GhostControlState FORWARD = new GhostControlState((short) 1000, (short) 0,
            (byte) 0, 0, 0, 0);

    @Test
    void twentyHertzCallbacksUseActualFiftyMillisecondWallIntervals() {
        Timeline timeline = new Timeline();
        timeline.accept(snapshot(0, 0), 0);
        for (int callback = 1; callback <= 8; callback++) {
            long now = callback * 50 * MS;
            timeline.record(callback, callback);
            assertEquals(callback, timeline.target(now));
            assertEquals(callback, timeline.plan(now).size());
        }
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                timeline.plan(400 * MS).stream().map(GhostControlInputAccumulator.TickInput::logicalTick).toList());
    }

    @Test
    void clusteredFullStateCommandsFoldBeforeOneTickAndRebuildIsIdempotent() {
        Timeline timeline = new Timeline();
        timeline.accept(snapshot(20, 0), 0);
        timeline.record(1, 21);
        timeline.record(2, 21);
        timeline.record(3, 21);
        var first = timeline.plan(60 * MS);
        var repeated = timeline.plan(60 * MS);
        assertEquals(List.of(21L), first.stream().map(GhostControlInputAccumulator.TickInput::logicalTick).toList());
        assertEquals(first, repeated);
        assertEquals(3, first.get(0).sequence()); // all due states fold, one simulation step
        assertEquals(3, timeline.history.pendingCount());
    }

    @Test
    void stalledClientAdvancesElapsedTicksRatherThanCallbackCount() {
        Timeline timeline = new Timeline();
        timeline.accept(snapshot(5, 0), 0);
        timeline.record(1, 6);
        // A single callback after a 200ms frame stall is four elapsed fixed steps.
        assertEquals(9, timeline.target(200 * MS));
        var plan = timeline.plan(200 * MS);
        assertEquals(List.of(6L, 7L, 8L, 9L), plan.stream()
                .map(GhostControlInputAccumulator.TickInput::logicalTick).toList());
        assertEquals(4, plan.size());
    }

    @Test
    void newerSnapshotReanchorsAndSettledAckDoesNotDoubleCountHeldTime() {
        Timeline timeline = new Timeline();
        timeline.accept(snapshot(100, 0), 0);
        timeline.record(1, 101);
        timeline.accept(snapshot(101, 1), 101 * MS); // independently settles seq 1
        assertEquals(0, timeline.history.pendingCount());
        assertEquals(101, timeline.target(101 * MS)); // anchor wall time is replaced: no stale elapsed carry
        assertEquals(0, timeline.plan(101 * MS).size());
        assertEquals(FORWARD, timeline.base.heldControlState());
        assertEquals(105, timeline.target(301 * MS));
        assertEquals(4, timeline.plan(301 * MS).size());
    }

    @Test
    void liveLeadAndRepeatedSameInstantCallbacksDoNotDependOnCallbackCount() {
        Timeline oneTickLead = new Timeline(1);
        oneTickLead.accept(snapshot(10, 0), 0);
        assertEquals(11, oneTickLead.target(0));
        assertEquals(12, oneTickLead.target(50 * MS));
        var oneTickPlan = oneTickLead.plan(50 * MS);
        assertEquals(List.of(11L, 12L), oneTickPlan.stream()
                .map(GhostControlInputAccumulator.TickInput::logicalTick).toList());
        assertEquals(oneTickPlan, oneTickLead.plan(50 * MS));

        // Direct clock lead represents a nonzero RTT-derived lead value; it is not a latency-protocol claim.
        Timeline threeTickLead = new Timeline(3);
        threeTickLead.accept(snapshot(10, 0), 0);
        assertEquals(13, threeTickLead.target(0));
        assertEquals(14, threeTickLead.target(50 * MS));
        assertEquals(4, threeTickLead.plan(50 * MS).size());
    }

    @Test
    void snapshotsClusteredAtSameWallTimeSettleAckAndReanchorToLatestTick() {
        Timeline timeline = new Timeline(1);
        timeline.accept(snapshot(40, 0), 0);
        timeline.record(1, 41);
        timeline.record(2, 42);
        timeline.accept(snapshot(41, 1), 100 * MS);
        timeline.accept(snapshot(42, 2), 100 * MS);
        assertEquals(2, timeline.history.lastAcknowledgedSequence());
        assertEquals(0, timeline.history.pendingCount());
        assertEquals(43, timeline.target(100 * MS));
        assertEquals(1, timeline.plan(100 * MS).size());

        // The next future transition starts after the last recorded tick, even after ACK emptied history.
        timeline.record(3, 44);
        assertEquals(44, timeline.history.lastRecordedIntendedTick());
        assertEquals(FORWARD, timeline.base.heldControlState());
        assertEquals(43, timeline.target(100 * MS));
        var at43 = timeline.plan(100 * MS);
        assertEquals(1, at43.size());
        assertEquals(2, at43.get(0).sequence());
        assertEquals(44, timeline.target(150 * MS));
        assertEquals(3, timeline.plan(150 * MS).get(1).sequence());
    }

    @Test
    void fifteenTpsSnapshotsCanReanchorBelowTwentyHertzTargetWithoutClaimingParity() {
        Timeline timeline = new Timeline();
        timeline.accept(snapshot(0, 0), 0);
        timeline.accept(snapshot(1, 0), 66_666_667L); // server at 15 TPS; snapshot tick is authoritative
        assertEquals(1, timeline.target(66_666_667L));
        assertEquals(1, timeline.target(100_000_000L)); // reanchor is below the un-reanchored 20Hz target of tick 2
        assertEquals(2, timeline.target(116_666_667L));
        assertEquals(3, timeline.target(166_666_667L));
        // This records current local policy: latest snapshot tick + elapsed 50ms quanta,
        // not proof that the SS14 processed-tick-difference buffer rule is equivalent.
        assertEquals(50 * MS, GhostPredictionClock.NANOS_PER_TICK);
    }

    @Test
    void sustainedFifteenTpsSnapshotsInterleavedWithTwentyHertzCallbacks() {
        Timeline timeline = new Timeline(1);
        timeline.accept(snapshot(0, 0), 0);
        long[] callbackTargets = new long[20];
        int callback = 0;
        for (long callbackNanos = 50 * MS; callbackNanos <= 1_000 * MS; callbackNanos += 50 * MS) {
            long serverTickAtCallback = callbackNanos * 15 / (1_000 * MS);
            long snapshotNanos = serverTickAtCallback * 1_000 * MS / 15;
            // Deliver each server snapshot that arrived since the prior client callback.
            while (timeline.base.serverGameTick() < serverTickAtCallback)
                timeline.accept(snapshot(timeline.base.serverGameTick() + 1, 0), snapshotNanos);
            callbackTargets[callback++] = timeline.target(callbackNanos);
        }
        assertEquals(List.of(2L, 2L, 3L, 4L, 5L, 5L, 6L, 7L, 8L, 8L,
                        9L, 10L, 11L, 11L, 12L, 13L, 14L, 14L, 15L, 16L),
                java.util.Arrays.stream(callbackTargets).boxed().toList());
        assertEquals(16, timeline.target(1_000 * MS));
    }

    private static GhostControlPayloads.Snapshot snapshot(long serverTick, long acknowledged) {
        // Uniform straight-line world positions and velocity; no external collision/motor is introduced here.
        return new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.GHOST, serverTick, acknowledged,
                serverTick * 0.1, 64, 0, 0, 0, 2, 0, 0, true, 1, 1, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, Optional.empty(), FORWARD, FlightState.GROUNDED);
    }

    private static final class Timeline {
        private final GhostPredictionClock clock = new GhostPredictionClock();
        private final GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY);
        private final int leadTicks;
        private GhostControlPayloads.Snapshot base;

        Timeline() { this(0); }
        Timeline(int leadTicks) { this.leadTicks = leadTicks; }

        void accept(GhostControlPayloads.Snapshot snapshot, long receiptNanos) {
            assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, history.reconcile(snapshot).status());
            base = snapshot;
            assertTrue(clock.anchor(snapshot.serverGameTick(), receiptNanos));
        }

        void record(long sequence, long intendedTick) {
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(new GhostControlPayloads.Intent(
                    EPOCH, sequence, intendedTick, FORWARD.wishX(), FORWARD.wishZ(), FORWARD.verticalWish(),
                    FORWARD.buttons(), FORWARD.yaw(), FORWARD.pitch())));
        }

        long target(long nowNanos) { return clock.targetTick(nowNanos, leadTicks); }
        List<GhostControlInputAccumulator.TickInput> plan(long nowNanos) {
            return history.planReplayTicks(base, target(nowNanos)).ticks();
        }
    }
}
