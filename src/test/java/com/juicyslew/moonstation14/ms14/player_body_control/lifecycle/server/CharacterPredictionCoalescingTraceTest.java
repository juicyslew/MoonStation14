package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Coupled client-history/server-FIFO trace for full-tick CHARACTER predictions. */
class CharacterPredictionCoalescingTraceTest {
    private static final long EPOCH = 17;
    private static final int BODY = 29;
    private static final MovementCollisionResolver FLAT_WORLD = (position, displacement, wasGrounded) ->
            new MovementCollisionResolver.CollisionResult(displacement, true);

    @Test
    void queuedServerStepsAndRealAckReplayHaveNoArtificialBackwardsCorrection() throws Exception {
        CharacterMovementPolicy policy = resolvedHumanPolicy();
        GroundedHarnessMotor motor = new GroundedHarnessMotor(policy);
        CharacterMovementEnvironment world = new CharacterMovementEnvironment(
                GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 0d, 0d, FLAT_WORLD);
        CharacterMovementState initial = new CharacterMovementState(MovementVector.ZERO,
                new MovementVector(0d, 0d, policy.walkSpeedPerSecond()), true);

        Trace steady = steady(motor, world, initial);
        Trace burst = burstThenQuiet(motor, world, initial);
        String trace = "steady=" + steady.events + "; burst/quiet=" + burst.events;
        System.out.println("CHARACTER_FIFO_PREDICTION_TRACE " + trace);

        assertEquals(0d, steady.maximumBackwardCorrection, 1e-12, trace);
        assertEquals(0d, burst.maximumBackwardCorrection, 1e-12, trace);
        assertEquals(List.of(0, 0, 0), steady.replayCounts);
        assertEquals(List.of(1, 0, 0), burst.replayCounts,
                "ACK1 keeps seq2 available for actual motor replay; ACK2 follows its later dequeue");
        assertEquals(List.of(1L, 2L, 2L), burst.acknowledgements);
        assertEquals(steady.finalServerPosition, burst.finalServerPosition, 1e-12,
                "same three physical ticks and full-tick input stream produce same server state");
    }

