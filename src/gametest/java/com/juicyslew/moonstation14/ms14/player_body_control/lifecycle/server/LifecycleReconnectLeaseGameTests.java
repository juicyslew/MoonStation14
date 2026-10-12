package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"lifecycle-reconnect-lease"})
public final class LifecycleReconnectLeaseGameTests {
    private LifecycleReconnectLeaseGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void reconnectReacquiresGroundedLeaseAndPreservesStrictNoAiRequirement(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = ModEntities.MOUSE.get().create(level);
        require(mouse != null, "mouse created");
        mouse.setPos(helper.absolutePos(new BlockPos(2, 1, 2)).getX() + .5, 2,
                helper.absolutePos(new BlockPos(2, 1, 2)).getZ() + .5);
        require(level.addFreshEntity(mouse), "mouse added");
        CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        require(mouse.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()) != null,
                "mouse has mapped character identity");

        mouse.setNoAi(false); // GroundedHarnessLease.close() restores this native AI state on park.
        var parked = com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease
                .tryAcquireRoleTarget(mouse).orElseThrow();
        require(mouse.isNoAi(), "park lease disables vanilla AI for controlled movement");
        parked.close();
        require(!mouse.isNoAi(), "park release restores ordinary native AI state");

        var reconnect = LifecycleCharacterSessionControl.acquireReconnectRoleLease(mouse);
        require(reconnect != null && mouse.isNoAi(), "production reconnect preparation reacquires lease before session preflight");
        require(com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease
                .isOwnedRoleTargetEligible(mouse), "reacquired lease satisfies strict owned-role body eligibility");
        reconnect.close();
        require(!mouse.isNoAi(), "untransferred lease cleanup restores native AI");

        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        require(zombie != null, "unmapped mob created");
        zombie.setPos(helper.absolutePos(new BlockPos(4, 1, 2)).getX() + .5, 2,
                helper.absolutePos(new BlockPos(4, 1, 2)).getZ() + .5);
        require(level.addFreshEntity(zombie), "unmapped mob added");
        require(!LifecycleCharacterSessionControl.requiresReconnectRoleLease(zombie)
                        && LifecycleCharacterSessionControl.acquireReconnectRoleLease(zombie) == null,
                "unmapped entity type cannot acquire reconnect lease");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
