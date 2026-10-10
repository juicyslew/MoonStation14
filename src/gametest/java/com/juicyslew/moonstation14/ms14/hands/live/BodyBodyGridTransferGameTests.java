package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class BodyBodyGridTransferGameTests {
    private BodyBodyGridTransferGameTests() { }
    private static final GridGeometry.Cell ZERO = new GridGeometry.Cell(0, 0);
    private static final GridGeometry.Cell ONE = new GridGeometry.Cell(1, 0);
    private static final GridGeometry.Rotation R0 = GridGeometry.Rotation.DEG_0;

    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"equipment-regression"})
    public static void directMoves(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            List<String> declaredEquipment = LiveHands.equipmentSnapshot(body, initial).orElseThrow().slots().stream()
                    .map(LiveHands.EquipmentSlot::id).toList();
            require(declaredEquipment.size() == 15 && declaredEquipment.containsAll(List.of("belt", "back")),
                    "resolved human equipment slots should include belt/back (found " + declaredEquipment + ")");
            ItemStack belt = new ItemStack(ModItems.BELT.get());
            belt.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("child", new ItemStack(Items.APPLE, 3), 0, 0, R0))));
            ItemStack legacy = new ItemStack(ModItems.BAG.get());
            legacy.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.empty());
            var state = initial.putWhole(0, "left", new ItemToken("worn"), belt).state()
                    .equip(1, declaredEquipment, "left", "belt", "worn").orElseThrow()
                    .putWhole(2, "left", new ItemToken("left-host"), legacy).state()
                    .putWhole(3, "right", new ItemToken("right-host"), new ItemStack(ModItems.BELT.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state);
            var worn = new LiveHands.GridHost(true, "belt", "worn");
            var left = new LiveHands.GridHost(false, "left", "left-host");
            var right = new LiveHands.GridHost(false, "right", "right-host");
            require(state.moveBodyGrid(4, declaredEquipment, worn, left, "child", ONE, R0, ZERO, R0).isEmpty(), "old anchor");
            require(state.moveBodyGrid(4, declaredEquipment, worn, left, "child", ZERO, R0, new GridGeometry.Cell(7, 0), R0).isEmpty(), "fit");
            require(state.moveBodyGrid(4, declaredEquipment, worn, worn, "child", ZERO, R0, ZERO, R0).isEmpty(), "same host");
            require(state.moveBodyGrid(4, declaredEquipment, worn,
                    new LiveHands.GridHost(false, "left", "changed"), "child", ZERO, R0, ZERO, R0).isEmpty(), "host changed");
            require(BodyBodyGridTransfer.moveOwned(body, worn, left, "child", 4, ZERO, R0, ZERO, R0, () -> false)
                    == BodyBodyGridTransfer.Result.DENIED, "authority");
            require(BodyBodyGridTransfer.moveOwned(body, worn, left, "child", 4, ZERO, R0, ZERO, R0, () -> true,
                    (carrier, next) -> { throw new IllegalStateException(); }) == BodyBodyGridTransfer.Result.DENIED, "pre write");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == state, "no mutation");
            require(BodyBodyGridTransfer.moveOwned(body, worn, left, "child", 4, ZERO, R0, ZERO, R0, () -> true)
                    == BodyBodyGridTransfer.Result.SUCCESS, "worn to held");
            var moved = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(moved.revision() == 5 && moved.activeHand().equals(state.activeHand()), "single revision no active gating");
            require(BodyBodyGridTransfer.moveOwned(body, worn, left, "child", 4, ZERO, R0, ZERO, R0, () -> true)
                    == BodyBodyGridTransfer.Result.DENIED, "stale");
            require(BodyBodyGridTransfer.moveOwned(body, left, right, "child", 5, ZERO, R0, ONE, R0, () -> true)
                    == BodyBodyGridTransfer.Result.SUCCESS, "held to held");
            var held = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(held.revision() == 6 && GridContents.read(held.stackCopy("left").orElseThrow()).orElseThrow().entries().isEmpty(), "source empty");
            require(BodyBodyGridTransfer.moveOwned(body, right, worn, "child", 6, ONE, R0, ZERO, R0, () -> true,
                    (carrier, next) -> { carrier.setData(ModDataAttachments.LIVE_HANDS.get(), next); throw new IllegalStateException(); })
                    == BodyBodyGridTransfer.Result.RECOVERY_REQUIRED, "post write recovery");
            var recovered = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var entries = GridContents.read(LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(s -> s.id().equals("belt")).findFirst().orElseThrow().stack().orElseThrow()).orElseThrow().entries();
            require(recovered.revision() == 7 && entries.size() == 1 && entries.getFirst().token().equals("child")
                    && entries.getFirst().stack().getCount() == 3, "conservation");
            var legacyState = recovered.moveBodyGrid(7, declaredEquipment, worn, left, "child", ZERO, R0, ZERO, R0);
            require(legacyState.isPresent(), "migrated legacy grid can move back to host");
            ItemStack occupied = new ItemStack(ModItems.BAG.get());
            occupied.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("blocker", new ItemStack(Items.STICK), 0, 0, R0))));
            var blocked = initial.putWhole(0, "left", new ItemToken("worn"), belt).state()
                    .equip(1, declaredEquipment, "left", "belt", "worn").orElseThrow()
                    .putWhole(2, "left", new ItemToken("left-host"), occupied).state();
            require(blocked.moveBodyGrid(3, declaredEquipment, worn, left, "child", ZERO, R0, ZERO, R0).isEmpty(), "occupied");
            ItemStack duplicate = new ItemStack(ModItems.BAG.get());
            duplicate.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("child", new ItemStack(Items.STICK), 1, 0, R0))));
            var collision = initial.putWhole(0, "left", new ItemToken("worn"), belt).state()
                    .equip(1, declaredEquipment, "left", "belt", "worn").orElseThrow()
                    .putWhole(2, "left", new ItemToken("left-host"), duplicate).state();
            require(collision.moveBodyGrid(3, declaredEquipment, worn, left, "child", ZERO, R0, ZERO, R0).isEmpty(), "duplicate child");
            helper.succeed();
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
