package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementCollisionGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(MovementCollisionGameTests.class);
    private static final double DT = .05d;

    private MovementCollisionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void realVillagerCollisionResolutionBoundsMotorState(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM));

        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 2));
        villager.setNoAi(true);
        double startX = helper.absolutePos(new BlockPos(3, 1, 2)).getX() + .65d;
        double startY = helper.absolutePos(new BlockPos(3, 1, 2)).getY();
        double startZ = helper.absolutePos(new BlockPos(3, 1, 2)).getZ() + .5d;
        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);

        placeAndGround(villager, startX, startY, startZ);
        require(villager.onGround(), "directional collision fixture must be grounded");
        villager.setDeltaMovement(4d, 0d, 0d);
        CharacterMovementState directional = tickAgainstVillager(helper, villager, motor,
                new CharacterMovementCommand(1d, 0d, false, false), "directional-wish");
        requireInObstacleFootprint(directional, helper, "directional-wish");

        // Repeat from the same grounded position with a grounded jump command adjacent to the slab.
        placeAndGround(villager, startX, startY, startZ);
        // Give the real entity enough bounded horizontal speed to reach the slab during the jump tick.
        villager.setDeltaMovement(4d, 0d, 0d);
        require(villager.onGround(), "jump collision fixture must be grounded");
        CharacterMovementState jumping = tickAgainstVillager(helper, villager, motor,
                new CharacterMovementCommand(1d, 0d, true, false), "grounded-jump", true);
        requireInObstacleFootprint(jumping, helper, "grounded-jump");
        LOGGER.info("Movement collision GameTest used world position ({}, {}, {}); real-coordinate rounding is exercised by the resolver",
                startX, startY, startZ);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void realVillagerGroundProbeRetainsFloorButDoesNotGlueAtAnEdge(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setNoAi(true);
        double floorY = helper.absolutePos(new BlockPos(2, 1, 2)).getY();
        double startZ = helper.absolutePos(new BlockPos(2, 1, 2)).getZ() + .5d;
        CharacterMovementMotor ordinaryMotor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        placeAndGround(villager, helper.absolutePos(new BlockPos(2, 1, 2)).getX() + .5d, floorY, startZ);
        require(villager.onGround(), "real Villager must start on the stone floor");

        for (int tick = 0; tick < 8; tick++) {
            CharacterMovementState grounded = tickAgainstVillager(helper, villager, ordinaryMotor,
                    new CharacterMovementCommand(0d, 0d, false, false), "ground-support-" + tick);
            require(grounded.onGround() && Math.abs(villager.position().y() - floorY) < 1e-9d,
                    "a supported stationary Villager must remain grounded on every probe tick");
        }

        // Disable friction only for this controlled edge departure; actual collision resolution remains vanilla.
        CharacterMovementMotor edgeMotor = new CharacterMovementMotor(
                new CharacterMovementPolicy(0d, 2.5d, 4.5d, 0d, 0d, .005d));
        double edgeStartX = helper.absolutePos(new BlockPos(3, 1, 2)).getX() + .5d;
        placeAndGround(villager, edgeStartX, floorY, startZ);
        villager.setDeltaMovement(4d, 0d, 0d);
        CharacterMovementState edgeState = null;
        for (int tick = 0; tick < 12 && villager.onGround(); tick++) {
            edgeState = tickAgainstVillager(helper, villager, edgeMotor,
                    new CharacterMovementCommand(0d, 0d, false, false), "edge-departure-" + tick);
        }
        require(edgeState != null && !villager.onGround() && !edgeState.onGround(),
                "the real Villager must lose support after moving off the stone edge");
        double supportedFloorY = floorY;
        for (int tick = 0; tick < 3 && villager.position().y() >= supportedFloorY; tick++) {
            edgeState = tickAgainstVillager(helper, villager, edgeMotor,
                    new CharacterMovementCommand(0d, 0d, false, false), "edge-fall-" + tick);
        }
        require(villager.position().y() < supportedFloorY && !edgeState.onGround(),
                "unsupported probe must permit the Villager to fall instead of gluing it to the edge");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void realVillagerGroundProbeRemainsStableWhileTurningOnLube(GameTestHelper helper) {
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos start = new BlockPos(4, 1, 4);
        Villager villager = helper.spawn(EntityType.VILLAGER, start);
        villager.setNoAi(true);
        double floorY = helper.absolutePos(start).getY();
        double startX = helper.absolutePos(start).getX() + .5d;
        double startZ = helper.absolutePos(start).getZ() + .5d;
        placeAndGround(villager, startX, floorY, startZ);
        require(villager.onGround(), "real Villager must start grounded on the flat stone floor");

        CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementCommand[] turningWishes = {
                new CharacterMovementCommand(1d, 0d, false, true),
                new CharacterMovementCommand(0d, 1d, false, true),
                new CharacterMovementCommand(-1d, 0d, false, true),
                new CharacterMovementCommand(0d, -1d, false, true)
        };
        List<String> trace = new ArrayList<>();
        double previousSpeed = 0d;
        for (int tick = 0; tick < 16; tick++) {
            CharacterMovementState result = tickAgainstVillager(helper, villager, motor,
                    turningWishes[tick % turningWishes.length], "lube-turn-" + tick, .05d, .4d);
            Vec3 position = villager.position();
            Vec3 velocity = villager.getDeltaMovement();
            double speed = Math.hypot(velocity.x, velocity.z);
            trace.add(tick + ":ground=" + villager.onGround() + ",y=" + position.y() + ",speed=" + speed);
            String diagnostics = "tick=" + tick + ", trace=" + trace + ", actualY=" + position.y()
                    + ", floorY=" + floorY + ", velocity=" + velocity;
            require(result.onGround() && villager.onGround(),
                    "real Villager lost ground while turning on lube; " + diagnostics);
            require(Math.abs(position.y() - floorY) < 1e-9d,
                    "real Villager feet must remain stable on the flat floor; " + diagnostics);
            require(position.x() >= helper.absolutePos(new BlockPos(1, 0, 1)).getX()
                            && position.x() <= helper.absolutePos(new BlockPos(8, 0, 8)).getX() + 1d
                            && position.z() >= helper.absolutePos(new BlockPos(1, 0, 1)).getZ()
                            && position.z() <= helper.absolutePos(new BlockPos(8, 0, 8)).getZ() + 1d,
                    "turning Villager must remain within the stone floor area; " + diagnostics);
            require(speed <= previousSpeed + .091d,
                    "lube acceleration must not cause a one-tick speed jump; " + diagnostics
                            + ", previousSpeed=" + previousSpeed);
            require(speed < 1.8d, "lube acceleration unexpectedly reached sprint target in one tick; " + diagnostics);
            previousSpeed = speed;
        }
        LOGGER.info("Real Villager lube turning ground/speed trace: {}", trace);
        helper.succeed();
    }

    private static CharacterMovementState tickAgainstVillager(GameTestHelper helper, Villager villager,
                                                                CharacterMovementMotor motor,
                                                                CharacterMovementCommand command, String label) {
        return tickAgainstVillager(helper, villager, motor, command, label, false);
    }

    private static CharacterMovementState tickAgainstVillager(GameTestHelper helper, Villager villager,
                                                                CharacterMovementMotor motor,
                                                                CharacterMovementCommand command, String label,
                                                                boolean requirePositiveStep) {
        return tickAgainstVillager(helper, villager, motor, command, label, requirePositiveStep, 1d, 1d);
    }

    private static CharacterMovementState tickAgainstVillager(GameTestHelper helper, Villager villager,
                                                                CharacterMovementMotor motor,
                                                                CharacterMovementCommand command, String label,
                                                                double surfaceMovementFactor,
                                                                double voluntarySpeedFactor) {
        return tickAgainstVillager(helper, villager, motor, command, label, false,
                surfaceMovementFactor, voluntarySpeedFactor);
    }

    private static CharacterMovementState tickAgainstVillager(GameTestHelper helper, Villager villager,
                                                                CharacterMovementMotor motor,
                                                                CharacterMovementCommand command, String label,
                                                                boolean requirePositiveStep,
                                                                double surfaceMovementFactor,
                                                                double voluntarySpeedFactor) {
        Vec3 worldPosition = villager.position();
        Vec3 worldVelocity = villager.getDeltaMovement();
        CharacterMovementState state = new CharacterMovementState(vector(worldPosition), vector(worldVelocity),
                villager.onGround());
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<MovementVector> requestedTrace = new AtomicReference<>();
        AtomicReference<MovementVector> actualTrace = new AtomicReference<>();
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            require(calls.incrementAndGet() == 1, label + " must resolve exactly once");
            requestedTrace.set(requested);
            Vec3 before = villager.position();
            villager.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 after = villager.position();
            MovementVector actual = new MovementVector(after.x - before.x, after.y - before.y, after.z - before.z);
            actualTrace.set(actual);
            return new MovementCollisionResolver.CollisionResult(actual, villager.onGround());
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, villager.maxUpStep(), resolver,
                surfaceMovementFactor, voluntarySpeedFactor);

        final CharacterMovementState result;
        try {
            result = motor.tick(state, command, environment, false);
        } catch (IllegalArgumentException exception) {
            throw new GameTestAssertException(label + " motor rejected real Villager collision resolution: requested="
                    + requestedTrace.get() + ", actual=" + actualTrace.get() + ", position=" + worldPosition
                    + ", velocity=" + worldVelocity + ", onGround=" + villager.onGround() + ", reason="
                    + exception.getMessage());
        }

        require(calls.get() == 1, label + " must invoke the real collision resolver once");
        requireFinite(result, label);
        Vec3 actualPosition = villager.position();
        require(close(result.position().x(), actualPosition.x())
                        && close(result.position().y(), actualPosition.y())
                        && close(result.position().z(), actualPosition.z()),
                label + " result must equal real Villager position; requested=" + requestedTrace.get()
                        + ", actual=" + actualTrace.get() + ", result=" + result.position()
                        + ", villager=" + actualPosition);
        require(result.onGround() == villager.onGround(), label + " ground state must match the real Villager");
        if (requirePositiveStep) {
            MovementVector requested = requestedTrace.get();
            MovementVector actual = actualTrace.get();
            double yRounding = Math.ulp(worldPosition.y()) + Math.ulp(worldPosition.y() + requested.y());
            BlockPos obstacle = helper.absolutePos(new BlockPos(4, 1, 2));
            double slabTop = obstacle.getY() + .5d;
            require(requested.x() > 0d && actual.x() > 0d,
                    label + " must make positive horizontal progress into the slab; requested=" + requested
                            + ", actual=" + actual);
            require(requested.y() > 0d && actual.y() > requested.y() + yRounding,
                    label + " must prove real collision stepping raised the Villager beyond its requested jump rise; requested="
                            + requested + ", actual=" + actual + ", yRounding=" + yRounding);
            require(!result.onGround() && !villager.onGround(),
                    label + " upward step must retain the real Villager's airborne ground state; requested="
                            + requested + ", actual=" + actual);
            require(Math.abs(villager.position().y() - slabTop) <= .01d,
                    label + " stepped position must be at the slab top; slabTop=" + slabTop + ", actual="
                            + actual + ", position=" + villager.position());
            LOGGER.info("{} real Villager step proof: requested={}, actual={}, position={}, slabTop={}",
                    label, requested, actual, villager.position(), slabTop);
        }
        villager.setDeltaMovement(result.velocity().x(), result.velocity().y(), result.velocity().z());
        return result;
    }

    private static void placeAndGround(Villager villager, double x, double y, double z) {
        villager.setPos(x, y, z);
        villager.setDeltaMovement(Vec3.ZERO);
        villager.move(MoverType.SELF, new Vec3(0d, -.05d, 0d));
    }

    private static void requireInObstacleFootprint(CharacterMovementState state, GameTestHelper helper, String label) {
        BlockPos obstacle = helper.absolutePos(new BlockPos(4, 1, 2));
        MovementVector position = state.position();
        require(position.x() >= obstacle.getX() - 1d && position.x() <= obstacle.getX() + 1.5d
                        && position.y() >= obstacle.getY() - .1d && position.y() <= obstacle.getY() + 1.5d
                        && position.z() >= obstacle.getZ() - .5d && position.z() <= obstacle.getZ() + 1.5d,
                label + " resolved position escaped the sensible slab footprint: " + position
                        + ", obstacle=" + obstacle);
    }

    private static void requireFinite(CharacterMovementState state, String label) {
        MovementVector position = state.position();
        MovementVector velocity = state.velocity();
        require(Double.isFinite(position.x()) && Double.isFinite(position.y()) && Double.isFinite(position.z())
                        && Double.isFinite(velocity.x()) && Double.isFinite(velocity.y()) && Double.isFinite(velocity.z()),
                label + " motor result must be finite: " + state);
    }

    private static MovementVector vector(Vec3 value) {
        return new MovementVector(value.x, value.y, value.z);
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= 1e-9d;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
