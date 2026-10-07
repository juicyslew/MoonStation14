package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "movement", "containment"})
@PrefixGameTestTemplate(false)
public final class DisposalEscapeMovementGameTests {
    private DisposalEscapeMovementGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_escape_gates", timeoutTicks = 30)
    public static void sleepingOccupantCannotEscapeAndDoesNotConsumeCooldown(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos unitPos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(4, 1, 3));
        CompoundTag saved = new CompoundTag(), binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID()); binding.putString("Profile", "main"); binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding); body.readAdditionalSaveData(saved);
        body.setNoAi(true);
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "human enrollment");
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "real human hands initialized");
        require(unit.insertBody(body), "live unit accepts stuffed body");
        body.startSleeping(unitPos);
        long attemptBefore = unit.timingSnapshot().lastExitAttemptTick() == null ? -1 : unit.timingSnapshot().lastExitAttemptTick();
        require(!unit.tryEscape(body), "sleeping occupant cannot escape");
        require(unit.bodyIds().contains(body.getUUID()), "sleeping occupant remains bound");
        require(java.util.Objects.equals(attemptBefore, unit.timingSnapshot().lastExitAttemptTick() == null ? -1L : unit.timingSnapshot().lastExitAttemptTick()),
                "blocked movement does not consume the unit exit cooldown");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
