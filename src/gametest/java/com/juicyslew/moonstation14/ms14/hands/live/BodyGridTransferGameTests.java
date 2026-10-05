package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
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
public final class BodyGridTransferGameTests {
    private BodyGridTransferGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void gridTransferPublishesOnceAndRejectsInvalidWrites(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            LiveHands held = initial.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip bag");
            LiveHands worn = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            LiveHands sources = worn.putWhole(2, "left", new ItemToken("first"), new ItemStack(Items.APPLE))
                    .state().putWhole(3, "right", new ItemToken("second"), new ItemStack(Items.STICK)).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), sources);
            var anchor = new GridGeometry.Cell(0, 0);
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "first", 4, anchor,
                    GridGeometry.Rotation.DEG_0, true, () -> false) == BodyEquippedStorageTransfer.Result.DENIED,
                    "authority denial");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == sources, "authority denial inert");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "first", 3, anchor,
                    GridGeometry.Rotation.DEG_0, true, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "stale revision");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "wrong", "left", "first", 4, anchor,
                    GridGeometry.Rotation.DEG_0, true, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "wrong token");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "belt", "bag", "left", "first", 4, anchor,
                    GridGeometry.Rotation.DEG_0, true, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "wrong slot");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "first", 4,
                    new GridGeometry.Cell(7, 0), GridGeometry.Rotation.DEG_0, true, () -> true)
                    == BodyEquippedStorageTransfer.Result.DENIED, "invalid placement");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == sources, "invalid writes inert");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "first", 4, anchor,
                    GridGeometry.Rotation.DEG_0, true, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS,
                    "store first");
            LiveHands one = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(one.revision() == 5 && one.token("left").isEmpty(), "single revision publication");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "right", "second", 5,
                    new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0, true, () -> true)
                    == BodyEquippedStorageTransfer.Result.SUCCESS, "store second");
            LiveHands both = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var equipment = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(s -> s.id().equals("back")).findFirst().orElseThrow();
            GridContents grid = GridContents.read(equipment.stack().orElseThrow()).orElseThrow();
            require(both.revision() == 6 && grid.entries().size() == 2, "both entries published");
            require(equipment.stack().orElseThrow().has(ModDataComponents.GRID_CONTENTS.get())
                    && !equipment.stack().orElseThrow().has(ModDataComponents.POUCH_CONTENTS.get()), "grid only component");
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "left", "first", 6,
                    anchor, null, false, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS, "take first");
            LiveHands taken = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(taken.revision() == 7 && taken.stackCopy("left").orElseThrow().is(Items.APPLE), "single take revision");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void gridStoreMigratesLegacyChildWithoutLoss(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            ItemStack legacyBag = PouchContents.put(new ItemStack(ModItems.BAG.get()), "legacy", new ItemStack(Items.APPLE, 3)).orElseThrow();
            LiveHands held = initial.putWhole(0, "left", new ItemToken("bag"), legacyBag).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip legacy bag");
            LiveHands worn = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            LiveHands withSource = worn.putWhole(2, "right", new ItemToken("child"), new ItemStack(Items.STICK)).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), withSource);
            require(BodyEquippedStorageTransfer.transferGridOwned(body, "back", "bag", "right", "child", 3,
                    new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0, true, () -> true)
                    == BodyEquippedStorageTransfer.Result.SUCCESS, "migrate and store");
            LiveHands stored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            ItemStack host = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(s -> s.id().equals("back")).findFirst().orElseThrow().stack().orElseThrow();
            var entries = GridContents.read(host).orElseThrow().entries();
            require(stored.revision() == 4 && entries.size() == 2 && !host.has(ModDataComponents.POUCH_CONTENTS.get()),
                    "migration retains both children in grid");
            require(entries.stream().anyMatch(e -> e.token().equals("legacy") && e.stack().getCount() == 3),
                    "legacy child count preserved");
            helper.succeed();
        });
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
