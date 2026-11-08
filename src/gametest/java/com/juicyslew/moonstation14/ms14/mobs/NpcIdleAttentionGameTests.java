package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags("mouse-attention")
public final class NpcIdleAttentionGameTests {
    private NpcIdleAttentionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 500)
    public static void productionIdleAttentionFacesNearbyPlayerAndYieldsToFood(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++)
            helper.getLevel().setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "mouse created by ordinary constructor");
        mouse.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        require(level.addFreshEntity(mouse), "ordinary mouse spawned into production NPC lifecycle");
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "attention"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setCamera(player);
        player.setPos(mouse.getX() + 1.5, mouse.getY(), mouse.getZ() + 1.5);
        require(level.addFreshEntity(player), "physical player entity joins loaded level");
        com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(mouse, level);
        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(mouse, level);
        awaitAttention(helper, mouse, player, 0);
    }

    private static void awaitAttention(GameTestHelper helper, com.juicyslew.moonstation14.entities.MouseMob mouse,
                                       net.minecraft.server.level.ServerPlayer player, int elapsed) {
        player.setPos(mouse.getX() + 1.5, mouse.getY(), mouse.getZ() + 1.5);
        var brain = NpcBehaviorSystem.debugSnapshot(mouse);
        if (brain != null && player.getUUID().equals(brain.attentionTarget())) {
            float expectedYaw = (float) Math.toDegrees(Math.atan2(-1, 1));
            require(Math.abs(net.minecraft.util.Mth.wrapDegrees(mouse.getYRot() - expectedYaw)) < 10
                            && Math.abs(net.minecraft.util.Mth.wrapDegrees(mouse.getYHeadRot() - expectedYaw)) < 10,
                    "production attention turns mouse body and head toward player: " + mouse.getYRot()
                            + "/" + mouse.getYHeadRot());
            mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get(),
                    new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                            new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(0)));
            var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
            var food = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                    mouse.position().add(.55, 0, 0), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
            require(level.addFreshEntity(food), "food target appears during attention");
            helper.runAfterDelay(12, () -> {
                var after = NpcBehaviorSystem.debugSnapshot(mouse);
                require(after != null && after.attentionTarget() == null
                                && after.task() == NpcBehaviorComponent.Task.FOOD,
                        "nutrition task cancels attention immediately on its production planning pass: " + after);
                helper.succeed();
            });
            return;
        }
        if (elapsed >= 420) {
            throw new GameTestAssertException("production idle attention missed nearby player: " + brain
                + " mouse=" + mouse.position() + " target=" + player.position() + " alive=" + player.isAlive()
                + " spectator=" + player.isSpectator());
        }
        helper.runAfterDelay(1, () -> awaitAttention(helper, mouse, player, elapsed + 1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
