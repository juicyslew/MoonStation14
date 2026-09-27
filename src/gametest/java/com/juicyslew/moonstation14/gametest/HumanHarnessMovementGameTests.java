package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HumanHarnessMovementGameTests {
    private static final double DT = .05d;

    private HumanHarnessMovementGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void realHumanVillagerMovesAndJumpsThroughHarnessMotor(GameTestHelper helper) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        BlockPos spawn = new BlockPos(3, 1, 3);
        Villager villager = helper.spawn(EntityType.VILLAGER, spawn);
        villager.setNoAi(true);
        require(CharacterIdentitySystem.resolve(villager)
                        .filter(ModCharacters.require(helper.getLevel(), ModCharacters.HUMAN_ID)::equals)
                        .isPresent(),
                "real Villager must resolve its enrolled moonstation14:human character before motor use");

        BlockPos absoluteSpawn = helper.absolutePos(spawn);
        double floorY = absoluteSpawn.getY();
        placeAndGround(villager, absoluteSpawn.getX() + .5d, floorY, absoluteSpawn.getZ() + .5d);
        require(villager.onGround(), "real Villager must begin grounded on stone");

        GroundedHarnessMotor motor = new GroundedHarnessMotor(com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.fromCharacterData(
                ModCharacters.require(helper.getLevel(), ModCharacters.HUMAN_ID)));
        double startZ = villager.position().z();
        for (int tick = 0; tick < 20; tick++) {
            CharacterMovementState result = tick(villager, motor, 0, 1_000, false, 0d,
                    "forward-" + tick);
            require(result.onGround() && villager.onGround(),
                    "forward movement must retain stable stone support at tick " + tick);
            require(Math.abs(villager.position().y() - floorY) < 1e-9d,
                    "forward movement must remain at floor height at tick " + tick);
            require(villager.position().z() > startZ, "forward input must move the real Villager");
            require(Math.hypot(villager.getDeltaMovement().x(), villager.getDeltaMovement().z())
                            <= 2.5d + 1e-9d,
                    "observed horizontal speed must remain bounded by HUMAN speed");
        }

        double beforeJumpY = villager.position().y();
        CharacterMovementState jump = tick(villager, motor, 0, 0, true, 0d, "grounded-jump");
        double rise = villager.position().y() - beforeJumpY;
        require(Math.abs(rise - .42d) <= 1e-9d,
                "first grounded jump tick must rise by approximately .42 blocks; rise=" + rise);
        require(!jump.onGround() && !villager.onGround(), "jump result and real Villager must be airborne");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sharedWorldStepAppliesHumanAndPigProfiles(GameTestHelper helper) {
        for (int x = 1; x <= 20; x++) {
            for (int z = 1; z <= 20; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos humanSpawn = new BlockPos(3, 1, 3);
        BlockPos pigSpawn = new BlockPos(6, 1, 6);
        Villager human = helper.spawn(EntityType.VILLAGER, humanSpawn);
        var pig = helper.spawn(EntityType.PIG, pigSpawn);
        BlockPos absoluteHuman = helper.absolutePos(humanSpawn);
        BlockPos absolutePig = helper.absolutePos(pigSpawn);
        placeAndGround(human, absoluteHuman.getX() + .5d, absoluteHuman.getY(), absoluteHuman.getZ() + .5d);
        placeAndGround(pig, absolutePig.getX() + .5d, absolutePig.getY(), absolutePig.getZ() + .5d);
        require(human.onGround() && pig.onGround(), "both real mobs begin grounded on stone");
        human.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        pig.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        var humanLease = GroundedHarnessLease.tryAcquire(human).orElseThrow();
        var pigLease = GroundedHarnessLease.tryAcquire(pig).orElseThrow();
        GroundedHarnessWorldStep sharedStep = new GroundedHarnessWorldStep();
        // Keep the probe centered and before any test-structure boundary; acceleration reaches cap quickly.
        for (int tick = 0; tick < 5; tick++) {
            require(sharedStep.step(human, 0, 1000, false, false, 0f).isPresent(),
                    "shared world step accepts owned HUMAN Villager");
            require(sharedStep.step(pig, 0, 1000, false, false, 0f).isPresent(),
                    "same shared world step accepts owned Pig");
        }
        require(Math.abs(human.getDeltaMovement().z * 20d - 2.5d) < 1e-8,
                "shared adapter resolves HUMAN walk policy at 2.5 blocks/second");
        require(Math.abs(pig.getDeltaMovement().z * 20d - 4d) < .02d,
                "shared adapter resolves Pig walk policy at 4 blocks/second; actual="
                        + pig.getDeltaMovement().z * 20d + ", position=" + pig.position()
                        + ", grounded=" + pig.onGround());
        humanLease.close();
        pigLease.close();
        helper.succeed();
    }

    private static CharacterMovementState tick(Villager villager, GroundedHarnessMotor motor,
                                                int wishX, int wishZ, boolean jump, double yaw, String label) {
        Vec3 before = villager.position();
        Vec3 delta = villager.getDeltaMovement();
        CharacterMovementState state = new CharacterMovementState(vector(before),
                new MovementVector(delta.x * 20d, delta.y * 20d, delta.z * 20d), villager.onGround());
        AtomicInteger calls = new AtomicInteger();
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            require(calls.incrementAndGet() == 1, label + " must resolve collision exactly once");
            Vec3 actualBefore = villager.position();
            villager.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 actualAfter = villager.position();
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(actualAfter.x - actualBefore.x, actualAfter.y - actualBefore.y,
                            actualAfter.z - actualBefore.z), villager.onGround());
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(DT, 32d, 8.4d, .98d,
                MovementVector.ZERO, 0d, villager.maxUpStep(), resolver);
        CharacterMovementState result = motor.tick(state, wishX, wishZ, jump, false, yaw, environment, false);
        require(calls.get() == 1, label + " must invoke one real entity move");
        Vec3 after = villager.position();
        require(close(result.position().x(), after.x) && close(result.position().y(), after.y)
                        && close(result.position().z(), after.z),
                label + " motor position must match the single real collision move");
        require(result.onGround() == villager.onGround(), label + " ground result must match the real Villager");
        villager.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d,
                result.velocity().z() / 20d);
        return result;
    }

    private static void placeAndGround(Mob mob, double x, double y, double z) {
        mob.setPos(x, y, z);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.move(MoverType.SELF, new Vec3(0d, -.05d, 0d));
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
