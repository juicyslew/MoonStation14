package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"movement", "character"})
@PrefixGameTestTemplate(false)
public final class MovementEligibilityGameTests {
    private MovementEligibilityGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear",
            batch = "movement_eligibility", timeoutTicks = 40)
    public static void sleepingHumanCannotWalkOrJumpAndCanMoveAfterWake(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 1; x <= 9; x++)
            for (int z = 1; z <= 9; z++) level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        BlockPos spawn = helper.absolutePos(new BlockPos(4, 1, 4));
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 4));
        body.setNoAi(true);
        net.minecraft.nbt.CompoundTag bindingTag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.CompoundTag binding = new net.minecraft.nbt.CompoundTag();
        binding.putUUID("Account", java.util.UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", java.util.UUID.randomUUID());
        bindingTag.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(bindingTag);
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "HUMAN identity enrollment");
        require(CharacterIdentitySystem.resolveForActor(body).isPresent(), "live HUMAN prototype resolves");
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        body.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        body.setDeltaMovement(Vec3.ZERO);
        body.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -.05, 0));
        require(body.onGround(), "body is grounded on stone");
        var step = new GroundedHarnessWorldStep();
        body.startSleeping(body.blockPosition());
        double sleepingX = body.getX(), sleepingY = body.getY(), sleepingZ = body.getZ();
        require(!CharacterControlSystem.canMove(body), "sleep contributes to shared movement policy");
        require(step.step(body, 1000, 1000, true, false, 0f).isPresent(), "sleeping actor step is accepted");
        require(body.getX() == sleepingX && body.getZ() == sleepingZ && body.getY() <= sleepingY,
                "sleep blocks voluntary walking and jump impulse while allowing gravity");
        body.stopSleeping();
        require(CharacterControlSystem.canMove(body), "waking restores shared movement eligibility");
        double awakeZ = body.getZ();
        require(step.step(body, 0, 1000, false, false, 0f).isPresent(), "awake actor step is accepted");
        require(body.getZ() > awakeZ, "awake body advances under voluntary input");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear",
            batch = "movement_eligibility", timeoutTicks = 40)
    public static void criticalAndDeadAreMovementIneligibleButDownedAliveCanCrawl(GameTestHelper helper) {
        var level = helper.getLevel();
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 4));
        body.setNoAi(true);
        net.minecraft.nbt.CompoundTag bindingTag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.CompoundTag binding = new net.minecraft.nbt.CompoundTag();
        binding.putUUID("Account", java.util.UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", java.util.UUID.randomUUID());
        bindingTag.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(bindingTag);
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "HUMAN identity enrollment");
        require(CharacterControlSystem.canMove(body), "alive HUMAN remains movement eligible");
        com.juicyslew.moonstation14.ms14.standing.PostureSystem.forceDown(body, level.getGameTime() + 100);
        require(com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(body)
                        && CharacterControlSystem.canMove(body),
                "knockdown preserves voluntary crawling eligibility");
        require(CharacterControlSystem.applyStun(body, 20) && !CharacterControlSystem.canMove(body),
                "stunned body remains movement ineligible");
        require(DamageSystem.applyHealthChange(body, java.util.Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                        == DamageSystem.Result.APPLIED
                        && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(body),
                "typed damage enters Critical");
        require(!CharacterControlSystem.canMove(body), "Critical is not movement eligible");
        require(DamageSystem.applyHealthChange(body, java.util.Map.of(DamageKeys.BLUNT, 100f), 1f, true)
                        == DamageSystem.Result.APPLIED
                        && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(body),
                "typed damage enters Dead");
        require(!CharacterControlSystem.canMove(body), "Dead is not movement eligible");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
