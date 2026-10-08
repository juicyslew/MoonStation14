package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags("examine_policy")
public final class ExamineFreshBodyAuthorityGameTests {
    private ExamineFreshBodyAuthorityGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void freshEnrolledBodyUsesSharedDefaultWithoutInitializingState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "fresh villager enrolls as human");
        require(body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) == null,
                "fresh human has no preseeded mob-state attachment");

        ExamineAuthority.Actor actor = ExamineAuthority.fromEntity(body, 1, false, null);
        require(actor != null, "production actor adapter accepts resolved normal human profile");
        require(actor.state() == ExaminationPolicy.State.ALIVE, "shared mob-state default resolves healthy body as alive");
        require(body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) == null,
                "read-only actor examination does not materialize mob-state attachment");
        require(actor.examiner() != null, "resolved human examiner profile is retained");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
