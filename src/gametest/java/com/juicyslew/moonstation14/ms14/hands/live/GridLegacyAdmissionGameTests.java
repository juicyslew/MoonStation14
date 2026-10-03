package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Focused registration surface for regression cases exercising legacy/grid admission. */
public final class GridLegacyAdmissionGameTests {
    private GridLegacyAdmissionGameTests() { }

    public static void rejectsHiddenOwnershipWithoutConsumingWorldSource(GameTestHelper helper) {
        BodyPouchWorldRoundTripGameTests.worldAdmissionRejectsHiddenOwnership(helper);
    }

    public static void equippedLegacyBeltRetainsOversizedPickaxeOnTake(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            ItemStack legacy = PouchContents.put(new ItemStack(ModItems.BELT.get()), "old-pickaxe",
                    new ItemStack(ModItems.PICKAXE.get())).orElseThrow();
            require(GridContents.read(legacy).isEmpty(), "old belt remains incompatible with grid migration");
            LiveHands equipped = initial.putWhole(0, "left", new ItemToken("belt"), legacy).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), equipped);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip filled legacy belt");
            LiveHands worn = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyHandStateService.representable(worn, body), "worn legacy belt remains representable");
            require(BodyEquippedStorageTransfer.transferOwned(body, "belt", "belt", "left", "old-pickaxe",
                    worn.revision(), false, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS,
                    "legacy one-cell take succeeds");
            LiveHands taken = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var slot = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                    .filter(value -> value.id().equals("belt")).findFirst().orElseThrow();
            require(taken.token("left").orElseThrow().equals("old-pickaxe")
                    && taken.stackCopy("left").orElseThrow().is(ModItems.PICKAXE)
                    && slot.token().orElseThrow().equals("belt")
                    && !slot.stack().orElseThrow().has(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get())
                    && PouchContents.read(slot.stack().orElseThrow()).orElseThrow().child().isEmpty(),
                    "same pickaxe token taken while belt retains legacy pouch without migration");
            helper.succeed();
        });
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
