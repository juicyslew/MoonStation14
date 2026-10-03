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
import java.util.ArrayList;

/** Body-owned transition and publication seam; not connected-player authority proof. */
public final class BodyWornCrossHostGameTests {
    private static final GridGeometry.Cell ZERO = new GridGeometry.Cell(0, 0);
    private static final GridGeometry.Rotation R0 = GridGeometry.Rotation.DEG_0;
    private BodyWornCrossHostGameTests() { }

    public static void moveBetweenWornHostsConservesChildrenAndRejectsStaleAndCollision(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            ItemStack belt = new ItemStack(ModItems.BELT.get());
            belt.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("child", new ItemStack(Items.APPLE, 3), 0, 0, R0),
                    new GridContents.Entry("remaining", new ItemStack(Items.STICK), 2, 0, R0))));
            LiveHands holding = initial.putWhole(0, "left", new ItemToken("belt-host"), belt).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), holding);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt-host", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "belt equipped");
            LiveHands bagHeld = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                    .putWhole(2, "left", new ItemToken("bag-host"), new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), bagHeld);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag-host", 3, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "pristine bag equipped");
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            List<String> declared = List.of("belt", "back");
            require(before.moveEquippedGridBetweenHosts(before.revision(), declared, "belt", "belt-host",
                    "back", "bag-host", "child", new GridGeometry.Cell(1, 0), R0, ZERO, R0).isEmpty(), "old anchor");
            require(before.moveEquippedGridBetweenHosts(before.revision(), declared, "belt", "belt-host",
                    "back", "bag-host", "child", ZERO, GridGeometry.Rotation.DEG_90, ZERO, R0).isEmpty(), "old rotation");
            require(before.moveEquippedGridBetweenHosts(before.revision(), declared, "belt", "belt-host",
                    "back", "bag-host", "child", ZERO, R0, new GridGeometry.Cell(7, 0), R0).isEmpty(), "out of bounds");
            require(before.moveEquippedGridBetweenHosts(before.revision(), declared, "belt", "wrong",
                    "back", "bag-host", "child", ZERO, R0, ZERO, R0).isEmpty(), "wrong host token");
            require(before.moveEquippedGridBetweenHosts(before.revision(), declared, "belt", "belt-host",
                    "belt", "belt-host", "child", ZERO, R0, ZERO, R0).isEmpty(), "same slot");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before, "pure rejections do not publish");
            require(BodyEquippedStorageTransfer.moveGridBetweenHostsOwned(body, "belt", "belt-host", "back",
                    "bag-host", "child", before.revision(), ZERO, R0, ZERO, R0, () -> false)
                    == BodyEquippedStorageTransfer.Result.DENIED, "authority denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before, "authority cannot mutate");
            require(BodyEquippedStorageTransfer.moveGridBetweenHostsOwned(body, "belt", "belt-host", "back",
                    "bag-host", "child", before.revision(), ZERO, R0, ZERO, R0,
                    () -> { throw new IllegalStateException("authority callback failed"); })
                    == BodyEquippedStorageTransfer.Result.DENIED, "callback exception cannot publish");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before,
                    "callback exception conserves single source child");
            require(BodyEquippedStorageTransfer.moveGridBetweenHostsOwned(body, "belt", "belt-host", "back",
                    "bag-host", "child", before.revision(), ZERO, R0, ZERO, R0, () -> true,
                    (carrier, next) -> { throw new IllegalStateException("write failed before publication"); })
                    == BodyEquippedStorageTransfer.Result.DENIED, "pre-publication exception denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before,
                    "failed write retains one source child");
            require(BodyEquippedStorageTransfer.moveGridBetweenHostsOwned(body, "belt", "belt-host", "back",
                    "bag-host", "child", before.revision(), ZERO, R0, ZERO, R0, () -> true)
                    == BodyEquippedStorageTransfer.Result.SUCCESS, "atomic move");
            LiveHands moved = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(moved.revision() == before.revision() + 1 && moved.token("left").isEmpty()
                    && moved.token("right").isEmpty(), "one revision; no hand intermediate");
            var slots = LiveHands.equipmentSnapshot(body).orElseThrow().slots();
            var beltEntries = GridContents.read(slots.stream().filter(s -> s.id().equals("belt"))
                    .findFirst().orElseThrow().stack().orElseThrow()).orElseThrow().entries();
            var bagEntries = GridContents.read(slots.stream().filter(s -> s.id().equals("back"))
                    .findFirst().orElseThrow().stack().orElseThrow()).orElseThrow().entries();
            require(beltEntries.size() == 1 && beltEntries.getFirst().token().equals("remaining")
                    && bagEntries.size() == 1 && bagEntries.getFirst().token().equals("child")
                    && bagEntries.getFirst().stack().getCount() == 3, "exact token and stack conserved");
            require(BodyEquippedStorageTransfer.moveGridBetweenHostsOwned(body, "belt", "belt-host", "back",
                    "bag-host", "child", before.revision(), ZERO, R0, ZERO, R0, () -> true)
                    == BodyEquippedStorageTransfer.Result.DENIED, "stale revision denied");
            require(moved.moveEquippedGridBetweenHosts(moved.revision(), declared, "back", "bag-host",
                    "belt", "belt-host", "child", ZERO, R0, new GridGeometry.Cell(2, 0), R0).isEmpty(), "collision denied");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == moved, "rejections leave one child");
            LiveHands inverse = moved.moveEquippedGridBetweenHosts(moved.revision(), declared, "back", "bag-host",
                    "belt", "belt-host", "child", ZERO, R0, new GridGeometry.Cell(1, 0), R0).orElseThrow();
            require(inverse.revision() == moved.revision() + 1 && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == moved,
                    "reverse direction remains pure");
            require(BodyEquippedStorageTransfer.moveGridBetweenHostsOwned(body, "back", "bag-host", "belt",
                    "belt-host", "child", moved.revision(), ZERO, R0, new GridGeometry.Cell(1, 0), R0, () -> true,
                    (carrier, next) -> {
                        carrier.setData(ModDataAttachments.LIVE_HANDS.get(), next);
                        throw new IllegalStateException("write succeeded before error");
                    }) == BodyEquippedStorageTransfer.Result.RECOVERY_REQUIRED, "post-publication exception requires recovery");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != moved
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).revision() == inverse.revision(),
                    "published snapshot survives recovery without rollback");
            var reverseSlots = LiveHands.equipmentSnapshot(body).orElseThrow().slots();
            var reversed = GridContents.read(reverseSlots.stream().filter(s -> s.id().equals("belt"))
                    .findFirst().orElseThrow().stack().orElseThrow()).orElseThrow().entries();
            require(reversed.size() == 2 && reversed.getFirst().token().equals("remaining")
                    && reversed.getLast().token().equals("child"), "destination appends to recency order");
            require(GridContents.read(reverseSlots.stream().filter(s -> s.id().equals("back"))
                    .findFirst().orElseThrow().stack().orElseThrow()).orElseThrow().entries().isEmpty(),
                    "recovery snapshot contains child only once");

            ItemStack legacy = new ItemStack(ModItems.BELT.get());
            legacy.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.of("oversized", new ItemStack(ModItems.PICKAXE.get())));
            Villager legacyBody = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));
            LiveHands legacyInitial = LiveHands.initialize(legacyBody).orElseThrow();
            LiveHands legacyHeld = legacyInitial.putWhole(0, "left", new ItemToken("legacy-host"), legacy).state();
            legacyBody.setData(ModDataAttachments.LIVE_HANDS.get(), legacyHeld);
            require(BodyEquipmentTransfer.transferOwned(legacyBody, "left", "belt", "legacy-host", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "legacy host worn");
            LiveHands legacyWithBag = legacyBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                    .putWhole(2, "left", new ItemToken("bag-host"), new ItemStack(ModItems.BAG.get())).state();
            legacyBody.setData(ModDataAttachments.LIVE_HANDS.get(), legacyWithBag);
            require(BodyEquipmentTransfer.transferOwned(legacyBody, "left", "back", "bag-host", 3, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "valid legacy target worn");
            LiveHands invalidSource = legacyBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(GridContents.read(legacy).isEmpty(), "oversized legacy source invalid");
            require(invalidSource.moveEquippedGridBetweenHosts(invalidSource.revision(), declared,
                    "belt", "legacy-host", "back", "bag-host", "oversized", ZERO, R0, ZERO, R0).isEmpty(),
                    "invalid legacy source denied");

            ArrayList<GridContents.Entry> filled = new ArrayList<>();
            for (int y = 0; y < 4; y++) for (int x = 0; x < 7; x++)
                filled.add(new GridContents.Entry("occupied-" + x + "-" + y, new ItemStack(Items.STICK), x, y, R0));
            ItemStack fullBag = new ItemStack(ModItems.BAG.get());
            fullBag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(filled));
            Villager fullBody = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 1));
            LiveHands fullInitial = LiveHands.initialize(fullBody).orElseThrow();
            fullBody.setData(ModDataAttachments.LIVE_HANDS.get(), fullInitial.putWhole(0, "left",
                    new ItemToken("belt-host"), belt).state());
            require(BodyEquipmentTransfer.transferOwned(fullBody, "left", "belt", "belt-host", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "full fixture belt equipped");
            fullBody.setData(ModDataAttachments.LIVE_HANDS.get(), fullBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                    .putWhole(2, "left", new ItemToken("full-bag"), fullBag).state());
            require(BodyEquipmentTransfer.transferOwned(fullBody, "left", "back", "full-bag", 3, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "full fixture bag equipped");
            LiveHands full = fullBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(full.moveEquippedGridBetweenHosts(full.revision(), declared, "belt", "belt-host", "back", "full-bag",
                    "child", ZERO, R0, ZERO, R0).isEmpty(), "full destination denied");
            require(fullBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == full, "full denial conserves owners");
            ItemStack duplicateBag = new ItemStack(ModItems.BAG.get());
            duplicateBag.set(ModDataComponents.GRID_CONTENTS.get(), GridContents.of(List.of(
                    new GridContents.Entry("child", new ItemStack(Items.APPLE), 1, 0, R0))));
            LiveHands duplicates = initial.putWhole(0, "left", new ItemToken("belt-host"), belt).state()
                    .equip(1, declared, "left", "belt", "belt-host").orElseThrow()
                    .putWhole(2, "left", new ItemToken("duplicate-host"), duplicateBag).state()
                    .equip(3, declared, "left", "back", "duplicate-host").orElseThrow();
            require(duplicates.moveEquippedGridBetweenHosts(duplicates.revision(), declared,
                    "belt", "belt-host", "back", "duplicate-host", "child", ZERO, R0, ZERO, R0).isEmpty(),
                    "duplicate nested child identity denied");
            helper.succeed();
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
