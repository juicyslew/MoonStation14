package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;

import java.util.UUID;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "loose-objects"})
public final class CreativeDropGameTests {
    private CreativeDropGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @GameTestTags({"medical-creative-drop"})
    public static void creativeDropReplacesModItemsAndLeavesVanillaDropsAlone(GameTestHelper helper) {
        FakePlayer creative = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "loosecreative"));
        creative.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.setBlock(floor, Blocks.STONE);
        BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
        creative.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        require(helper.getLevel().addFreshEntity(creative), "real Creative server player is added to the level");
        ItemStack steel = named(ModItems.STEEL.get().getDefaultInstance(), "creative-steel");
        ItemStack glass = named(ModItems.GLASS.get().getDefaultInstance(), "creative-glass");
        ItemStack bag = named(ModItems.BAG.get().getDefaultInstance(), "creative-bag");
        ItemStack diamond = named(new ItemStack(Items.DIAMOND), "vanilla-diamond");
        // Exercise the ordinary Creative UI route: this overload delegates with traceItem=false.
        creative.drop(steel, false);
        creative.drop(glass, false);
        creative.drop(bag, false);
        creative.drop(diamond, false);
        var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                creative.getBoundingBox().inflate(2), entity -> true);
        Map<String, ItemEntity> customDrops = new java.util.HashMap<>();
        for (String name : new String[]{"creative-steel", "creative-glass", "creative-bag"}) {
            var matches = dropped.stream().filter(entity -> hasName(entity.getItem(), name)).toList();
            require(matches.size() == 1 && matches.getFirst() instanceof LooseItemEntity,
                    name + " converts immediately to exactly one LooseItemEntity; found " + matches);
            customDrops.put(name, matches.getFirst());
        }
        var vanillaDrop = dropped.stream().filter(entity -> hasName(entity.getItem(), "vanilla-diamond")).toList();
        require(vanillaDrop.size() == 1 && !(vanillaDrop.getFirst() instanceof LooseItemEntity),
                "vanilla diamond remains a normal ItemEntity");
        ItemEntity vanillaDiamond = vanillaDrop.getFirst();
        // Stay out of pickup range until the conversion's randomized creative delay expires.
        creative.setPos(spawn.getX() + 20, spawn.getY(), spawn.getZ() + 20);
        helper.runAfterDelay(45, () -> {
            try {
                for (String name : new String[]{"creative-steel", "creative-glass", "creative-bag"}) {
                    require(!customDrops.get(name).isRemoved(), name + " remains available until explicit pickup");
                    LooseItemEntity loose = (LooseItemEntity) customDrops.get(name);
                    require(hasName(loose.getItem(), name),
                            name + " custom name/component survives conversion");
                    var item = loose.getItem().getItem();
                    creative.setPos(loose.getX(), loose.getY(), loose.getZ());
                    loose.playerTouch(creative);
                    require(loose.isRemoved() && creative.getInventory().countItem(item) > 0,
                            name + " admits into Creative inventory and consumes loose entity");
                    creative.setPos(spawn.getX() + 20, spawn.getY(), spawn.getZ() + 20);
                }
                require(!vanillaDiamond.isRemoved() && !(vanillaDiamond instanceof LooseItemEntity),
                        "vanilla diamond remains a normal ItemEntity");
            } finally {
                creative.discard();
            }
            helper.succeed();
        });
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static boolean hasName(ItemStack stack, String name) {
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                && stack.getHoverName().getString().equals(name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
