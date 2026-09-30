package com.juicyslew.moonstation14.ms14.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterMovementMotorTest {
    private static final MovementCollisionResolver OPEN = (position, displacement, grounded) ->
            new MovementCollisionResolver.CollisionResult(displacement, grounded);

    @Test
    void playerAndVillagerHostsUseTheSameHumanMotorPolicy() {
        CharacterMovementPolicy playerProfile = CharacterMovementPolicy.forCharacterPrototype("moonstation14:human");
        CharacterMovementPolicy villagerProfile = CharacterMovementPolicy.forCharacterPrototype("moonstation14:human");
        CharacterMovementMotor playerMotor = new CharacterMovementMotor(playerProfile);
        CharacterMovementMotor villagerMotor = new CharacterMovementMotor(villagerProfile);
        CharacterMovementState initial = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementCommand input = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);

        assertEquals(playerMotor.policy(), villagerMotor.policy());
        assertEquals(playerMotor.tick(initial, input, environment, false),
                villagerMotor.tick(initial, input, environment, false));
        assertEquals(20d, CharacterMovementPolicy.HUMAN.accelerationPerSecondSquared());
    }

    @Test
    void typedHumanAndPigProfilesUseTheSameMotorWithDifferentConfiguredSpeeds() throws IOException {
        CharacterMovementPolicy human = CharacterMovementPolicy.fromCharacterData(readCharacter("human.json"));
        CharacterMovementPolicy pig = CharacterMovementPolicy.fromCharacterData(readCharacter("pig.json"));
        CharacterMovementMotor humanMotor = new CharacterMovementMotor(human);
        CharacterMovementMotor pigMotor = new CharacterMovementMotor(pig);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState humanWalk = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState pigWalk = humanWalk;
        CharacterMovementState humanSprint = humanWalk;
        CharacterMovementState pigSprint = humanWalk;

        for (int tick = 0; tick < 120; tick++) {
            humanWalk = humanMotor.tick(humanWalk, new CharacterMovementCommand(1d, 0d, false, false), environment, false);
            pigWalk = pigMotor.tick(pigWalk, new CharacterMovementCommand(1d, 0d, false, false), environment, false);
            humanSprint = humanMotor.tick(humanSprint, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
            pigSprint = pigMotor.tick(pigSprint, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
        }

        assertEquals(2.5d, humanWalk.velocity().x(), 1e-9);
        assertEquals(4d, pigWalk.velocity().x(), 1e-9);
        assertEquals(4.5d, humanSprint.velocity().x(), 1e-9);
        assertEquals(4d, pigSprint.velocity().x(), 1e-9);
        assertEquals(20d, human.accelerationPerSecondSquared());
        assertEquals(20d, pig.accelerationPerSecondSquared());
    }

    private static CharacterData readCharacter(String name) throws IOException {
        String path = "data/moonstation14/moonstation14/character/" + name;
        try (var stream = CharacterMovementMotorTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return CharacterData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        }
    }

    @Test
    void walkAndSprintSpeedsUseTheConfiguredRatio() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState walk = initial;
        CharacterMovementState sprint = initial;
        for (int i = 0; i < 120; i++) {
            walk = motor.tick(walk, new CharacterMovementCommand(1d, 0d, false, false), environment, false);
            sprint = motor.tick(sprint, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
        }
        assertEquals(2.5d, walk.velocity().x(), 1e-9);
        assertEquals(4.5d, sprint.velocity().x(), 1e-9);
        assertEquals(1.8d, sprint.velocity().x() / walk.velocity().x(), 1e-9);
    }

    @Test
    void knockdownScalesVoluntaryTargetSpeedButDoesNotBlockInputOrStunMomentum() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState normal = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState crawling = normal;
        CharacterMovementEnvironment ordinary = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementEnvironment knockedDown = envWithVoluntarySpeed(.05d, 0d, 0d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR, OPEN);
        CharacterMovementCommand input = new CharacterMovementCommand(1d, 0d, false, false);
        for (int i = 0; i < 120; i++) {
            normal = motor.tick(normal, input, ordinary, false);
            crawling = motor.tick(crawling, input, knockedDown, false);
        }
        assertEquals(2.5d, normal.velocity().x(), 1e-9);
        assertEquals(1d, crawling.velocity().x(), 1e-9);
        assertTrue(crawling.velocity().x() > 0d, "knockdown alone preserves voluntary input");

        CharacterMovementState stunned = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true), input,
                knockedDown, true);
        assertEquals(0d, stunned.velocity().x(), 0d, "full stun still blocks voluntary input");
    }

    @Test
    void knockdownDoesNotScaleImpulseOrChangeNoInputCoasting() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment knockedDown = envWithVoluntarySpeed(.05d, 0d, 0d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR, OPEN);
        CharacterMovementState impulse = motor.tick(state(MovementVector.ZERO,
                        new MovementVector(2d, 0d, 0d), false),
                new CharacterMovementCommand(1d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 0d, 0d, 1d, new MovementVector(1d, 0d, 0d),
                        1d, 0d, OPEN, 1d, CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR), false);
        assertEquals(3d, impulse.velocity().x(), 1e-12,
                "external impulse and pre-existing velocity remain unscaled when above target speed");

        CharacterMovementEnvironment ordinary = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState coasting = state(MovementVector.ZERO, new MovementVector(.004d, 0d, 0d), true);
        assertEquals(motor.tick(coasting, new CharacterMovementCommand(0d, 0d, false, false), ordinary, false),
                motor.tick(coasting, new CharacterMovementCommand(0d, 0d, false, false), knockedDown, false));
    }

    @Test
    void diagonalWishIsNormalizedAndAccelerationIsBoundedPerTick() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementCommand diagonal = new CharacterMovementCommand(1d, 1d, false, false);
        assertEquals(1d, Math.hypot(diagonal.wishX(), diagonal.wishZ()), 1e-12);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true), diagonal,
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(2.5d, result.velocity().horizontalLength(), 1e-12); // accel * tick * wishSpeed
    }

    @Test
    void fractionalWishScalesTopSpeedWhileFullDiagonalRetainsFullSpeed() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment environment = env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState fractional = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState diagonal = fractional;
        for (int i = 0; i < 120; i++) {
            fractional = motor.tick(fractional, new CharacterMovementCommand(.5d, 0d, false, false),
                    environment, false);
            diagonal = motor.tick(diagonal, new CharacterMovementCommand(1d, 1d, false, false),
                    environment, false);
        }
        assertEquals(CharacterMovementPolicy.HUMAN.walkSpeedPerSecond() * .5d, fractional.velocity().x(), 1e-9);
        assertEquals(0d, fractional.velocity().z(), 1e-12);
        assertEquals(CharacterMovementPolicy.HUMAN.walkSpeedPerSecond(), diagonal.velocity().horizontalLength(), 1e-9);
    }

    @Test
    void groundFrictionAndExplicitGravityAreAppliedInTickUnits() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver leavesGround = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(displacement, false);
        CharacterMovementEnvironment environment = env(.1d, 10d, 0d, MovementVector.ZERO, 0d, leavesGround);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), environment, false);
        assertEquals(0d, result.velocity().x(), 0d);
        assertEquals(-1d, result.velocity().y(), 1e-12);
        assertEquals(0d, result.position().x(), 0d);
        assertEquals(-.001d, result.position().y(), 1e-12);
    }

    @Test
    void humanNeutralGroundFrictionStopsOnTheNextTwentyHertzNoWishTick() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState result = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2.5d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(1d / 20d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(MovementVector.ZERO, result.velocity());
    }

    @Test
    void customLowFrictionPolicyRetainsTheExplicitPointSevenFiveExample() {
        CharacterMovementPolicy lowFriction = new CharacterMovementPolicy(20d, 2.5d, 4.5d,
                2.5d, 2.5d, .005d);
        CharacterMovementMotor motor = new CharacterMovementMotor(lowFriction);
        CharacterMovementState result = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(1.5d, result.velocity().x(), 1e-12);
    }

    @Test
    void belowMinimumFrictionSpeedPreservesStunnedSlipAndEqualityStillAppliesFriction() {
        CharacterMovementPolicy policy = CharacterMovementPolicy.HUMAN;
        CharacterMovementMotor motor = new CharacterMovementMotor(policy);
        double dt = .1d;
        double threshold = policy.minimumFrictionSpeed();
        CharacterMovementEnvironment environment = env(dt, 10d, 0d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementCommand noWish = new CharacterMovementCommand(0d, 0d, false, false);

        CharacterMovementState below = motor.tick(
                state(MovementVector.ZERO, new MovementVector(threshold / 2d, 0d, 0d), true),
                noWish, environment, true);
        assertEquals(threshold / 2d, below.velocity().x(), 0d);
        assertEquals(threshold / 2d * dt, below.position().x(), 0d);
        assertTrue(below.position().x() > 0d);
        assertEquals(-.001d, below.position().y(), 1e-12);

        CharacterMovementState atThreshold = motor.tick(
                state(MovementVector.ZERO, new MovementVector(threshold, 0d, 0d), true),
                noWish, environment, true);
        double frictionFactor = Math.max(0d, 1d - policy.groundFrictionNoInputPerSecond() * dt);
        assertEquals(threshold * frictionFactor, atThreshold.velocity().x(), 1e-12);
        assertEquals(threshold * frictionFactor * dt, atThreshold.position().x(), 1e-12);
        assertEquals(-.001d, atThreshold.position().y(), 1e-12);
    }

    @Test
    void stunnedWishUsesNoInputFrictionWhileUnstunnedWishUsesWithInputFriction() {
        CharacterMovementPolicy policy = new CharacterMovementPolicy(0d, 2.5d, 4.5d, 1d, 4d, .005d);
        CharacterMovementMotor motor = new CharacterMovementMotor(policy);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true);
        CharacterMovementCommand wish = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementEnvironment environment = env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN);

        CharacterMovementState moving = motor.tick(initial, wish, environment, false);
        CharacterMovementState stopped = motor.tick(initial, wish, environment, true);

        assertEquals(1.8d, moving.velocity().x(), 1e-12); // with-input friction
        assertEquals(1.2d, stopped.velocity().x(), 1e-12); // stun suppresses wish before friction selection
    }

    @Test
    void stunSuppressesVoluntaryWishButPreservesMomentumAndExternalImpulse() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(1d, 0d, 0d), false);
        CharacterMovementEnvironment environment = env(.1d, 0d, 0d, new MovementVector(2d, 0d, 0d), 2d, OPEN);
        CharacterMovementState stunned = motor.tick(initial,
                new CharacterMovementCommand(1d, 0d, true, true), environment, true);
        assertEquals(3d, stunned.velocity().x(), 0d);
        assertEquals(.3d, stunned.position().x(), 1e-12);
        assertEquals(0d, stunned.velocity().y(), 0d);

        CharacterMovementState unstunned = motor.tick(initial,
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertNotEquals(initial.velocity().x(), unstunned.velocity().x());
    }

    @Test
    void collisionWallBlocksDisplacementAndVelocityIntoWall() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver wall = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, requested.y(), requested.z()), grounded);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, wall), false);
        assertEquals(0d, result.position().x(), 0d);
        assertEquals(0d, result.velocity().x(), 0d);
    }

    @Test
    void repeatedInputsFromSameStateAreDeterministic() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(new MovementVector(2d, 3d, 4d), new MovementVector(.1d, -.2d, .3d), false);
        CharacterMovementCommand command = new CharacterMovementCommand(.4d, -.8d, false, true);
        CharacterMovementEnvironment environment = env(.05d, 9.8d, 0d, new MovementVector(.2d, 0d, -.1d), .3d, OPEN);
        assertEquals(motor.tick(initial, command, environment, false), motor.tick(initial, command, environment, false));
    }

    @Test
    void multiTickReplayFromIdenticalStateAndCommandHistoryIsDeterministic() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(new MovementVector(2d, 3d, 4d),
                new MovementVector(.1d, -.2d, .3d), true);
        CharacterMovementCommand[] history = {
                new CharacterMovementCommand(.4d, -.8d, false, true),
                new CharacterMovementCommand(.5d, 0d, false, false),
                new CharacterMovementCommand(0d, 1d, false, true),
                new CharacterMovementCommand(0d, 0d, false, false)
        };
        CharacterMovementEnvironment environment = env(.05d, 9.8d, 0d,
                new MovementVector(.2d, 0d, -.1d), .3d, OPEN);

        CharacterMovementState first = initial;
        CharacterMovementState replay = initial;
        for (int i = 0; i < 20; i++) {
            CharacterMovementCommand command = history[i % history.length];
            first = motor.tick(first, command, environment, false);
            replay = motor.tick(replay, command, environment, false);
            assertEquals(first, replay);
        }
    }

    @Test
    void rejectsNonfiniteAndOutOfBoundInputsAndCollisionDestinations() {
        assertThrows(IllegalArgumentException.class, () -> new MovementVector(Double.NaN, 0d, 0d));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(Double.NaN, 0d, false, false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(Double.POSITIVE_INFINITY, 0d, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterMovementPolicy(Double.NaN, 1d, 1d, 1d, 1d, .005d));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterMovementPolicy(1d, 1d, 1d, 1d, -1d, .005d));
        assertThrows(IllegalArgumentException.class,
                () -> env(Double.NaN, 0d, 0d, MovementVector.ZERO, 0d, OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> env(.05d, 0d, 0d, new MovementVector(2d, 0d, 0d), 1d, OPEN));
        MovementCollisionResolver teleport = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(100d, 0d, 0d), grounded);
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementMotor(CharacterMovementPolicy.HUMAN).tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true), new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, teleport), false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(1.01d, 0d, false, false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterMovementCommand(0d, -1.01d, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> CharacterMovementPolicy.forCharacterPrototype("minecraft:zombie"));
    }

    @Test
    void directionChangeAcceleratesAlongWishWithoutRemovingLateralMomentum() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(0d, 0d, 3d), false);
        CharacterMovementState result = motor.tick(initial, new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(2.5d, result.velocity().x(), 1e-12);
        assertEquals(3d, result.velocity().z(), 1e-12);
    }

    @Test
    void airbornePerpendicularImpulseRetainsResultantSpeedBeyondDeclaredWishSpeed() throws IOException {
        CharacterMovementCommand forwardFast = new CharacterMovementCommand(1d, 0d, false, true);
        for (String prototype : new String[]{"human.json", "pig.json"}) {
            CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(readCharacter(prototype));
            CharacterMovementMotor motor = new CharacterMovementMotor(policy);
            // Already carrying 3 blocks/s sideways; a separate 1 block/s impulse arrives this tick.
            CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(0d, 0d, 3d), false);
            CharacterMovementEnvironment impulse = env(.05d, 0d, 0d,
                    new MovementVector(0d, 0d, 1d), 1d, OPEN);
            CharacterMovementState moving = motor.tick(initial, forwardFast, impulse, false);
            double wish = policy.sprintSpeedPerSecond();

            assertEquals(wish, moving.velocity().x(), 1e-12, prototype + " projected wish");
            assertEquals(4d, moving.velocity().z(), 1e-12, prototype + " retained sideways speed");
            assertEquals(Math.hypot(wish, 4d), moving.velocity().horizontalLength(), 1e-12,
                    prototype + " resultant speed is not the projected-wish limit");
            assertEquals(wish * .05d, moving.position().x(), 1e-12);
            assertEquals(.2d, moving.position().z(), 1e-12);

            CharacterMovementState coasting = motor.tick(moving,
                    new CharacterMovementCommand(0d, 0d, false, false),
                    env(.05d, 0d, 0d, MovementVector.ZERO, 0d, OPEN), false);
            assertEquals(moving.velocity().horizontalLength(), coasting.velocity().horizontalLength(), 1e-12,
                    prototype + " no-input air tick retains both horizontal components");
        }
    }

    @Test
    void jumpingIntoOneBlockWallClipsBlockedAxisUntilClearanceButKeepsLateralMomentum() throws IOException {
        MovementCollisionResolver wallUntilOneBlockHigh = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(
                        position.y() + requested.y() >= 1d ? requested.x() : 0d,
                        requested.y(), requested.z()), false);
        for (String prototype : new String[]{"human.json", "pig.json"}) {
            CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(readCharacter(prototype));
            CharacterMovementMotor motor = new CharacterMovementMotor(policy);
            CharacterMovementEnvironment environment = new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                    .98d, MovementVector.ZERO, 0d, 0d, wallUntilOneBlockHigh);
            CharacterMovementEnvironment midairImpulse = new CharacterMovementEnvironment(.05d, 32d, 8.4d,
                    .98d, new MovementVector(0d, 0d, 3d), 3d, 0d, wallUntilOneBlockHigh);
            CharacterMovementState current = state(MovementVector.ZERO, MovementVector.ZERO, true);
            double wish = policy.sprintSpeedPerSecond();
            for (int tick = 0; tick < 2; tick++) {
                current = motor.tick(current, new CharacterMovementCommand(1d, 0d, tick == 0, true),
                        tick == 0 ? environment : midairImpulse, false);
                assertEquals(0d, current.position().x(), 0d, prototype + " wall blocks horizontal request");
                assertEquals(0d, current.velocity().x(), 0d, prototype + " blocked velocity cleared");
                assertEquals(tick == 0 ? 0d : 3d, current.velocity().z(), 1e-12,
                        prototype + " airborne perpendicular impulse survives the wall");
            }
            assertEquals(.7532d, current.position().y(), 1e-12);
            current = motor.tick(current, new CharacterMovementCommand(1d, 0d, false, true), environment, false);
            assertEquals(1.001336d, current.position().y(), 1e-12,
                    "third jump displacement crosses fixture clearance");
            assertEquals(wish, current.velocity().x(), 1e-12, prototype + " projected wish after wall");
            assertEquals(3d, current.velocity().z(), 1e-12);
            assertEquals(Math.hypot(wish, 3d), current.velocity().horizontalLength(), 1e-12,
                    prototype + " resultant after clearance");
            assertEquals(wish * .05d, current.position().x(), 1e-12);
            assertEquals(.3d, current.position().z(), 1e-12);
        }
    }

    @Test
    void landingRetainsAirborneHorizontalSpeedThenNeutralGroundFrictionStopsIt() throws IOException {
        MovementCollisionResolver floor = (position, requested, grounded) -> {
            double y = position.y() + requested.y() < 0d ? -position.y() : requested.y();
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(requested.x(), y, requested.z()), y != requested.y() || grounded);
        };
        for (String prototype : new String[]{"human.json", "pig.json"}) {
            CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(readCharacter(prototype));
            CharacterMovementMotor motor = new CharacterMovementMotor(policy);
            CharacterMovementEnvironment environment = env(.05d, 32d, 8.4d,
                    MovementVector.ZERO, 0d, floor);
            double wish = policy.sprintSpeedPerSecond();
            CharacterMovementState falling = state(new MovementVector(0d, .1d, 0d),
                    new MovementVector(wish, -4d, 3d), false);
            CharacterMovementCommand noInput = new CharacterMovementCommand(0d, 0d, false, false);
            CharacterMovementState landed = motor.tick(falling, noInput, environment, false);
            assertTrue(landed.onGround());
            assertEquals(0d, landed.position().y(), 1e-12);
            assertEquals(0d, landed.velocity().y(), 0d);
            assertEquals(wish, landed.velocity().x(), 1e-12);
            assertEquals(3d, landed.velocity().z(), 1e-12);
            assertEquals(Math.hypot(wish, 3d), landed.velocity().horizontalLength(), 1e-12,
                    prototype + " landing tick still uses airborne friction rule");
            assertEquals(wish * .05d, landed.position().x(), 1e-12);
            assertEquals(.15d, landed.position().z(), 1e-12);

            CharacterMovementState stopped = motor.tick(landed, noInput, environment, false);
            assertTrue(stopped.onGround());
            assertEquals(0d, stopped.velocity().horizontalLength(), 0d,
                    prototype + " 20/s friction at .05s gives factor zero");
            assertEquals(landed.position().x(), stopped.position().x(), 0d);
            assertEquals(landed.position().z(), stopped.position().z(), 0d);
        }
    }

    @Test
    void groundedStunPreservesSlipUnderLinearFrictionAndGravity() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver wallAndGround = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, 0d, displacement.z()), true);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, new MovementVector(2d, 0d, 2d), true),
                new CharacterMovementCommand(1d, 0d, true, true),
                env(.1d, 10d, 7d, MovementVector.ZERO, 0d, wallAndGround), true);
        assertEquals(0d, result.velocity().x(), 0d); // wall blocked the horizontal axis
        assertEquals(0d, result.velocity().y(), 0d); // grounded collision clears downward velocity
        assertEquals(0d, result.position().z(), 0d); // high neutral-ground friction applies before stunned movement
    }

    @Test
    void slipperySurfaceReducesGroundCoastingAndRightAngleSteeringWithoutChangingTargetSpeed() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState initial = state(MovementVector.ZERO, new MovementVector(2.5d, 0d, 0d), true);
        CharacterMovementCommand noWish = new CharacterMovementCommand(0d, 0d, false, false);
        CharacterMovementState normalCoast = motor.tick(initial, noWish,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), false);
        CharacterMovementState lubeCoast = motor.tick(initial, noWish,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, .05d, OPEN), false);
        assertEquals(0d, normalCoast.velocity().x(), 0d);
        assertEquals(2.375d, lubeCoast.velocity().x(), 1e-12);
        assertTrue(lubeCoast.velocity().horizontalLength() < initial.velocity().horizontalLength(),
                "contact friction still brakes, but must not add speed");

        CharacterMovementState lateral = state(MovementVector.ZERO, new MovementVector(0d, 0d, 2d), true);
        CharacterMovementCommand turn = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementState normalTurn = motor.tick(lateral, turn,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), false);
        CharacterMovementState lubeTurn = motor.tick(lateral, turn,
                envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, .05d, OPEN), false);
        assertEquals(2.5d, normalTurn.velocity().x(), 1e-12);
        assertEquals(.125d, lubeTurn.velocity().x(), 1e-12);
        assertTrue(lubeTurn.velocity().z() > normalTurn.velocity().z());
        assertEquals(1.9d, lubeTurn.velocity().z(), 1e-12, "contact does not erase lateral momentum");
    }

    @Test
    void stationaryGroundedTicksProbeSupportAndKeepLubeAccelerationBounded() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver floor = (position, displacement, grounded) -> {
            if (displacement.y() < 0d && position.y() <= 0d) {
                return new MovementCollisionResolver.CollisionResult(
                        new MovementVector(displacement.x(), 0d, displacement.z()), true);
            }
            return new MovementCollisionResolver.CollisionResult(displacement, false);
        };
        CharacterMovementEnvironment lube = new CharacterMovementEnvironment(1d / 20d, 32d, 0d, 1d,
                MovementVector.ZERO, 0d, 0d, floor, .05d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR);
        CharacterMovementCommand knockdownWish = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementState state = state(MovementVector.ZERO, MovementVector.ZERO, true);

        for (int tick = 0; tick < 8; tick++) {
            double previousSpeed = state.velocity().x();
            state = motor.tick(state, knockdownWish, lube, false);
            assertTrue(state.onGround(), "a supporting floor must survive a stationary grounded tick");
            assertEquals(0d, state.position().y(), 0d);
            assertTrue(state.velocity().x() - previousSpeed <= .09d,
                    "per-tick lube-scaled acceleration must not jump to 1.8 blocks/s");
        }
        assertEquals(.05d * (1d - Math.pow(.95d, 8)) / .05d, state.velocity().x(), 1e-12,
                "lube-scaled knockdown acceleration remains bounded each tick instead of jumping to 1.8 blocks/s");

        CharacterMovementEnvironment lubeNoInput = envWithSurface(1d / 20d, 32d, 0d,
                MovementVector.ZERO, 0d, .05d, floor);
        CharacterMovementState coasting = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), lubeNoInput, false);
        assertEquals(1.9d, coasting.velocity().x(), 1e-12,
                "grounded no-input momentum retains the configured .95 lube friction factor");
        assertTrue(coasting.onGround());
    }

    @Test
    void groundedSupportProbeDoesNotOverrideUpwardImpulseOrJumpDisplacement() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment environment = env(.05d, 32d, 8.4d, MovementVector.ZERO, 0d, OPEN);
        CharacterMovementState jump = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, true, false), environment, false);
        assertEquals(.42d, jump.position().y(), 1e-12);

        CharacterMovementEnvironment upwardImpulseEnvironment = new CharacterMovementEnvironment(.05d, 32d,
                8.4d, .98d, new MovementVector(0d, 1d, 0d), 1d, 0d,
                (position, displacement, grounded) -> {
                    assertTrue(displacement.y() > 0d, "an upward impulse must not receive a downward probe");
                    return new MovementCollisionResolver.CollisionResult(displacement, false);
                });
        motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false), upwardImpulseEnvironment, false);
    }

    @Test
    void stickySurfaceIncreasesGroundDampingAndAccelerationWithoutChangingTargetSpeed() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementEnvironment normal = envWithSurface(.025d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN);
        CharacterMovementEnvironment sticky = envWithSurface(.025d, 0d, 0d, MovementVector.ZERO, 0d, 1.5d, OPEN);

        CharacterMovementState normalCoast = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), normal, false);
        CharacterMovementState stickyCoast = motor.tick(
                state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true),
                new CharacterMovementCommand(0d, 0d, false, false), sticky, false);
        assertEquals(1d, normalCoast.velocity().x(), 1e-12);
        assertEquals(.5d, stickyCoast.velocity().x(), 1e-12);

        CharacterMovementCommand forward = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementEnvironment normalAccelerationEnvironment =
                envWithSurface(.01d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN);
        CharacterMovementEnvironment stickyAccelerationEnvironment =
                envWithSurface(.01d, 0d, 0d, MovementVector.ZERO, 0d, 1.5d, OPEN);
        CharacterMovementState normalAccelerating = motor.tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true), forward, normalAccelerationEnvironment, false);
        CharacterMovementState stickyAccelerating = motor.tick(
                state(MovementVector.ZERO, MovementVector.ZERO, true), forward, stickyAccelerationEnvironment, false);
        assertTrue(stickyAccelerating.velocity().x() > normalAccelerating.velocity().x());

        CharacterMovementState normalAtTarget = state(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState stickyAtTarget = normalAtTarget;
        for (int i = 0; i < 1200; i++) {
            normalAtTarget = motor.tick(normalAtTarget, forward, normalAccelerationEnvironment, false);
            stickyAtTarget = motor.tick(stickyAtTarget, forward, stickyAccelerationEnvironment, false);
        }
        assertEquals(CharacterMovementPolicy.HUMAN.walkSpeedPerSecond(), normalAtTarget.velocity().x(), 1e-9);
        assertEquals(normalAtTarget.velocity().x(), stickyAtTarget.velocity().x(), 1e-9);

        assertEquals(1d, env(.1d, 0d, 0d, MovementVector.ZERO, 0d, OPEN).surfaceMovementFactor());
    }

    @Test
    void surfaceMovementFactorMustBeFiniteAndNonnegative() {
        assertThrows(IllegalArgumentException.class,
                () -> envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, -0.1d, OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, Double.NaN, OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> envWithSurface(.05d, 0d, 0d, MovementVector.ZERO, 0d, Double.POSITIVE_INFINITY, OPEN));
    }

    @Test
    void stunnedSlipMomentumGetsContactFrictionAndContactExitImmediatelyRestoresNormalFriction() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementCommand blockedWish = new CharacterMovementCommand(1d, 0d, false, false);
        CharacterMovementState slipping = state(MovementVector.ZERO, new MovementVector(2d, 0d, 0d), true);
        CharacterMovementState stunnedLube = motor.tick(slipping, blockedWish,
                envWithSurface(.1d, 0d, 0d, MovementVector.ZERO, 0d, .05d, OPEN), true);
        CharacterMovementState stunnedNormal = motor.tick(slipping, blockedWish,
                envWithSurface(.1d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), true);
        assertEquals(1.8d, stunnedLube.velocity().x(), 1e-12);
        assertEquals(0d, stunnedNormal.velocity().x(), 0d);
        assertEquals(0d, stunnedLube.velocity().z(), 0d);
        assertTrue(stunnedLube.position().x() > 0d, "friction must not fabricate an immediate stop");

        // Environment snapshots are sampled per tick: once contact is gone the next tick is neutral,
        // with no retained surface factor or post-contact glide timer.
        CharacterMovementState leftSource = motor.tick(stunnedLube,
                new CharacterMovementCommand(0d, 0d, false, false),
                envWithSurface(.1d, 0d, 0d, MovementVector.ZERO, 0d, 1d, OPEN), true);
        assertEquals(0d, leftSource.velocity().x(), 0d);
    }

    @Test
    void nonzeroJumpUsesInjectedVelocityAndGravity() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, true, false),
                env(.1d, 10d, 5d, MovementVector.ZERO, 0d, OPEN), false);
        assertEquals(.5d, result.position().y(), 1e-12);
        assertEquals(4d, result.velocity().y(), 1e-12);
    }

    @Test
    void vanillaJumpUsesPreGravityVelocityAndFallsBackToGround() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        double dt = 1d / 20d;
        double gravity = .08d * 20d * 20d;
        double jump = .42d * 20d;
        MovementCollisionResolver floor = (position, displacement, grounded) -> {
            if (position.y() + displacement.y() < 0d) {
                return new MovementCollisionResolver.CollisionResult(
                        new MovementVector(displacement.x(), -position.y(), displacement.z()), true);
            }
            return new MovementCollisionResolver.CollisionResult(displacement, false);
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(dt, gravity, jump, .98d,
                MovementVector.ZERO, 0d, 0d, floor);
        CharacterMovementState state = state(MovementVector.ZERO, MovementVector.ZERO, true);
        state = motor.tick(state, new CharacterMovementCommand(0d, 0d, true, false), environment, false);
        assertEquals(.42d, state.position().y(), 1e-12);
        double maximumRise = state.position().y();
        for (int tick = 0; tick < 100 && !state.onGround(); tick++) {
            state = motor.tick(state, new CharacterMovementCommand(0d, 0d, false, false), environment, false);
            maximumRise = Math.max(maximumRise, state.position().y());
        }
        assertTrue(maximumRise >= 1.20d, "vanilla jump should rise about 1.25 blocks: " + maximumRise);
        assertTrue(maximumRise <= 1.30d, "vanilla jump rise should remain near 1.25 blocks: " + maximumRise);
        assertTrue(state.onGround(), "falling jump should resolve back onto the floor");
        assertEquals(0d, state.velocity().y(), 0d);

        CharacterMovementState nextJump = motor.tick(state,
                new CharacterMovementCommand(0d, 0d, true, false), environment, false);
        assertEquals(.42d, nextJump.position().y() - state.position().y(), 1e-12,
                "a fresh grounded jump input should apply its full first-tick displacement");
    }

    @Test
    void jumpClearsOneBlockObstacleWithAValidatedCollisionFixture() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver oneBlockObstacle = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(position.y() + displacement.y() >= 1d ? displacement.x() : 0d,
                                displacement.y(), displacement.z()), false);
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, 0d, oneBlockObstacle);
        CharacterMovementState state = state(MovementVector.ZERO, MovementVector.ZERO, true);
        for (int tick = 0; tick < 20 && state.position().x() == 0d; tick++) {
            state = motor.tick(state, new CharacterMovementCommand(1d, 0d, tick == 0, false), environment, false);
        }
        assertTrue(state.position().y() >= 1d, "horizontal progress should begin only after clearing the block height");
        assertTrue(state.position().x() > 0d, "the jump should make horizontal progress beyond a one-block obstacle");
    }

    @Test
    void groundedResolverMayReportBoundedStepUpForDownwardRequest() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver step = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), .4d, displacement.z()), true);
        CharacterMovementState result = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 10d, 0d, MovementVector.ZERO, 0d, .5d, step), false);
        assertEquals(.4d, result.position().y(), 0d);
        assertEquals(0d, result.velocity().y(), 0d);

        MovementCollisionResolver tooHigh = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), .6d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 10d, 0d, MovementVector.ZERO, 0d, .5d, tooHigh), false));

        MovementCollisionResolver stationaryRise = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, .4d, 0d), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 10d, 0d, MovementVector.ZERO, 0d, .5d, stationaryRise), false));
    }

    @Test
    void coordinateSubtractionRoundingDoesNotRejectMovementOrSuppressMomentum() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementVector position = new MovementVector(1e7, 1e7, 1e7);
        MovementCollisionResolver entityMoveRounding = (before, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(
                        (before.x() + requested.x()) - before.x(),
                        (before.y() + requested.y()) - before.y(),
                        (before.z() + requested.z()) - before.z()), grounded);
        CharacterMovementState result = motor.tick(
                state(position, new MovementVector(1d, 0d, 1d), false),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, entityMoveRounding), true);

        assertEquals((position.x() + .05d) - position.x(), result.position().x() - position.x(), 0d);
        assertEquals(1d, result.velocity().x(), 0d);
        assertEquals(1d, result.velocity().z(), 0d);
    }

    @Test
    void coordinateSubtractionRoundingIsAllowedNearMinecraftWorldBorder() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementVector position = new MovementVector(29_999_999.9d, 29_999_999.9d, 29_999_999.9d);
        MovementCollisionResolver entityMoveRounding = (before, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(
                        (before.x() + requested.x()) - before.x(),
                        (before.y() + requested.y()) - before.y(),
                        (before.z() + requested.z()) - before.z()), grounded);
        CharacterMovementState result = motor.tick(
                state(position, new MovementVector(1d, 0d, 1d), false),
                new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, entityMoveRounding), true);

        assertEquals((position.x() + .05d) - position.x(), result.position().x() - position.x(), 0d);
        assertEquals(1d, result.velocity().x(), 0d);
        assertEquals(1d, result.velocity().z(), 0d);
    }

    @Test
    void coordinateRoundingAllowanceDoesNotGrowAtAbsurdFinitePositions() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver oneBlockTeleport = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(1d, requested.y(), requested.z()), grounded);

        assertThrows(IllegalArgumentException.class, () -> motor.tick(
                state(new MovementVector(1e20d, 0d, 0d), MovementVector.ZERO, false),
                new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 0d, 0d, MovementVector.ZERO, 0d, oneBlockTeleport), true));
    }

    @Test
    void groundedStepUsesLargerOfRequestedRiseAndStepHeightAndRequiresHorizontalStep() {
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        MovementCollisionResolver step = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(
                        new MovementVector(displacement.x(), .5d, displacement.z()), true);
        CharacterMovementState stepped = motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, step), false);
        assertEquals(.5d, stepped.position().y(), 0d);

        MovementCollisionResolver tooHigh = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), .6d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, tooHigh), false));

        MovementCollisionResolver noHorizontalStep = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, .6d, 0d), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, noHorizontalStep), false));

        MovementCollisionResolver wallStep = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(0d, .6d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, wallStep), false));

        MovementCollisionResolver teleportStep = (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(displacement.x(), 5d, displacement.z()), true);
        assertThrows(IllegalArgumentException.class, () -> motor.tick(state(MovementVector.ZERO, MovementVector.ZERO, true),
                new CharacterMovementCommand(1d, 0d, true, false),
                env(.05d, 0d, 8d, MovementVector.ZERO, 0d, .5d, teleportStep), false));
    }

    private static CharacterMovementState state(MovementVector position, MovementVector velocity, boolean grounded) {
        return new CharacterMovementState(position, velocity, grounded);
    }

    private static CharacterMovementEnvironment env(double dt, double gravity, double jump,
                                                    MovementVector impulse, double impulseBound,
                                                    MovementCollisionResolver resolver) {
        return env(dt, gravity, jump, impulse, impulseBound, 0d, resolver);
    }

    private static CharacterMovementEnvironment env(double dt, double gravity, double jump,
                                                     MovementVector impulse, double impulseBound, double maxStep,
                                                     MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(dt, gravity, jump, 1d, impulse, impulseBound, maxStep, resolver);
    }

    private static CharacterMovementEnvironment envWithSurface(double dt, double gravity, double jump,
                                                                MovementVector impulse, double impulseBound,
                                                                double surfaceFactor,
                                                                MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(dt, gravity, jump, 1d, impulse, impulseBound, 0d, resolver,
                surfaceFactor);
    }

    private static CharacterMovementEnvironment envWithVoluntarySpeed(double dt, double gravity, double jump,
                                                                        double speedFactor,
                                                                        MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(dt, gravity, jump, 1d, MovementVector.ZERO,
                0d, 0d, resolver, 1d, speedFactor);
    }
}
