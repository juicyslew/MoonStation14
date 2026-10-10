package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
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

import java.util.List;

@GameTestTags({"hands", "storage"})
@PrefixGameTestTemplate(false)
public final class LiveGridStateGameTests {
    private LiveGridStateGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void pureGridInsertExtractPreservesTokenAnchorAndSnapshot(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands held = initial.putWhole(0, "left", new ItemToken("bag"),
                    new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            LiveHands worn = BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1,
                    true, () -> true) == BodyEquipmentTransfer.Result.SUCCESS
                    ? body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) : null;
            require(worn != null, "bag equipped");

            List<String> declaredSlots = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .map(LiveHands.EquipmentSlot::id).toList();
            LiveHands withTwoSources = worn.putWhole(2, "left", new ItemToken("first"),
                    new ItemStack(Items.APPLE)).state().putWhole(3, "right", new ItemToken("second"),
                    new ItemStack(Items.STICK)).state();
            GridGeometry.Cell firstAnchor = new GridGeometry.Cell(0, 0);
            LiveHands first = withTwoSources.insertEquippedGrid(4, declaredSlots, "back", "bag",
                    "left", "first", firstAnchor, GridGeometry.Rotation.DEG_0).orElseThrow();
            LiveHands second = first.insertEquippedGrid(5, declaredSlots, "back", "bag",
                    "right", "second", new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0).orElseThrow();
            require(second.revision() == 6 && second.token("left").isEmpty()
                    && withTwoSources.token("left").orElseThrow().equals("first"), "pure inputs unchanged");
            LiveHands extracted = second.extractEquippedGrid(6, declaredSlots, "back", "bag",
                    "left", "first", firstAnchor).orElseThrow();
            require(extracted.token("left").orElseThrow().equals("first")
                    && extracted.stackCopy("left").orElseThrow().is(Items.APPLE), "exact child extracted");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
