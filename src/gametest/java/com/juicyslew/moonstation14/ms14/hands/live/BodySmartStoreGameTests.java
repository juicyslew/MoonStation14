package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BodySmartStoreGameTests {
    private BodySmartStoreGameTests() { }

    public static void bagSmartStoreIsOrderedAndLifo(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), initial.putWhole(0, "left", new ItemToken("bag"),
                    new ItemStack(ModItems.BAG.get())).state());
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "wear bag");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state(body).putWhole(2, "left", new ItemToken("pick"),
                    new ItemStack(ModItems.PICKAXE.get())).state());
            LiveHands before = state(body);
            deny(body, "back", "bag", "left", "wrong", 3, true, "wrong source");
            deny(body, "back", "wrong", "left", "pick", 3, true, "wrong host");
            deny(body, "back", "bag", "left", "pick", 2, true, "stale store");
            require(state(body) == before, "denials leave attachment unchanged");
            success(body, "back", "bag", "left", "pick", 3, true);
            require(state(body).revision() == 4 && state(body).token("left").isEmpty(), "pick transferred once");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state(body).putWhole(4, "right", new ItemToken("stack"),
                    new ItemStack(Items.APPLE, 17)).state());
            success(body, "back", "bag", "right", "stack", 5, true);
            var entries = entries(body, "back");
            require(entries.size() == 2 && entries.get(0).token().equals("pick") && entries.get(1).token().equals("stack")
                    && entries.get(0).x() == 0 && entries.get(0).y() == 0
                    && entries.get(1).x() == 3 && entries.get(1).y() == 0, "row-major first fit and append order");
            Villager loaded = EntityType.VILLAGER.create(body.level());
            require(loaded != null, "load fixture");
            loaded.load(body.saveWithoutId(new CompoundTag()));
            require(GridContents.read(LiveHands.equipmentSnapshot(loaded).orElseThrow().slots().stream()
                    .filter(s -> s.id().equals("back")).findFirst().orElseThrow().stack().orElseThrow())
                    .orElseThrow().entries().stream().map(GridContents.Entry::token).toList()
                    .equals(java.util.List.of("pick", "stack")), "save/load preserves recency");
            LiveHands both = state(body);
            deny(body, "back", "bag", "left", "pick", 6, false, "not last");
            deny(body, "back", "bag", "right", "wrong", 6, false, "wrong last token");
            deny(body, "back", "bag", "left", "stack", 5, false, "stale take");
            require(state(body) == both, "take denials inert");
            success(body, "back", "bag", "left", "stack", 6, false);
            require(state(body).revision() == 7 && state(body).stackCopy("left").orElseThrow().getCount() == 17,
                    "newest whole stack first");
            deny(body, "back", "bag", "left", "pick", 7, false, "still occupied");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state(body).move(7, "left", "right").orElseThrow());
            success(body, "back", "bag", "left", "pick", 8, false);
            require(state(body).revision() == 9 && state(body).stackCopy("left").orElseThrow().is(ModItems.PICKAXE)
                    && entries(body, "back").isEmpty(), "previous T pickaxe follows");
            helper.succeed();
        });
    }

    public static void beltRejectsNoSpaceAndNestedSource(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            body.setData(ModDataAttachments.LIVE_HANDS.get(), LiveHands.initialize(body).orElseThrow()
                    .putWhole(0, "left", new ItemToken("belt"), new ItemStack(ModItems.BELT.get())).state());
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "wear belt");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state(body).putWhole(2, "left", new ItemToken("pick"),
                    new ItemStack(ModItems.PICKAXE.get())).state());
            LiveHands blocked = state(body);
            deny(body, "belt", "belt", "left", "pick", 3, true, "T cannot fit two-row belt");
            require(state(body) == blocked, "no-space denial inert");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state(body).move(3, "left", "right").orElseThrow());
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state(body).putWhole(4, "left", new ItemToken("apple"),
                    new ItemStack(Items.APPLE, 23)).state());
            success(body, "belt", "belt", "left", "apple", 5, true);
            require(entries(body, "belt").size() == 1 && entries(body, "belt").getFirst().x() == 0
                    && entries(body, "belt").getFirst().y() == 0, "belt first fit within 8x2");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), state(body).putWhole(6, "left", new ItemToken("nested"),
                    new ItemStack(ModItems.BAG.get())).state());
            deny(body, "belt", "belt", "left", "nested", 7, true, "nested child denied with space");
            Villager full = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));
            var cells = new java.util.ArrayList<GridContents.Entry>();
            for (int y = 0; y < 2; y++) for (int x = 0; x < 8; x++)
                cells.add(new GridContents.Entry("cell-" + x + "-" + y, new ItemStack(Items.STICK), x, y,
                        GridGeometry.Rotation.DEG_0));
            ItemStack packed = GridContents.applyCopyWithGrid(new ItemStack(ModItems.BELT.get()),
                    GridContents.of(cells)).orElseThrow();
            full.setData(ModDataAttachments.LIVE_HANDS.get(), LiveHands.initialize(full).orElseThrow()
                    .putWhole(0, "left", new ItemToken("full-belt"), packed).state());
            require(BodyEquipmentTransfer.transferOwned(full, "left", "belt", "full-belt", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "wear packed belt");
            full.setData(ModDataAttachments.LIVE_HANDS.get(), state(full).putWhole(2, "left", new ItemToken("extra"),
                    new ItemStack(Items.APPLE, 4)).state());
            deny(full, "belt", "full-belt", "left", "extra", 3, true, "packed belt has no space");
            require(entries(full, "belt").size() == 16 && state(full).stackCopy("left").orElseThrow().getCount() == 4,
                    "full belt retains all entries and source");
            helper.succeed();
        });
    }

    private static LiveHands state(Villager body) { return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static java.util.List<GridContents.Entry> entries(Villager body, String slot) {
        return GridContents.read(LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                .filter(s -> s.id().equals(slot)).findFirst().orElseThrow().stack().orElseThrow())
                .orElseThrow().entries();
    }
    private static void success(Villager body, String slot, String host, String hand, String token, long revision, boolean store) {
        var before = entries(body, slot).stream().map(GridContents.Entry::token).toList();
        var result = BodyEquippedStorageTransfer.smartOwned(body, slot, host, hand, token, revision, store, () -> true);
        var after = entries(body, slot).stream().map(GridContents.Entry::token).toList();
        require(result == BodyEquippedStorageTransfer.Result.SUCCESS, "smart " + (store ? "store " : "take ")
                + token + " result=" + result + " grid before=" + before + " after=" + after);
    }
    private static void deny(Villager body, String slot, String host, String hand, String token, long revision,
                             boolean store, String label) {
        LiveHands before = state(body);
        require(BodyEquippedStorageTransfer.smartOwned(body, slot, host, hand, token, revision, store, () -> true)
                == BodyEquippedStorageTransfer.Result.DENIED && state(body) == before, label);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
