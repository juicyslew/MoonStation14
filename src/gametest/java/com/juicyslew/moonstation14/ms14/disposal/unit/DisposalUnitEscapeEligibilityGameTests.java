package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
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

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "movement", "character", "containment"})
@PrefixGameTestTemplate(false)
public final class DisposalUnitEscapeEligibilityGameTests {
    private DisposalUnitEscapeEligibilityGameTests() { }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_escape_gates", timeoutTicks = 40)
    public static void sleepingAndStunnedBodiesCannotEscape(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos unitPos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        var body = liveHuman(helper, new BlockPos(4, 1, 3));
        require(unit.insertBody(body), "unit accepts live body");

        body.startSleeping(body.blockPosition());
        require(!CharacterControlSystem.canMove(body), "sleep blocks shared movement eligibility");
        require(!unit.tryEscape(body) && unit.bodyIds().contains(body.getUUID()), "sleeping body cannot escape or lose containment");
        body.stopSleeping();
        require(CharacterControlSystem.applyStun(body, 20), "real stun status applied");
        require(!CharacterControlSystem.canMove(body), "stun blocks shared movement eligibility");
        require(!unit.tryEscape(body) && unit.bodyIds().contains(body.getUUID()), "stunned body cannot escape or lose containment");
        helper.runAfterDelay(21, () -> {
            require(CharacterControlSystem.canMove(body), "expired stun restores movement eligibility");
            require(unit.tryEscape(body), "eligible body exits through real unit path");
            require(unit.bodyIds().isEmpty(), "successful escape removes unit membership");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14_camera_tour", template = "disposal_clear", batch = "disposals_escape_gates", timeoutTicks = 40)
    public static void escapeCooldownIsUnitScopedAndExactlyTenTicks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos unitPos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(unitPos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(unitPos);
        var first = liveHuman(helper, new BlockPos(4, 1, 3));
        var second = liveHuman(helper, new BlockPos(5, 1, 3));
        require(unit.insertBody(first) && unit.insertBody(second), "unit accepts both live bodies");
        long firstAttemptTick = level.getGameTime();
        require(unit.tryEscape(first), "first body's initial exit is allowed");
        require(!unit.bodyIds().contains(first.getUUID()) && unit.bodyIds().contains(second.getUUID()), "first exit preserves second membership");
        require(unit.insertBody(first), "first body can re-enter during cooldown");

        second.startSleeping(second.blockPosition());
        helper.runAfterDelay(9, () -> {
            require(level.getGameTime() - firstAttemptTick == 9, "nine actual world ticks elapsed");
            require(!unit.tryEscape(second), "second body's attempt is blocked by unit-wide cooldown");
            second.stopSleeping();
            require(!unit.tryEscape(second), "failed eligible attempt before boundary remains blocked");
            require(!unit.tryEscape(first), "re-entered first body cannot bypass cooldown");
            helper.runAfterDelay(1, () -> {
                require(level.getGameTime() - firstAttemptTick == 10, "exactly ten actual world ticks elapsed");
                require(unit.tryEscape(second), "another body may exit at the ten-tick boundary");
                require(unit.bodyIds().contains(first.getUUID()), "successful second exit leaves re-entered first body contained");
                helper.succeed();
            });
        });
    }

    private static com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity liveHuman(
            GameTestHelper helper, BlockPos pos) {
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), pos);
        CompoundTag saved = new CompoundTag(), binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(saved);
        body.setNoAi(true);
        require(body.playerCharacterBinding() != null, "normal player-character binding restored");
        require(CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID), "human enrollment");
        require(com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(body)
                && com.juicyslew.moonstation14.ms14.lung.LungSystem.reconcile(body)
                && BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "live body and hands initialized");
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        require(hands != null && hands.compatible(body) && !hands.handIds().isEmpty(), "actual compatible live hands exist");
        return body;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
