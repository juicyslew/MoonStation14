package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.movement.*;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** CHARACTER receives client full-tick commands in order and runs at most one motor step per server tick. */
class CharacterIntentArrivalScheduleTest {
    private static final MovementCollisionResolver OPEN_GROUND = (position, displacement, wasGrounded) ->
            new MovementCollisionResolver.CollisionResult(displacement, true);
    private static final CharacterMovementEnvironment WORLD = new CharacterMovementEnvironment(
            GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
            GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
            MovementVector.ZERO, 0d, 0d, OPEN_GROUND);

    @Test
    void burstStatesReduceInOrderBeforeOneStepAndBacklogDoesNotPersist() throws Exception {
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        CharacterMovementInputState input = new CharacterMovementInputState();
        GroundedHarnessMotor motor = new GroundedHarnessMotor(resolvedHumanPolicy());
        CharacterMovementState state = grounded();
        for (int i = 1; i <= 14; i++) offer(gate, i, 40, forward(i));
        assertEquals(14, input.acceptQueued(gate).sequence());
        // Dequeue is admission only; acknowledgement follows physical application below.
        assertEquals(0, gate.acknowledgedAppliedSequence());
        state = step(motor, state, input);
        gate.markApplied(14);
        assertEquals(14, gate.acknowledgedAppliedSequence());
        assertEquals(0, gate.queuedSize(), "entire accepted burst is reduced in this callback");
        offer(gate, 15, 41, intent(15, -1000, 0, GhostControlPayloads.BUTTON_SPRINT | GhostControlPayloads.BUTTON_SNEAK));
        assertEquals(15, input.acceptQueued(gate).sequence());
        int calls = 0;
        state = step(motor, state, input); calls++;
        assertEquals(1, calls, "one physical step irrespective of admitted command count");
        assertEquals(-1000, input.held().wishX());
        assertTrue((input.held().buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0);
        assertTrue((input.held().buttons() & GhostControlPayloads.BUTTON_SNEAK) != 0);
        assertNull(gate.takeQueued());
    }

    @Test
    void jumpEdgesCoalesceWithinBatchAndAreConsumedEvenWhenLatestStateReleases() {
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        CharacterMovementInputState input = new CharacterMovementInputState();
        offer(gate, 1, 50, intent(1, 0, 0, GhostControlPayloads.BUTTON_JUMP));
        offer(gate, 2, 50, intent(2, 0, 0, 0));
        offer(gate, 3, 50, intent(3, 0, 0, GhostControlPayloads.BUTTON_JUMP));
        offer(gate, 4, 50, intent(4, 0, 0, 0));
        assertEquals(4, input.acceptQueued(gate).sequence());
        var sampled = input.consumeForTick(50);
        assertTrue(input.jumpForStep(sampled), "ordered quick-tap edge survives release");
        assertTrue(input.jumpForStep(sampled), "sampled tick remains stable until its single application");
        input.consumeForTick(51);
        assertFalse(input.jumpForStep(input.consumeForTick(52)), "multiple presses in one physical tick do not queue a future jump");
    }

    @Test
    void releaseInBurstIsAppliedAndAcknowledgedOnlyOnItsOwnPhysicalStep() throws Exception {
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        CharacterMovementInputState input = new CharacterMovementInputState();
        GroundedHarnessMotor motor = new GroundedHarnessMotor(resolvedHumanPolicy());
        CharacterMovementState state = grounded();
        long ack = 0;
        offer(gate, 1, 70, forward(1));
        offer(gate, 2, 70, intent(2, 0, 0, 0));

        GhostControlPayloads.Intent applied = gate.takeQueued();
        input.accept(applied);
        state = step(motor, state, input, 70);
        ack = applied.sequence();
        gate.markApplied(ack);
        double afterForwardStep = state.position().z();
        assertEquals(1, ack);

        applied = gate.takeQueued();
        assertEquals(2, applied.sequence());
        input.accept(applied);
        double beforeSpeed = state.velocity().z();
        CharacterMovementPolicy policy = resolvedHumanPolicy();
        double h = WORLD.secondsPerTick() / 3d;
        double f = Math.max(0d, Math.min(1d, 1d - policy.groundFrictionNoInputPerSecond() * h));
        assertTrue(beforeSpeed > policy.minimumFrictionSpeed(), "release starts above the friction threshold");
        assertTrue(beforeSpeed * f * f > policy.minimumFrictionSpeed(),
                "speed remains above the friction threshold through the third substep");
        state = step(motor, state, input, 71);
        ack = applied.sequence();
        gate.markApplied(ack);
        assertEquals(2, ack);
        double releaseVelocity = state.velocity().z();
        double expectedRelease = beforeSpeed * f * f * f;
        double expectedAdditionalDistance = beforeSpeed * h * (f + f * f + f * f * f);
        assertEquals(expectedRelease, releaseVelocity, 1e-12,
                "neutral input applies configured friction over three equal horizontal substeps");
        assertEquals(expectedAdditionalDistance, state.position().z() - afterForwardStep, 1e-12,
                "release displacement is the sum of the three post-friction substep velocities");
        assertTrue(releaseVelocity >= 0d, "release must not reverse the prior forward motion");
        assertNull(gate.takeQueued());
        for (int tick = 72; tick < 140 && state.velocity().z() != 0d; tick++) {
            state = step(motor, state, input, tick);
        }
        assertEquals(0d, state.velocity().z(), 0d, "neutral friction eventually settles released motion");
        assertTrue(state.position().z() >= afterForwardStep,
                "settlement preserves the accumulated forward displacement");
        double settledPosition = state.position().z();
        state = step(motor, state, input, 140);
        assertEquals(settledPosition, state.position().z(), 0d,
                "settled neutral motion holds a stable quiet position");
    }

    @Test
    void jumpTapPressAndReleaseStayOrderedAndPerformOneJumpStep() throws Exception {
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        CharacterMovementInputState input = new CharacterMovementInputState();
        GroundedHarnessMotor motor = new GroundedHarnessMotor(resolvedHumanPolicy());
        int[] physicalSteps = {0};
        MovementCollisionResolver countedGround = (position, displacement, grounded) -> {
            physicalSteps[0]++;
            return new MovementCollisionResolver.CollisionResult(displacement, true);
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(
                GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 0d, 0d, countedGround);
        GroundedHarnessMotor countedMotor = new GroundedHarnessMotor(resolvedHumanPolicy());
        CharacterMovementState state = grounded();
        offer(gate, 1, 90, intent(1, 0, 0, GhostControlPayloads.BUTTON_JUMP));
        offer(gate, 2, 90, intent(2, 0, 0, 0));

        GhostControlPayloads.Intent press = gate.takeQueued();
        assertEquals(1, press.sequence());
        input.accept(press);
        var pressSample = input.consumeForTick(90);
        assertTrue(input.jumpForStep(pressSample));
        state = countedMotor.tick(state, 0, 0, input.jumpForStep(pressSample), false, 0, environment, false);
        assertTrue(state.position().y() > 0d);

        GhostControlPayloads.Intent release = gate.takeQueued();
        assertEquals(2, release.sequence());
        input.accept(release);
        var releaseSample = input.consumeForTick(91);
        assertFalse(input.jumpForStep(releaseSample));
        state = countedMotor.tick(state, 0, 0, input.jumpForStep(releaseSample), false, 0, environment, false);
        for (int i = 0; i < 2; i++) {
            var quietSample = input.consumeForTick(92 + i);
            state = countedMotor.tick(state, 0, 0, input.jumpForStep(quietSample), false, 0, environment, false);
        }
        assertEquals(4, physicalSteps[0], "two queued states and two quiet held-state ticks each step once");
        assertFalse(input.jumpForStep(input.consumeForTick(94)), "release is the held state; the edge is not replayed");
    }

    @Test
    void staleDuplicateCapacityOverflowAndBarrierDiscardAreFailClosedAndMonotonic() {
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        CharacterMovementInputState input = new CharacterMovementInputState();
        assertTrue(gate.offerQueued(4, forward(4), 2));
        assertFalse(gate.offerQueued(4, intent(4, 0, 0, 0), 2));
        assertFalse(gate.offerQueued(3, intent(3, 0, 0, 0), 2));
        assertTrue(gate.offerQueued(5, forward(5), 2));
        assertFalse(gate.offerQueued(6, forward(6), 2));
        assertFalse(gate.exhausted(), "capacity pressure is not sequence-number exhaustion");
        assertEquals(2, gate.queuedSize());
        assertEquals(4, gate.takeQueued().sequence());
        assertEquals(5, gate.discardQueuedThroughSequence());
        assertFalse(gate.offerQueued(5, forward(5), 2), "barrier discard retains monotonic session sequence");
        assertFalse(gate.offerQueued(Long.MAX_VALUE, forward(Long.MAX_VALUE), 2));
        assertTrue(gate.exhausted(), "only the terminal sequence exhausts the gate");
        input.accept(forward(4));
        input.clear();
        assertFalse(input.hasInput());
        assertFalse(input.jumpForStep(input.consumeForTick(0)));

        gate.reset(); // caller uses only for a terminal session/new epoch
        assertTrue(gate.offerQueued(1, forward(1), 2));
    }

    private static CharacterMovementState physicalTick(GroundedHarnessMotor motor, CharacterMovementState state,
            CharacterMovementInputState input, GhostControlPayloads.Intent dequeued) {
        if (dequeued != null) input.accept(dequeued);
        return step(motor, state, input);
    }

    private static CharacterMovementState step(GroundedHarnessMotor motor, CharacterMovementState state,
                                                 CharacterMovementInputState input) {
        return step(motor, state, input, 0);
    }

    private static CharacterMovementState step(GroundedHarnessMotor motor, CharacterMovementState state,
                                                CharacterMovementInputState input, long applicationTick) {
        GhostControlPayloads.Intent held = input.held();
        if (held == null) return motor.tick(state, 0, 0, false, false, 0, WORLD, false);
        var sampled = input.consumeForTick(applicationTick);
        CharacterMovementState stepped = motor.tick(state, held.wishX(), held.wishZ(), input.jumpForStep(sampled),
                (held.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, held.yaw(), WORLD, false);
        return stepped;
    }

    private static void offer(GhostIntentGate<GhostControlPayloads.Intent> gate, long sequence, long tick,
                              GhostControlPayloads.Intent intent) {
        assertTrue(LifecycleCharacterSessionControl.intentRateAllows((int) (sequence % 8 + 1)));
        assertTrue(gate.offerQueued(sequence, intent, 64), "accepted sequence " + sequence + " at tick " + tick);
    }

    private static CharacterMovementState grounded() {
        return new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, true);
    }

    private static CharacterMovementPolicy resolvedHumanPolicy() throws Exception {
        String resource = "/data/moonstation14/moonstation14/character/human.json";
        try (var stream = CharacterIntentArrivalScheduleTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("missing resolved CHARACTER definition " + resource);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return CharacterMovementPolicy.fromCharacterData(CharacterData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }

    private static GhostControlPayloads.Intent forward(long sequence) { return intent(sequence, 0, 1000, 0); }
    private static GhostControlPayloads.Intent intent(long sequence, int wishX, int wishZ, int buttons) {
        return new GhostControlPayloads.Intent(7, sequence, (short) wishX, (short) wishZ,
                (byte) 0, buttons, 0, 0);
    }
}