    private static Trace steady(GroundedHarnessMotor motor, CharacterMovementEnvironment world,
                                CharacterMovementState initial) {
        GhostPredictionHistory history = history();
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        CharacterMovementInputState held = new CharacterMovementInputState();
        CharacterMovementState client = initial, server = initial;
        List<String> events = new ArrayList<>();
        List<Integer> replays = new ArrayList<>();
        List<Long> acks = new ArrayList<>();
        double maxBackward = 0;

        for (int sequence = 1; sequence <= 2; sequence++) {
            GhostControlPayloads.Intent intent = forward(sequence);
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent));
            client = step(motor, world, client, intent);
            assertTrue(gate.offerQueued(sequence, intent, GhostPredictionHistory.CAPACITY));
            GhostControlPayloads.Intent dequeued = gate.takeQueued();
            assertEquals(sequence, dequeued.sequence());
            held.accept(dequeued);
            server = step(motor, world, server, held.held());
            held.afterStep();
            SnapshotApplication correction = reconcile(history, motor, world, client,
                    snapshot(sequence, sequence, server));
            client = correction.state;
            events.add("tick" + sequence + ":pred=" + correction.beforeZ + " auth=" + server.position().z()
                    + " ack=" + sequence + " replay=" + correction.replayCount + " after=" + client.position().z()
                    + " back=" + correction.backwardCorrection);
            maxBackward = Math.max(maxBackward, correction.backwardCorrection);
            replays.add(correction.replayCount);
            acks.add((long) sequence);
        }
        // Quiet server tick runs held movement. Same ACK is valid; it cannot cause backward correction.
        server = step(motor, world, server, held.held());
        SnapshotApplication quiet = reconcile(history, motor, world, client, snapshot(3, 2, server));
        events.add("tick3:pred=" + quiet.beforeZ + " auth=" + server.position().z()
                + " ack=2 replay=" + quiet.replayCount + " after=" + quiet.state.position().z()
                + " back=" + quiet.backwardCorrection);
        maxBackward = Math.max(maxBackward, quiet.backwardCorrection);
        replays.add(quiet.replayCount);
        acks.add(2L);
        return new Trace(events, maxBackward, replays, acks, server.position().z());
    }

    private static Trace burstThenQuiet(GroundedHarnessMotor motor, CharacterMovementEnvironment world,
                                        CharacterMovementState initial) {
        GhostPredictionHistory history = history();
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        CharacterMovementInputState held = new CharacterMovementInputState();
        CharacterMovementState client = initial, server = initial;
        List<String> events = new ArrayList<>();
        List<Integer> replays = new ArrayList<>();
        List<Long> acks = new ArrayList<>();
        double maxBackward = 0;

        // Two client physical predictions and sends arrive together within server tick 100.
        for (int sequence = 1; sequence <= 2; sequence++) {
            GhostControlPayloads.Intent intent = forward(sequence);
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent));
            client = step(motor, world, client, intent);
            assertTrue(gate.offerQueued(sequence, intent, GhostPredictionHistory.CAPACITY));
        }

        GhostControlPayloads.Intent first = gate.takeQueued();
        assertEquals(1, first.sequence());
        held.accept(first);
        server = step(motor, world, server, held.held()); // exactly one server step this tick
        held.afterStep();
        SnapshotApplication firstSnapshot = reconcile(history, motor, world, client, snapshot(100, 1, server));
        client = firstSnapshot.state;
        events.add("tick100:clientBefore=" + firstSnapshot.beforeZ + " auth=" + server.position().z()
                + " ack=1 replay=" + firstSnapshot.replayCount + " after=" + client.position().z()
                + " back=" + firstSnapshot.backwardCorrection);
        maxBackward = Math.max(maxBackward, firstSnapshot.backwardCorrection);
        replays.add(firstSnapshot.replayCount);
        acks.add(1L);

        // The already accepted seq2 is processed on the next physical tick, even with no new arrivals.
        GhostControlPayloads.Intent second = gate.takeQueued();
        assertEquals(2, second.sequence());
        held.accept(second);
        server = step(motor, world, server, held.held());
        held.afterStep();
        SnapshotApplication secondSnapshot = reconcile(history, motor, world, client, snapshot(101, 2, server));
        client = secondSnapshot.state;
        events.add("tick101:clientBefore=" + secondSnapshot.beforeZ + " auth=" + server.position().z()
                + " ack=2 replay=" + secondSnapshot.replayCount + " after=" + client.position().z()
                + " back=" + secondSnapshot.backwardCorrection);
        maxBackward = Math.max(maxBackward, secondSnapshot.backwardCorrection);
        replays.add(secondSnapshot.replayCount);
        acks.add(2L);

        assertNull(gate.takeQueued());
        server = step(motor, world, server, held.held()); // empty queue: last applied forward remains held
        SnapshotApplication quiet = reconcile(history, motor, world, client, snapshot(102, 2, server));
        events.add("tick102:clientBefore=" + quiet.beforeZ + " auth=" + server.position().z()
                + " ack=2 replay=" + quiet.replayCount + " after=" + quiet.state.position().z()
                + " back=" + quiet.backwardCorrection);
        maxBackward = Math.max(maxBackward, quiet.backwardCorrection);
        replays.add(quiet.replayCount);
        acks.add(2L);
        return new Trace(events, maxBackward, replays, acks, server.position().z());
    }

    private static SnapshotApplication reconcile(GhostPredictionHistory history, GroundedHarnessMotor motor,
            CharacterMovementEnvironment world, CharacterMovementState client,
            GhostControlPayloads.Snapshot snapshot) {
        double before = client.position().z();
        GhostPredictionHistory.SnapshotResult result = history.reconcile(snapshot);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
        CharacterMovementState corrected = new CharacterMovementState(
                new MovementVector(snapshot.x(), snapshot.y(), snapshot.z()),
                new MovementVector(snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ()), snapshot.onGround());
        for (GhostControlPayloads.Intent replay : result.replayFrames()) corrected = step(motor, world, corrected, replay);
        double backward = Math.max(0d, before - corrected.position().z());
        return new SnapshotApplication(corrected, result.replayFrames().size(), before, backward);
    }

    private static CharacterMovementState step(GroundedHarnessMotor motor, CharacterMovementEnvironment world,
                                                CharacterMovementState state, GhostControlPayloads.Intent intent) {
        return motor.tick(state, intent.wishX(), intent.wishZ(), false,
                (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, intent.yaw(), world, false);
    }

    private static GhostPredictionHistory history() {
        return new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
    }

    private static GhostControlPayloads.Snapshot snapshot(long tick, long ack, CharacterMovementState state) {
        return new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.CHARACTER, tick, ack,
                state.position().x(), state.position().y(), state.position().z(), 0, 0,
                state.velocity().x(), state.velocity().y(), state.velocity().z(), state.onGround(),
                1f, 1f, false, 0, GhostControlPayloads.Snapshot.MovementMode.GROUNDED,
                java.util.Optional.empty(), java.util.Optional.empty(), 0);
    }

    private static GhostControlPayloads.Intent forward(long sequence) {
        return new GhostControlPayloads.Intent(EPOCH, sequence, (short) 0, (short) 1000,
                (byte) 0, 0, 0, 0);
    }

    private static CharacterMovementPolicy resolvedHumanPolicy() throws Exception {
        String resource = "/data/moonstation14/moonstation14/character/human.json";
        try (var stream = CharacterPredictionCoalescingTraceTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("missing resolved CHARACTER definition " + resource);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return CharacterMovementPolicy.fromCharacterData(CharacterData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }

    private record SnapshotApplication(CharacterMovementState state, int replayCount, double beforeZ,
                                       double backwardCorrection) { }
    private record Trace(List<String> events, double maximumBackwardCorrection, List<Integer> replayCounts,
                         List<Long> acknowledgements, double finalServerPosition) { }
}
