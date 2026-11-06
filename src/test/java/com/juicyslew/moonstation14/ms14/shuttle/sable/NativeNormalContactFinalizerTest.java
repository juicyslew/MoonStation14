package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class NativeNormalContactFinalizerTest {
    private static final com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint WORLD =
            new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, null);

    @Test void liveUsedAdmissionRequiresSingleMoveAndWorldEndpoints() {
        var target = NativeNormalContactPolicy.prescribe(new Vec3(-1, 0, 0), Vec3.ZERO,
                new Vec3(1, 0, 0), .5);
        var framed = new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                Vec3.ZERO, Vec3.ZERO, java.util.UUID.randomUUID());
        assertSame(target, NativeNormalContactFinalizer.targetForStep(target, 1, WORLD, WORLD));
        assertNull(NativeNormalContactFinalizer.targetForStep(target, 0, WORLD, WORLD));
        assertNull(NativeNormalContactFinalizer.targetForStep(target, 2, WORLD, WORLD));
        assertNull(NativeNormalContactFinalizer.targetForStep(target, 1, WORLD, framed));
        assertNull(NativeNormalContactFinalizer.targetForStep(target, 1, framed, WORLD));
        assertNull(NativeNormalContactFinalizer.targetForStep(target, 1, null, WORLD));
    }
    @Test void replacesClippedNormalAndRetainsTangentAfterFrameCorrectionOnce() {
        var target = NativeNormalContactPolicy.prescribe(new Vec3(-4, 0, 0), Vec3.ZERO,
                new Vec3(1, 0, 0), .5);
        var result = NativeNormalContactFinalizer.finalizeWorld(new MovementVector(-2, 7, 3),
                new MovementVector(1, 2, 4), target);
        assertEquals(2, result.x(), 1e-9);
        assertEquals(9, result.y(), 1e-9);
        assertEquals(7, result.z(), 1e-9);
    }

    @Test void nullTargetPreservesOrdinaryMotorAndCorrectionExactly() {
        assertEquals(new MovementVector(4, 5, 6), NativeNormalContactFinalizer.finalizeWorld(
                new MovementVector(1, 2, 3), new MovementVector(3, 3, 3), null));
    }

    @Test void landingWitnessClipsOnlyApproachingDestinationNormal() {
        var destination = java.util.UUID.randomUUID();
        var witness = new NativeNormalContactFinalizer.LandingWitness(null,
                destination, new Vec3(0, 1, 0), -4d, false);
        assertEquals(new MovementVector(4, 0, 6), NativeNormalContactFinalizer.finalizeWorld(
                new MovementVector(0, 0, 0), new MovementVector(4, 5, 6), null,
                NativeNormalContactFinalizer.landingForStep(witness, 1, WORLD,
                        new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                                Vec3.ZERO, Vec3.ZERO, destination))));
        assertEquals(new MovementVector(4, 5, 6), NativeNormalContactFinalizer.finalizeWorld(
                new MovementVector(0, 0, 0), new MovementVector(4, 5, 6), null,
                new NativeNormalContactFinalizer.LandingWitness(witness.fromFrame(), witness.toFrame(),
                        witness.worldNormal(), 3d, false)));
        assertEquals(new MovementVector(4, 5, 6), NativeNormalContactFinalizer.finalizeWorld(
                new MovementVector(0, 0, 0), new MovementVector(4, 5, 6), null,
                new NativeNormalContactFinalizer.LandingWitness(witness.fromFrame(), witness.toFrame(),
                        witness.worldNormal(), -4d, true)));
        assertNull(NativeNormalContactFinalizer.landingForStep(witness, 2, WORLD,
                new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                        Vec3.ZERO, Vec3.ZERO, destination)));
        assertNull(NativeNormalContactFinalizer.landingForStep(witness, 1, null,
                new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                        Vec3.ZERO, Vec3.ZERO, destination)), "missing before point is unknown, not WORLD");
        assertNull(NativeNormalContactFinalizer.landingForStep(witness, 1, WORLD, WORLD),
                "a WORLD destination is not a supported frame landing");
        var wrongDestination = new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                Vec3.ZERO, Vec3.ZERO, java.util.UUID.randomUUID());
        assertNull(NativeNormalContactFinalizer.landingForStep(witness, 1, WORLD, wrongDestination));
        assertNull(NativeNormalContactFinalizer.landingForStep(
                new NativeNormalContactFinalizer.LandingWitness(destination, destination, new Vec3(0, 1, 0), -4, false),
                1, framePoint(destination), framePoint(destination)));
        assertNull(NativeNormalContactFinalizer.landingForStep(
                new NativeNormalContactFinalizer.LandingWitness(null, destination, new Vec3(0, 2, 0), -4, false),
                1, WORLD, framePoint(destination)));
    }

    @Test void motorClipsDescendingWorldToFrameLandingBeforeRebaseAndNeutralProbeStaysGrounded() {
        double dt = .05d;
        var destination = java.util.UUID.randomUUID();
        var framePoint = new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                Vec3.ZERO, Vec3.ZERO, destination);
        MovementVector frameDelta = new MovementVector(0, 4, 0);
        MovementVector[] requested = {MovementVector.ZERO};
        MovementCollisionResolver floor = (position, displacement, grounded) -> {
            requested[0] = displacement;
            assertEquals(new MovementVector(0, -.4, 0), displacement);
            return new MovementCollisionResolver.CollisionResult(new MovementVector(0, -.1, 0), true);
        };
        var environment = new CharacterMovementEnvironment(dt, 32, 8.4, .98, MovementVector.ZERO,
                32, 0, floor);
        var motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        var motorResult = motor.tick(new CharacterMovementState(MovementVector.ZERO,
                        new MovementVector(0, -8, 0), false),
                new CharacterMovementCommand(0, 0, false, false), environment, false);
        assertTrue(motorResult.onGround());
        assertEquals(0, motorResult.velocity().y(), 0);

        var witness = new NativeNormalContactFinalizer.LandingWitness(null, destination,
                new Vec3(0, 1, 0), requested[0].y() / dt + frameDelta.y(), false);
        var admitted = NativeNormalContactFinalizer.landingForStep(witness, 1, WORLD, framePoint);
        assertNotNull(admitted);
        assertEquals(-4, admitted.incomingDestinationNormalSpeed(), 1e-12);
        assertEquals(new MovementVector(0, 0, 0), NativeNormalContactFinalizer.finalizeWorld(
                motorResult.velocity(), frameDelta, null, admitted));
        assertEquals(4, NativeNormalContactFinalizer.finalizeWorld(
                motorResult.velocity(), frameDelta, null).y(), 0,
                "the legacy path reproduces the erroneous positive rebase");

        MovementCollisionResolver neutralFloor = (position, displacement, grounded) -> {
            assertEquals(new MovementVector(0, -.001, 0), displacement,
                    "neutral grounded tick retains the ordinary support probe");
            return new MovementCollisionResolver.CollisionResult(MovementVector.ZERO, true);
        };
        var neutralEnvironment = new CharacterMovementEnvironment(dt, 32, 8.4, .98, MovementVector.ZERO,
                32, 0, neutralFloor);
        var neutral = motor.tick(new CharacterMovementState(MovementVector.ZERO,
                        NativeNormalContactFinalizer.finalizeWorld(motorResult.velocity(), frameDelta, null, admitted), true),
                new CharacterMovementCommand(0, 0, false, false), neutralEnvironment, false);
        assertTrue(neutral.onGround());
        assertEquals(0, neutral.velocity().y(), 0);
    }

    @Test void diagonalLandingResponsePreservesTheEntireTangentAndLegitimateOutpacing() {
        var from = java.util.UUID.randomUUID();
        var to = java.util.UUID.randomUUID();
        Vec3 normal = new Vec3(1, 1, 0).normalize();
        var approaching = new NativeNormalContactFinalizer.LandingWitness(from, to, normal, -1, false);
        var diagonal = NativeNormalContactFinalizer.finalizeWorld(new MovementVector(0, 0, 2),
                new MovementVector(3, 1, 4), null, approaching);
        assertEquals(1, diagonal.x(), 1e-12);
        assertEquals(-1, diagonal.y(), 1e-12);
        assertEquals(6, diagonal.z(), 1e-12);
        assertEquals(0, diagonal.x() * normal.x + diagonal.y() * normal.y + diagonal.z() * normal.z, 1e-12);

        var outpacing = new NativeNormalContactFinalizer.LandingWitness(from, to,
                new Vec3(0, 1, 0), 3, false); // body -1 m/s, deck -4 m/s: separating in destination basis
        assertEquals(new MovementVector(3, 4, 4), NativeNormalContactFinalizer.finalizeWorld(
                MovementVector.ZERO, new MovementVector(3, 4, 4), null, outpacing));
    }

    @Test void productionMoveFreezesFloorWitnessAndBothOwnersUseSingleMoveAdmission() throws IOException {
        Path root = Path.of(System.getProperty("moonstation14.projectDir"));
        String motion = Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyMotion.java"));
        String server = Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessWorldStep.java"));
        String client = Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"));
        assertTrue(motion.contains("nativeFinalFloorRow(body, info.trackingSubLevel, collisionScope)"));
        assertTrue(motion.contains("requested.y()*normal.y"));
        assertTrue(motion.contains("GroundedHarnessPhysics.TICK_SECONDS"));
        assertTrue(server.contains("landingForStep(") && server.contains("nativeMoveCount[0]"));
        assertTrue(client.contains("landingForStep(") && client.contains("nativeMoveCount[0]"));
    }

    private static com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint framePoint(
            java.util.UUID frame) {
        return new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, frame);
    }
}
