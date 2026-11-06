package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Coupled model of custom snapshot replay and a later vanilla tracker write; not Minecraft packet execution. */
class AirborneVelocityReplayRegressionTest {
    private static final long EPOCH = 71;
    private static final int BODY = 29;

    @Test
    void jumpTakeoffReplayMustNotBeOverwrittenByStaleTrackerVelocity() throws Exception {
        assertScenario(true);
    }

    @Test
    void walkingOffEdgeReplayMustNotBeOverwrittenByStaleTrackerVelocity() throws Exception {
        assertScenario(false);
    }

    @Test
    void delayedAcknowledgementAcrossTakeoffAndLandingPreservesContinuousHistory() throws Exception {
        CharacterMovementPolicy policy = resolvedHumanPolicy();
        GroundedHarnessMotor motor = new GroundedHarnessMotor(policy);
        MovementCollisionResolver terrain = (position, requested, wasGrounded) -> {
            double y = position.y() + requested.y();
            if (y < 0d) y = 0d;
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(requested.x(), y - position.y(), requested.z()), y == 0d);
        };
        CharacterMovementEnvironment world = new CharacterMovementEnvironment(
                GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 0d, 0d, terrain);
        CharacterMovementState initial = new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, true);
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        List<GhostControlPayloads.Intent> frames = new ArrayList<>();
        frames.add(intent(1, 0, true, 0));
        for (int i = 2; i <= 30; i++) frames.add(intent(i, 0, false, 0));
        CharacterMovementState predicted = initial;
        List<CharacterMovementState> trajectory = new ArrayList<>();
        for (var frame : frames) {
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(frame));
            predicted = step(motor, world, predicted, frame);
            trajectory.add(predicted);
        }
        assertTrue(trajectory.get(0).velocity().y() > 0d, "first frame must take off");
        int landing = -1;
        for (int i = 1; i < trajectory.size(); i++) {
            if (!trajectory.get(i - 1).onGround() && trajectory.get(i).onGround()) { landing = i; break; }
        }
        assertTrue(landing > 0, "predicted jump must land");
        var takeoffAck = history.reconcile(snapshot(1, trajectory.get(0)));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, takeoffAck.status());
        assertFalse(trajectory.get(0).onGround(), "takeoff must be an observed onGround transition");
        CharacterMovementState replay = applySnapshot(snapshot(1, trajectory.get(0)));
        for (int i = 1; i < frames.size(); i++) {
            replay = step(motor, world, replay, frames.get(i));
            assertStateEquals(trajectory.get(i), replay);
        }
        assertTrue(trajectory.get(landing).onGround(), "landing must be the second onGround transition");
        System.out.println("DELAYED_TAKEOFF_LANDING takeoffSequence=1 takeoffY=" + trajectory.get(0).position().y()
                + " landingSequence=" + (landing + 1) + " landingY=" + trajectory.get(landing).position().y()
                + " onGroundTransitions=2 ackedThrough=" + (landing + 1));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshotWithAck(2, landing + 1, trajectory.get(landing))).status());
        assertEquals(frames.size() - landing - 1, history.pendingCount(), "landing ACK only settles through its frame");

        GhostPredictionHistory barrier = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        for (var frame : frames) barrier.record(frame);
        barrier.onAuthorityBarrier();
        assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST,
                barrier.reconcile(snapshot(1, trajectory.get(0))).status(),
                "old authority barrier must lose the suffix while the guarded transition remains replayable");
    }

    @Test
    void authoritativeRelocationSnapshotRebasesPoseBeforeReplayingPendingIntent() throws Exception {
        CharacterMovementPolicy policy = resolvedHumanPolicy();
        GroundedHarnessMotor motor = new GroundedHarnessMotor(policy);
        CharacterMovementEnvironment world = new CharacterMovementEnvironment(
                GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 0d, 0d,
                (position, requested, wasGrounded) -> new MovementCollisionResolver.CollisionResult(requested, false));
        var pending = intent(1, 1000, false, 0);
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        history.record(pending);
        var relocated = new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.CHARACTER, 10, 0,
                40, 12, -7, 0, 0, .25, -.5, .75, false, 1f, 1f, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, java.util.Optional.empty(),
                java.util.Optional.empty(), 1);
        var result = history.reconcile(relocated);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
        assertEquals(List.of(pending), result.replayFrames());
        CharacterMovementState actual = applySnapshot(relocated);
        for (var frame : result.replayFrames()) actual = step(motor, world, actual, frame);
        CharacterMovementState expected = step(motor, world,
                new CharacterMovementState(new MovementVector(40, 12, -7), new MovementVector(.25, -.5, .75), false), pending);
        assertStateEquals(expected, actual);
        assertNotEquals(0d, actual.position().x(), "replay must begin at the authoritative relocation, not old origin");
    }

    private static void assertScenario(boolean jump) throws Exception {
        CharacterMovementPolicy policy = resolvedHumanPolicy();
        GroundedHarnessMotor motor = new GroundedHarnessMotor(policy);
        MovementCollisionResolver terrain = (position, requested, wasGrounded) -> {
            double x = position.x() + requested.x();
            double y = position.y() + requested.y();
            if (x <= 0d && y < 0d && position.y() >= 0d) y = 0d; // upper platform
            if (y < -3d) y = -3d; // finite lower floor, including beyond the ledge
            boolean supported = y == 0d && x <= 0d || y == -3d;
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(requested.x(), y - position.y(), requested.z()), supported);
        };
        CharacterMovementEnvironment world = new CharacterMovementEnvironment(
                GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 0d, 0d, terrain);
        CharacterMovementState initial = new CharacterMovementState(
                new MovementVector(jump ? 0d : -.08d, 0d, 0d), MovementVector.ZERO, true);
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);

        // Client predicts two physical frames. Server has only applied frame K when its ACK arrives.
        GhostControlPayloads.Intent k = intent(1, jump ? 0 : 1000, jump, 0);
        GhostControlPayloads.Intent kPlusOne = intent(2, jump ? 0 : 1000, false, 0);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(k));
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(kPlusOne));
        CharacterMovementState authoritative = step(motor, world, initial, k);
        GhostControlPayloads.Snapshot ackK = snapshot(1, authoritative);
        GhostPredictionHistory.SnapshotResult result = history.reconcile(ackK);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
        assertEquals(List.of(kPlusOne), result.replayFrames());
        CharacterMovementState corrected = applySnapshot(ackK);
        for (GhostControlPayloads.Intent frame : result.replayFrames()) corrected = step(motor, world, corrected, frame);
        assertStateEquals(step(motor, world, applySnapshot(ackK), kPlusOne), corrected);
        assertFalse(corrected.onGround(), "replayed takeoff/edge frame must be airborne");
        if (jump) assertTrue(corrected.velocity().y() > 0d, "jump takeoff must be rising");
        else assertTrue(corrected.velocity().y() < 0d, "walking off edge must be falling");

        // Negative control: an unguarded vanilla packet write replaces ONLY velocity after custom replay.
        // The ordinary tracker packet for ACK K carries K's velocity, now stale against replayed K+1.
        CharacterMovementState corrupted = new CharacterMovementState(corrected.position(),
                authoritative.velocity(), corrected.onGround());
        CharacterMovementState preserved = corrected;
        CharacterMovementState reference = step(motor, world, step(motor, world, initial, k), kPlusOne);
        List<CharacterMovementState> negative = new ArrayList<>();
        List<CharacterMovementState> positive = new ArrayList<>();
        int positiveLandingTick = -1, negativeLandingTick = -1;
        for (int i = 0; i < 80; i++) {
            GhostControlPayloads.Intent neutral = intent(3 + i, 0, false, 0);
            corrupted = step(motor, world, corrupted, neutral);
            preserved = step(motor, world, preserved, neutral);
            reference = step(motor, world, reference, neutral);
            assertStateEquals(reference, preserved);
            negative.add(corrupted);
            positive.add(preserved);
            if (positiveLandingTick < 0 && preserved.onGround()) positiveLandingTick = i;
            if (negativeLandingTick < 0 && corrupted.onGround()) negativeLandingTick = i;
        }
        double maximumPositionDivergence = 0d, maximumVelocityDivergence = 0d;
        boolean positiveLand = false, negativeLand = false;
        for (int i = 0; i < positive.size(); i++) {
            CharacterMovementState good = positive.get(i), bad = negative.get(i);
            maximumPositionDivergence = Math.max(maximumPositionDivergence,
                    distance(good.position(), bad.position()));
            maximumVelocityDivergence = Math.max(maximumVelocityDivergence,
                    distance(good.velocity(), bad.velocity()));
            positiveLand |= good.onGround();
            negativeLand |= bad.onGround();
        }
        assertTrue(positiveLand && negativeLand, "both trajectories must reach the lower landing surface");
        String evidence = "maxPositionDivergence=" + maximumPositionDivergence + ", maxVelocityDivergence="
                + maximumVelocityDivergence + ", positiveLandingTick=" + positiveLandingTick
                + ", negativeLandingTick=" + negativeLandingTick;
        System.out.println((jump ? "JUMP" : "EDGE_FALL") + "_TRACKER_VELOCITY_REGRESSION " + evidence);
        assertTrue(maximumPositionDivergence > .1d, "stale velocity should cause meaningful positional divergence: " + evidence);
        assertTrue(maximumVelocityDivergence > 1d, "stale velocity should cause meaningful velocity divergence: " + evidence);
        assertNotEquals(positiveLandingTick, negativeLandingTick, "stale velocity should alter landing timing: " + evidence);
    }

    private static CharacterMovementState step(GroundedHarnessMotor motor, CharacterMovementEnvironment world,
                                                CharacterMovementState state, GhostControlPayloads.Intent intent) {
        return motor.tick(state, intent.wishX(), intent.wishZ(),
                (intent.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0, false, intent.yaw(), world, false);
    }

    private static GhostControlPayloads.Intent intent(long sequence, int wishX, boolean jump, int wishZ) {
        return new GhostControlPayloads.Intent(EPOCH, sequence, (short) wishX, (short) wishZ,
                (byte) 0, jump ? GhostControlPayloads.BUTTON_JUMP : 0, 0f, 0f);
    }

    private static GhostControlPayloads.Snapshot snapshot(long tick, CharacterMovementState state) {
        return new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.CHARACTER, tick, 1,
                state.position().x(), state.position().y(), state.position().z(), 0, 0,
                state.velocity().x(), state.velocity().y(), state.velocity().z(), state.onGround(),
                1f, 1f, false, 0, GhostControlPayloads.Snapshot.MovementMode.GROUNDED,
                java.util.Optional.empty(), java.util.Optional.empty(), 0);
    }

    private static GhostControlPayloads.Snapshot snapshotWithAck(long tick, long acknowledged,
                                                                  CharacterMovementState state) {
        return new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.CHARACTER, tick, acknowledged,
                state.position().x(), state.position().y(), state.position().z(), 0, 0,
                state.velocity().x(), state.velocity().y(), state.velocity().z(), state.onGround(),
                1f, 1f, false, 0, GhostControlPayloads.Snapshot.MovementMode.GROUNDED,
                java.util.Optional.empty(), java.util.Optional.empty(), 0);
    }

    private static CharacterMovementState applySnapshot(GhostControlPayloads.Snapshot snapshot) {
        return new CharacterMovementState(new MovementVector(snapshot.x(), snapshot.y(), snapshot.z()),
                new MovementVector(snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ()), snapshot.onGround());
    }

    private static CharacterMovementPolicy resolvedHumanPolicy() throws Exception {
        String resource = "/data/moonstation14/moonstation14/character/human.json";
        try (var stream = AirborneVelocityReplayRegressionTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("missing resolved CHARACTER definition " + resource);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return CharacterMovementPolicy.fromCharacterData(CharacterData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }

    private static double distance(MovementVector a, MovementVector b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    private static void assertStateEquals(CharacterMovementState expected, CharacterMovementState actual) {
        assertEquals(expected.position().x(), actual.position().x(), 1e-10);
        assertEquals(expected.position().y(), actual.position().y(), 1e-10);
        assertEquals(expected.position().z(), actual.position().z(), 1e-10);
        assertEquals(expected.velocity().x(), actual.velocity().x(), 1e-10);
        assertEquals(expected.velocity().y(), actual.velocity().y(), 1e-10);
        assertEquals(expected.velocity().z(), actual.velocity().z(), 1e-10);
        assertEquals(expected.onGround(), actual.onGround());
    }
}
