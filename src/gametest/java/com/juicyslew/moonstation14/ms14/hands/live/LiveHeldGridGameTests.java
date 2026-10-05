package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"hands", "storage"})
@PrefixGameTestTemplate(false)
public final class LiveHeldGridGameTests {
    private LiveHeldGridGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void heldBagGridInsertMoveAndExtract(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands bag = initial.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            LiveHands children = bag.putWhole(1, "right", new ItemToken("tool"), new ItemStack(Items.IRON_PICKAXE)).state();
            var anchor = new GridGeometry.Cell(0, 0);
            LiveHands stored = children.insertHeldGrid(2, "left", "bag", "right", "tool", anchor,
                    GridGeometry.Rotation.DEG_0).orElseThrow();
            require(children.token("right").orElseThrow().equals("tool") && stored.token("right").isEmpty(), "pure insertion");
            var grid = GridContents.read(stored.stackCopy("left").orElseThrow()).orElseThrow();
            require(grid.entries().size() == 1 && grid.entries().get(0).stack().is(Items.IRON_PICKAXE), "tool stored");
            require(stored.moveHeldGrid(3, "left", "bag", "tool", anchor, GridGeometry.Rotation.DEG_0,
                    new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_90).isPresent(), "internal rotation succeeds");
            require(stored.moveHeldGrid(3, "left", "bag", "tool", anchor, GridGeometry.Rotation.DEG_0,
                    new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0).isEmpty(), "no-op denied");
            LiveHands turned = stored.moveHeldGrid(3, "left", "bag", "tool", anchor, GridGeometry.Rotation.DEG_0,
                    new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_90).orElseThrow();
            LiveHands taken = turned.extractHeldGrid(4, "left", "bag", "right", "tool", new GridGeometry.Cell(1, 0)).orElseThrow();
            require(taken.token("right").orElseThrow().equals("tool") && taken.revision() == 5, "whole child returned");
            require(taken.extractHeldGrid(5, "left", "bag", "right", "tool", new GridGeometry.Cell(1, 0)).isEmpty(), "occupied hand denied");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
