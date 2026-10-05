package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
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

import java.util.List;

/** State-transition fixture coverage; this does not assert connected-player request authority. */
@GameTestTags({"hands", "storage"})
@PrefixGameTestTemplate(false)
public final class BodyGridMoveGameTests {
    private BodyGridMoveGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void moveGridChildRotatesAndRejectsStaleOrInvalidPlacement(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            ItemStack bag = new ItemStack(ModItems.BAG.get());
            bag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("pickaxe", new ItemStack(ModItems.PICKAXE.get()), 0, 0,
                            GridGeometry.Rotation.DEG_0),
                    new GridContents.Entry("apple", new ItemStack(Items.APPLE, 3), 3, 0,
                            GridGeometry.Rotation.DEG_0))));
            LiveHands holdingBag = initial.putWhole(0, "left", new ItemToken("bag"), bag).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), holdingBag);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip fresh grid bag");

            LiveHands equipped = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            long revision = equipped.revision();

            require(BodyEquippedStorageTransfer.moveGridOwned(body, "back", "bag", "pickaxe", revision,
                    new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                    new GridGeometry.Cell(4, 1), GridGeometry.Rotation.DEG_90, () -> true)
                    == BodyEquippedStorageTransfer.Result.SUCCESS, "move and rotate pickaxe");
            LiveHands moved = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(moved.revision() == revision + 1, "success advances one revision");
            var slots = LiveHands.equipmentSnapshot(body).orElseThrow().slots();
            var movedBag = slots.stream().filter(slot -> slot.id().equals("back")).findFirst().orElseThrow();
            require(movedBag.token().orElseThrow().equals("bag"), "worn container token preserved");
            List<GridContents.Entry> entries = GridContents.read(movedBag.stack().orElseThrow()).orElseThrow().entries();
            require(entries.size() == 2 && entries.get(0).token().equals("pickaxe")
                    && entries.get(1).token().equals("apple"), "move preserves grid entry order");
            GridContents.Entry pickaxe = entries.stream().filter(entry -> entry.token().equals("pickaxe")).findFirst().orElseThrow();
            GridContents.Entry apple = entries.stream().filter(entry -> entry.token().equals("apple")).findFirst().orElseThrow();
            require(pickaxe.x() == 4 && pickaxe.y() == 1 && pickaxe.rotation() == GridGeometry.Rotation.DEG_90
                    && pickaxe.stack().is(ModItems.PICKAXE), "pickaxe placement/rotation/item retained");
            require(apple.x() == 3 && apple.y() == 0 && apple.stack().getCount() == 3,
                    "other child placement/token/count unchanged");
            require(moved.stackCopy("left").isEmpty() && moved.token("left").isEmpty(), "hands unchanged");

            deny(body, moved, new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                    new GridGeometry.Cell(2, 0), GridGeometry.Rotation.DEG_0, "old placement mismatch");
            deny(body, moved, new GridGeometry.Cell(4, 1), GridGeometry.Rotation.DEG_90,
                    new GridGeometry.Cell(3, 0), GridGeometry.Rotation.DEG_0, "overlap denied");
            deny(body, moved, new GridGeometry.Cell(4, 1), GridGeometry.Rotation.DEG_90,
                    new GridGeometry.Cell(7, 0), GridGeometry.Rotation.DEG_0, "out of range denied");
            require(BodyEquippedStorageTransfer.moveGridOwned(body, "back", "wrong-bag", "pickaxe", moved.revision(),
                    new GridGeometry.Cell(4, 1), GridGeometry.Rotation.DEG_90,
                    new GridGeometry.Cell(2, 1), GridGeometry.Rotation.DEG_0, () -> true)
                    == BodyEquippedStorageTransfer.Result.DENIED, "wrong container token denied");
            require(BodyEquippedStorageTransfer.moveGridOwned(body, "back", "bag", "pickaxe", revision,
                    new GridGeometry.Cell(4, 1), GridGeometry.Rotation.DEG_90,
                    new GridGeometry.Cell(2, 1), GridGeometry.Rotation.DEG_0, () -> true)
                    == BodyEquippedStorageTransfer.Result.DENIED, "stale revision denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == moved,
                    "all rejected moves preserve exact snapshot");
            helper.succeed();
        });
    }

    private static void deny(Villager body, LiveHands before, GridGeometry.Cell oldAnchor,
                             GridGeometry.Rotation oldRotation, GridGeometry.Cell destination,
                             GridGeometry.Rotation rotation, String message) {
        require(BodyEquippedStorageTransfer.moveGridOwned(body, "back", "bag", "pickaxe", before.revision(),
                oldAnchor, oldRotation, destination, rotation, () -> true)
                == BodyEquippedStorageTransfer.Result.DENIED, message);
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before, message + " leaves state untouched");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
