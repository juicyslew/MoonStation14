package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Focused native-player inventory drop guard coverage; does not cover connection or ghost control. */
@PrefixGameTestTemplate(false)
@GameTestTags({"medical-state", "death", "storage"})
public final class NativeCorpseInventoryGameTests {
    private NativeCorpseInventoryGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void inventoryDropRetainsPolicyCorpseNativeGear(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "native-medical-corpse-inventory"));
        player.setPos(helper.absolutePos(new BlockPos(2, 1, 2)).getX() + .5, 1,
                helper.absolutePos(new BlockPos(2, 1, 2)).getZ() + .5);
        require(helper.getLevel().addFreshEntity(player), "FakePlayer joins the server level");
        helper.runAfterDelay(1, () -> {
            ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
            player.getInventory().setItem(38, armor);
            require(MobStateSystem.policy(player) != null, "native player resolves a medical policy");

            MobStateSystem.NativeDeathEvents.onLivingDeath(
                    new LivingDeathEvent(player, helper.getLevel().damageSources().generic()));
            require(MobStateSystem.isDead(player), "native death event places policy player in Dead state");
            player.getInventory().dropAll();

            require(ItemStack.isSameItemSameComponents(player.getInventory().getItem(38), armor)
                            && player.getInventory().getItem(38).getCount() == 1,
                    "dead policy player's native armor remains in inventory after dropAll");
            require(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            player.getBoundingBox().inflate(4)).stream()
                            .noneMatch(drop -> drop.getItem().is(Items.DIAMOND_CHESTPLATE)),
                    "dropAll created no duplicate armor item entity");
            player.discard();
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
