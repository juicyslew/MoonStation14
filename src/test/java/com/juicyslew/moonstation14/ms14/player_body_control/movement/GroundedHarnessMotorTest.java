package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundedHarnessMotorTest {
    private static final double DT = 1d / 20d;
    private static final MovementCollisionResolver GROUNDED = (position, requested, wasOnGround) ->
            new MovementCollisionResolver.CollisionResult(requested, wasOnGround);
    private static final MovementCollisionResolver OPEN = (position, requested, wasOnGround) ->
            new MovementCollisionResolver.CollisionResult(requested, false);
    private final GroundedHarnessMotor motor = new GroundedHarnessMotor(CharacterMovementPolicy.HUMAN);

    @Test
    void frozenTinyAirBaselineReproducesRejectedNativeMoveAndStalledGravity() {
        var reference = new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(
                java.util.UUID.randomUUID(), new MovementVector(0d, -.0012435420526908472d, 0d));
        var requests = new java.util.ArrayList<MovementVector>();
        var environment = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, (position, requested, grounded) -> {
                    requests.add(requested);
                    // Entity.move's no-position-update path for request.lengthSqr() <= 1.0E-7.
                    return new MovementCollisionResolver.CollisionResult(MovementVector.ZERO, false);
                });
        var initial = new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, false, reference);
        var result = motor.tick(initial, 0, 0, false, false, 0d, environment, false);
        assertEquals(-6.217710263454236e-5d, requests.getFirst().y(), 1e-15);
        var nativeRequest = requests.getFirst();
        assertTrue(nativeRequest.x() * nativeRequest.x() + nativeRequest.y() * nativeRequest.y()
                        + nativeRequest.z() * nativeRequest.z() <= 1.0e-7d,
                "native Entity.move does not update position below squared displacement threshold");
        assertEquals(-1.568d + reference.pointVelocity().y() * (1d - .98d), result.velocity().y(), 1e-12,
                "rejected native-sized request must not suppress subsequent gravity");
    }

    @Test
    void airborneCoastMovesBeforeMinecraftAirRetention() {
        CharacterMovementState result = state(new MovementVector(2d, 0d, 0d), false);
        CharacterMovementEnvironment air = env(32d, 8.4d, OPEN);
        for (int i = 0; i < 4; i++) result = motor.tick(result, 0, 0, false, false, 0d, air, false);
        double retention = .91F;
        assertEquals(.1d * (1d - Math.pow(retention, 4)) / (1d - retention), result.position().x(), 1e-12);
        assertEquals(2d * Math.pow(retention, 4), result.velocity().x(), 1e-12);
    }

    @Test
    void cleanupUsesBlocksPerSecondThresholdStrictlyAndPreservesPostCleanupImpulse() {
        CharacterMovementEnvironment impulse = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                new MovementVector(.1d, 0d, 0d), .1d, 0d, OPEN);
        CharacterMovementState result = motor.tick(state(new MovementVector(.04d, 0d, -.08d), false),
                0, 0, false, false, 0d, impulse, false);
        assertEquals(.1d * DT, result.position().x(), 1e-12);
        assertEquals(-.004d, result.position().z(), 1e-12);
        assertEquals(.1d * .91F, result.velocity().x(), 1e-12);
        assertEquals(.06d * DT, tick(state(new MovementVector(.06d, 0d, 0d), false), 0, 0,
                false, false, 0d, env(32d, 8.4d, OPEN), false).position().x(), 1e-12);
        CharacterMovementEnvironment tinyImpulse = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                new MovementVector(.04d, 0d, 0d), .04d, 0d, OPEN);
        CharacterMovementState newlyImpulsed = motor.tick(state(MovementVector.ZERO, false),
                0, 0, false, false, 0d, tinyImpulse, false);
        assertEquals(.04d * DT, newlyImpulsed.position().x(), 1e-12);
    }

    @Test
    void takeoffUsesPreStepGroundStateAndZeroGravityAndFlightAreUnchanged() {
        CharacterMovementPolicy policy = CharacterMovementPolicy.HUMAN;
        double takeoffSpeed = Math.min(policy.walkSpeedPerSecond(),
                policy.accelerationPerSecondSquared() * DT * policy.walkSpeedPerSecond());
        double takeoffDistance = takeoffSpeed * DT;
        CharacterMovementState takeoff = motor.tick(state(MovementVector.ZERO, true),
                0, 1000, true, false, 0d, env(32d, 8.4d, OPEN), false);
        assertEquals(takeoffDistance, takeoff.position().z(), 1e-12);
        assertEquals(takeoffSpeed, takeoff.velocity().z(), 1e-12, "no air retention on takeoff tick");
        assertEquals(.42d, takeoff.position().y(), 1e-12);
        assertEquals((8.4d - 32d * DT) * .98d, takeoff.velocity().y(), 1e-12);
        CharacterMovementState zeroG = motor.tick(state(new MovementVector(2d, 0d, 0d), false),
                0, 0, false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(.1d, zeroG.position().x(), 1e-12);
        CharacterMovementState flight = motor.tick(state(new MovementVector(2d, 0d, 0d), false),
                0, 0, false, false, 0d, env(32d, 8.4d, OPEN), false, true);
        assertEquals(.1d, flight.position().x(), 1e-12);
        CharacterMovementState tinyZeroG = tick(state(new MovementVector(.04d, 0d, 0d), false),
                0, 0, false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(.002d, tinyZeroG.position().x(), 1e-12);
        CharacterMovementState tinyFlight = motor.tick(state(new MovementVector(.04d, 0d, 0d), false),
                0, 0, false, false, 0d, env(32d, 8.4d, OPEN), false, true);
        assertEquals(.002d, tinyFlight.position().x(), 1e-12);
        CharacterMovementEnvironment landingResolver = env(32d, 8.4d, GROUNDED);
        CharacterMovementState landing = motor.tick(state(new MovementVector(2d, 0d, 0d), false),
                0, 0, false, false, 0d, landingResolver, false);
        assertEquals(.1d, landing.position().x(), 1e-12);
        assertEquals(2d * .91F, landing.velocity().x(), 1e-12);
    }

    @Test
    void usesSuppliedSpeciesPolicyRatherThanHumanConstants() {
        CharacterMovementPolicy pig = new CharacterMovementPolicy(20d, 4d, 4d, 20d, 20d, .005d);
        GroundedHarnessMotor pigMotor = new GroundedHarnessMotor(pig);
        CharacterMovementState result = state(MovementVector.ZERO, true);
        for (int tick = 0; tick < 120; tick++) {
            result = pigMotor.tick(result, 0, 1000, false, false, 0d, env(0d, 0d, GROUNDED), false);
        }
        assertEquals(4d, result.velocity().z(), 1e-9);
    }

    @Test
    void walkSprintAndAccelerationUseHumanPolicy() {
        CharacterMovementState walk = state(MovementVector.ZERO, true);
        CharacterMovementState sprint = walk;
        for (int tick = 0; tick < 120; tick++) {
            walk = tick(walk, 0, 1000, false, false, 0d, env(0d, 0d, GROUNDED), false);
            sprint = tick(sprint, 0, 1000, false, true, 0d, env(0d, 0d, GROUNDED), false);
        }
        assertEquals(2.5d, walk.velocity().z(), 1e-9);
        assertEquals(4.5d, sprint.velocity().z(), 1e-9);
        assertEquals(20d, CharacterMovementPolicy.HUMAN.accelerationPerSecondSquared());
        CharacterMovementState first = tick(state(MovementVector.ZERO, false), 0, 1000,
                false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(2.5d, first.velocity().z(), 1e-12);
    }

    @Test
    void conditionedRotatedAirSteeringRemainsWithinWalkAndSprintEnvelopes() {
        assertRotatedAirSteeringBounded(CharacterMovementPolicy.HUMAN, false);
        assertRotatedAirSteeringBounded(CharacterMovementPolicy.HUMAN, true);
        CharacterMovementPolicy custom = new CharacterMovementPolicy(14d, 3d, 5d, 18d, 18d, .005d);
        assertRotatedAirSteeringBounded(custom, false);
        assertRotatedAirSteeringBounded(custom, true);
    }

    @Test
    void frozenAirBaselineIsNotDampedAndRelativeControlMatchesStationaryFlight() {
        var reference = new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(
                java.util.UUID.randomUUID(), new MovementVector(2d, 0d, 2d));
        var requests = new java.util.ArrayList<MovementVector>();
        var air = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, (position, displacement, grounded) -> {
                    requests.add(displacement);
                    return new MovementCollisionResolver.CollisionResult(displacement, grounded);
                });
        var translated = new CharacterMovementState(MovementVector.ZERO,
                new MovementVector(2d, 0d, 2d), false, reference);
        var stationary = new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, false);
        var motor = new GroundedHarnessMotor(CharacterMovementPolicy.HUMAN);
        for (int i = 0; i < 10; i++) {
            translated = motor.tick(translated, 0, 0, false, false, 0d, air, false);
            stationary = motor.tick(stationary, 0, 0, false, false, 0d, air, false);
        }
        assertEquals(2d, translated.velocity().x(), 1e-12);
        assertEquals(2d, translated.velocity().z(), 1e-12);
        assertEquals(stationary.velocity(), reference.relativeVelocity(translated.velocity()));
        assertEquals(1d, translated.position().z(), 1e-12);
        assertEquals(1d, translated.position().x(), 1e-12);
        assertEquals(0d, stationary.position().z(), 1e-12);
        assertEquals(20, requests.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(.1d, requests.get(i * 2).z(), 1e-12);
            assertEquals(.1d, requests.get(i * 2).x(), 1e-12);
            assertEquals(0d, requests.get(i * 2 + 1).z(), 1e-12);
            assertEquals(0d, requests.get(i * 2 + 1).x(), 1e-12);
        }
    }

    @Test
    void profileRetentionAppliesOnceToRelativeMotionAndPreservesBaseline() {
        var baseline = new MovementVector(2d, 0d, 0d);
        var reference = new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(
                java.util.UUID.randomUUID(), baseline);
        var environment = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, OPEN, 1d, 1d, MovementVector.ZERO,
                GroundedHarnessPhysics.gravityAirControl(false));
        var initial = new CharacterMovementState(MovementVector.ZERO,
                new MovementVector(4d, 0d, 0d), false, reference);
        var result = motor.tick(initial, 0, 0, false, false, 0d, environment, false);
        assertEquals(2d + 2d * .91F, result.velocity().x(), 1e-12);
    }

    @Test
    void profilePathStillSnapsTinyRelativeVelocityWithoutDampingBaseline() {
        var baseline = new MovementVector(2d, 0d, 0d);
        var reference = new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(
                java.util.UUID.randomUUID(), baseline);
        var environment = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, OPEN, 1d, 1d, MovementVector.ZERO,
                GroundedHarnessPhysics.gravityAirControl(false));
        var initial = new CharacterMovementState(MovementVector.ZERO,
                new MovementVector(2.05d, 0d, 0d), false, reference);
        var result = motor.tick(initial, 0, 0, false, false, 0d, environment, false);
        assertEquals(2d, result.velocity().x(), 1e-12);
    }

    @Test
    void absentProfileRetainsLegacyAdapterConditioningWithBaseline() {
        var baseline = new MovementVector(2d, 0d, 0d);
        var reference = new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(
                java.util.UUID.randomUUID(), baseline);
        var initial = new CharacterMovementState(MovementVector.ZERO,
                new MovementVector(4d, 0d, 0d), false, reference);
        var result = motor.tick(initial, 0, 0, false, false, 0d,
                env(32d, 8.4d, OPEN), false);
        assertEquals(2d + 2d * .91F, result.velocity().x(), 1e-12);
    }

    @Test
    void fullThreeDimensionalFrozenTranslationIsInvariantUnderWorldDownGravity() {
        for (MovementVector baseline : java.util.List.of(
                new MovementVector(1.7d, 3.2d, -2.4d),
                new MovementVector(-2.3d, -4.1d, 1.2d))) {
            var translatedRequests = new java.util.ArrayList<MovementVector>();
            var stationaryRequests = new java.util.ArrayList<MovementVector>();
            CharacterMovementEnvironment translatedAir = airWithRequests(translatedRequests);
            CharacterMovementEnvironment stationaryAir = airWithRequests(stationaryRequests);
            var reference = new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(
                    java.util.UUID.randomUUID(), baseline);
            MovementVector relative = new MovementVector(.6d, .35d, -.45d);
            CharacterMovementState moving = new CharacterMovementState(MovementVector.ZERO,
                    reference.worldVelocity(relative), false, reference);
            CharacterMovementState still = new CharacterMovementState(MovementVector.ZERO, relative, false);
            var motor = new GroundedHarnessMotor(CharacterMovementPolicy.HUMAN);
            for (int tick = 1; tick <= 12; tick++) {
                moving = motor.tick(moving, 0, 0, false, false, 0d, translatedAir, false);
                still = motor.tick(still, 0, 0, false, false, 0d, stationaryAir, false);
                var elapsedBaseline = baseline.scale(DT * tick);
                assertVectorClose(still.position(), moving.position().add(elapsedBaseline.scale(-1d)));
                assertVectorClose(still.velocity(), reference.relativeVelocity(moving.velocity()));
                assertVectorClose(stationaryRequests.get(tick - 1).add(baseline.scale(DT)),
                        translatedRequests.get(tick - 1));
            }
        }
    }

    private static CharacterMovementEnvironment airWithRequests(java.util.List<MovementVector> requests) {
        return new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d, MovementVector.ZERO, 0d, 0d,
                (position, displacement, grounded) -> {
                    requests.add(displacement);
                    return new MovementCollisionResolver.CollisionResult(displacement, grounded);
                });
    }

    private static void assertVectorClose(MovementVector expected, MovementVector actual) {
        assertEquals(expected.x(), actual.x(), 1e-10);
        assertEquals(expected.y(), actual.y(), 1e-10);
        assertEquals(expected.z(), actual.z(), 1e-10);
    }

    private void assertRotatedAirSteeringBounded(CharacterMovementPolicy policy, boolean sprint) {
        GroundedHarnessMotor configuredMotor = new GroundedHarnessMotor(policy);
        CharacterMovementEnvironment air = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, OPEN);
        CharacterMovementState result = state(new MovementVector(.05d, 0d, -.05d), false);
        double speedEnvelope = sprint ? policy.sprintSpeedPerSecond() : policy.walkSpeedPerSecond();
        for (int tick = 0; tick < 1_000; tick++) {
            int phase = tick % 8;
            int wishX = switch (phase) {
                case 0, 1 -> 1_000;
                case 2, 3 -> -1_000;
                case 4, 5 -> 707;
                default -> -707;
            };
            int wishZ = switch (phase) {
                case 0, 3 -> 1_000;
                case 1, 2 -> -1_000;
                case 4, 7 -> 707;
                default -> -707;
            };
            double yaw = switch (phase) {
                case 0, 4 -> 45d;
                case 1, 5 -> -73d;
                case 2, 6 -> 137d;
                default -> -179d;
            };
            result = configuredMotor.tick(result, wishX, wishZ, false, sprint, yaw, air, false);
            double horizontalSpeed = Math.hypot(result.velocity().x(), result.velocity().z());
            assertTrue(horizontalSpeed <= speedEnvelope + 1e-9,
                    "conditioned air speed exceeded " + speedEnvelope + " at tick " + tick
                            + " (sprint=" + sprint + "): " + horizontalSpeed);
        }
    }

    @Test
    void yawCardinalsDiagonalAndPartialAnalogPreserveWishMagnitude() {
        assertDisplacement(0, 1000, 0d, 0d, 2.5d);
        assertDisplacement(0, 1000, 90d, -2.5d, 0d);
        assertDisplacement(0, 1000, -90d, 2.5d, 0d);
        CharacterMovementState diagonal = tick(state(MovementVector.ZERO, false), 1000, 1000,
                false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(2.5d / Math.sqrt(2d), diagonal.velocity().x(), 1e-12);
        assertEquals(2.5d / Math.sqrt(2d), diagonal.velocity().z(), 1e-12);
        CharacterMovementState partial = tick(state(MovementVector.ZERO, false), 0, 250,
                false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(.625d, partial.velocity().z(), 1e-12);
    }

    @Test
    void neutralGroundFrictionStopsHumanMomentumAtTwentyHertz() {
        CharacterMovementState result = tick(state(new MovementVector(2.5d, 0d, 0d), true),
                0, 0, false, false, 0d, env(0d, 0d, GROUNDED), false);
        assertEquals(0d, result.velocity().x(), 1e-12);
    }

    @Test
    void lubeSurfaceFactorScalesAccelerationAndKnockdownScalesVoluntarySpeed() {
        CharacterMovementEnvironment lube = new CharacterMovementEnvironment(DT, 0d, 0d, .98d,
                MovementVector.ZERO, 0d, 0d, GROUNDED, .05d, 1d);
        CharacterMovementState onLube = tick(state(MovementVector.ZERO, true), 0, 1000,
                false, false, 0d, lube, false);
        assertEquals(.125d, onLube.velocity().z(), 1e-12,
                "single-tick surface acceleration is 20 * .05 * .05 * 2.5");

        CharacterMovementEnvironment knockdown = new CharacterMovementEnvironment(DT, 0d, 0d, .98d,
                MovementVector.ZERO, 0d, 0d, GROUNDED, 1d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR);
        CharacterMovementState crawling = state(MovementVector.ZERO, true);
        for (int tick = 0; tick < 120; tick++) {
            crawling = tick(crawling, 0, 1000, false, false, 0d, knockdown, false);
        }
        assertEquals(1d, crawling.velocity().z(), 1e-9);
    }

    @Test
    void stunSuppressesVoluntaryInputWithoutErasingExternalMomentum() {
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(DT, 0d, 0d, .98d,
                MovementVector.ZERO, 0d, 0d, OPEN);
        CharacterMovementState result = tick(state(new MovementVector(3d, 0d, 0d), false),
                0, 1000, false, false, 0d, environment, true);
        assertEquals(3d, result.velocity().x(), 1e-12);
        assertEquals(0d, result.velocity().z(), 1e-12);
        assertEquals(.15d, result.position().x(), 1e-12);
    }

    @Test
    void groundedJumpUsesInjectedGravityAndReturnsResolverGrounding() {
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(DT, .08d * 400d,
                8.4d, .98d, MovementVector.ZERO, 0d, 0d, GROUNDED);
        CharacterMovementState result = tick(state(MovementVector.ZERO, true), 0, 0,
                true, false, 0d, environment, false);
        assertEquals(.42d, result.position().y(), 1e-12);
        assertEquals((8.4d - 32d * DT) * .98d, result.velocity().y(), 1e-12);
        assertTrue(result.onGround(), "ground state is the resolver's result, not adapter policy");
    }

    @Test
    void normalJumpBallisticAirtimeIsShorterThanAngularRecoveryGrace() {
        CharacterMovementEnvironment environment = env(32d, 8.4d, OPEN);
        CharacterMovementState result = tick(state(MovementVector.ZERO, true), 0, 0,
                true, false, 0d, environment, false);
        double priorY = result.position().y();
        double airtime = DT;
        // Reuse the production grounded motor's 20 Hz integration and existing human physics values.
        for (int i = 0; i < 40; i++) {
            result = tick(result, 0, 0, false, false, 0d, environment, false);
            airtime += DT;
            if (result.position().y() < priorY && result.position().y() <= 0d) break;
            priorY = result.position().y();
        }
        assertTrue(airtime < .65d, "discrete 8.4 m/s jump under 32 m/s^2 returns before recovery grace: " + airtime);
    }

    @Test
    void collisionResolverDisplacementAndSupportResultAreReturned() {
        MovementCollisionResolver clipped = (position, requested, wasOnGround) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(requested.x(), 0d, 0d), true);
        CharacterMovementState result = tick(state(MovementVector.ZERO, false), 0, 1000,
                false, false, 0d, env(0d, 0d, clipped), false);
        assertEquals(MovementVector.ZERO, result.position());
        assertEquals(0d, result.velocity().z(), 0d);
        assertTrue(result.onGround());
    }

    @Test
    void rejectsInvalidQuantizedWishesAndYaw() {
        CharacterMovementEnvironment environment = env(0d, 0d, OPEN);
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 1001,
                0, false, false, 0d, environment, false));
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 0,
                -1001, false, false, 0d, environment, false));
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 0,
                0, false, false, Double.NaN, environment, false));
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 0,
                0, false, false, 180.1d, environment, false));
        assertThrows(NullPointerException.class, () -> motor.tick(null, 0, 0,
                false, false, 0d, environment, false));
    }

    private void assertDisplacement(int wishX, int wishZ, double yaw, double expectedX, double expectedZ) {
        CharacterMovementState result = tick(state(MovementVector.ZERO, false), wishX, wishZ,
                false, false, yaw, env(0d, 0d, OPEN), false);
        assertEquals(expectedX * DT, result.position().x(), 1e-12);
        assertEquals(expectedZ * DT, result.position().z(), 1e-12);
    }

    private CharacterMovementState tick(CharacterMovementState state, int wishX, int wishZ, boolean jump,
                                        boolean sprint, double yaw, CharacterMovementEnvironment environment,
                                        boolean stunned) {
        return motor.tick(state, wishX, wishZ, jump, sprint, yaw, environment, stunned);
    }

    private static CharacterMovementState state(MovementVector velocity, boolean onGround) {
        return new CharacterMovementState(MovementVector.ZERO, velocity, onGround);
    }

    private static CharacterMovementEnvironment env(double gravity, double jump,
                                                    MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(DT, gravity, jump, .98d, MovementVector.ZERO,
                0d, 0d, resolver);
    }
}
