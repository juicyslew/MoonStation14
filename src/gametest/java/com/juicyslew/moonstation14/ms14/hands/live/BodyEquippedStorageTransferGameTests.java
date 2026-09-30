package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyEquippedStorageTransferGameTests {
    private BodyEquippedStorageTransferGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wornBagAndBeltStoreAndTakeWithoutUnequipping(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            for (String slot : List.of("back", "belt")) {
                ItemStack container = new ItemStack(slot.equals("back") ? ModItems.BAG.get() : ModItems.BELT.get());
                LiveHands held = initial.putWhole(0, "left", new ItemToken("container"), container).state();
                body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
                require(BodyEquipmentTransfer.transferOwned(body, "left", slot, "container", 1, true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "equip empty container");
                LiveHands worn = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                LiveHands withSource = worn.putWhole(2, "right", new ItemToken("child"),
                        new ItemStack(Items.DIAMOND, 7)).state();
                body.setData(ModDataAttachments.LIVE_HANDS.get(), withSource);
                require(BodyEquippedStorageTransfer.transferOwned(body, slot, "container", "right", "child", 3,
                        true, () -> false) == BodyEquippedStorageTransfer.Result.DENIED, "authority denied");
                require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == withSource,
                        "denial preserves snapshot");
                require(BodyEquippedStorageTransfer.transferOwned(body, slot, "container", "right", "child", 3,
                        true, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS, "store while worn");
                LiveHands filled = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(filled.revision() == 4 && filled.stackCopy("right").isEmpty()
                        && LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                        .filter(value -> value.id().equals(slot))
                        .anyMatch(value -> value.token().orElseThrow().equals("container")
                                && PouchContents.read(value.stack().orElseThrow()).orElseThrow().child()
                                .orElseThrow().stack().getCount() == 7), "one snapshot contains child in worn item");
                require(BodyEquippedStorageTransfer.transferOwned(body, slot, "container", "right", "child", 3,
                        false, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "stale revision");
                require(BodyEquippedStorageTransfer.transferOwned(body, slot, "wrong", "right", "child", 4,
                        false, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "wrong container token");
                require(BodyEquippedStorageTransfer.transferOwned(body, slot, "container", "right", "container", 4,
                        false, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "duplicate token");
                require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == filled, "denial stays inert");
                Villager restored = EntityType.VILLAGER.create(helper.getLevel());
                require(restored != null, "restore entity");
                restored.load(body.saveWithoutId(new CompoundTag()));
                require(LiveHands.equipmentSnapshot(restored).orElseThrow().slots().stream()
                        .filter(value -> value.id().equals(slot))
                        .anyMatch(value -> PouchContents.read(value.stack().orElseThrow()).orElseThrow()
                                .child().orElseThrow().stack().getCount() == 7), "serialized worn child");
                require(BodyEquippedStorageTransfer.transferOwned(body, slot, "container", "right", "child", 4,
                        false, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS, "take without unequip");
                LiveHands taken = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(taken.revision() == 5 && taken.token("right").orElseThrow().equals("child")
                        && taken.stackCopy("right").orElseThrow().getCount() == 7
                        && LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                        .filter(value -> value.id().equals(slot))
                        .anyMatch(value -> value.token().orElseThrow().equals("container")
                                && PouchContents.read(value.stack().orElseThrow()).orElseThrow().child().isEmpty()),
                        "worn container retained, cell empty");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oldHandSaveAndFilledContainerRejectOverwrite(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            ItemStack bag = PouchContents.put(new ItemStack(ModItems.BAG.get()), "child",
                    new ItemStack(Items.APPLE, 3)).orElseThrow();
            LiveHands held = initial.putWhole(0, "left", new ItemToken("bag"), bag).state();
            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
            CompoundTag oldTag = (CompoundTag) LiveHands.CODEC.encodeStart(ops, held).getOrThrow();
            oldTag.remove("equipment");
            body.setData(ModDataAttachments.LIVE_HANDS.get(), LiveHands.CODEC.parse(ops, oldTag).getOrThrow());
            require(BodyEquippedStorageTransfer.transferOwned(body, "back", "bag", "right", "child", 1,
                    false, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "old save not yet equipped");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "migrate on equip");
            LiveHands equipped = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquippedStorageTransfer.transferOwned(body, "back", "bag", "left", "child", 2,
                    false, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS, "take from migrated save");
            LiveHands taken = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(BodyEquippedStorageTransfer.transferOwned(body, "back", "bag", "left", "child", 3,
                    true, () -> true) == BodyEquippedStorageTransfer.Result.SUCCESS, "restore cell");
            LiveHands refilled = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(refilled.revision() == 4 && equipped.revision() == 2 && taken.revision() == 3,
                    "exactly one revision each");
            LiveHands competing = refilled.putWhole(4, "right", new ItemToken("other"),
                    new ItemStack(Items.DIAMOND)).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), competing);
            require(BodyEquippedStorageTransfer.transferOwned(body, "back", "bag", "right", "other", 5,
                    true, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "occupied cell no swap");
            require(BodyEquippedStorageTransfer.transferOwned(body, "back", "bag", "right", "child", 5,
                    false, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "occupied hand no swap");
            require(BodyEquippedStorageTransfer.transferOwned(body, "belt", "bag", "left", "child", 5,
                    false, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "wrong equipment slot");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == competing, "no failed publication");
            LiveHands empty = initial.putWhole(0, "left", new ItemToken("bag"),
                    new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), empty);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip empty bag");
            LiveHands nested = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                    .putWhole(2, "right", new ItemToken("nested"), new ItemStack(ModItems.BELT.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), nested);
            require(BodyEquippedStorageTransfer.transferOwned(body, "back", "bag", "right", "nested", 3,
                    true, () -> true) == BodyEquippedStorageTransfer.Result.DENIED, "nested container rejected");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == nested,
                    "nested denial preserves ownership");
            LiveHands overflow = initial.putWhole(0, "left", new ItemToken("bag"),
                    new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), overflow);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "equip for overflow check");
            LiveHands max = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            CompoundTag maxTag = (CompoundTag) LiveHands.CODEC.encodeStart(ops, max).getOrThrow();
            maxTag.putLong("revision", Long.MAX_VALUE);
            LiveHands atMax = LiveHands.CODEC.parse(ops, maxTag).getOrThrow();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), atMax);
            require(BodyEquippedStorageTransfer.transferOwned(body, "back", "bag", "right", "child",
                    Long.MAX_VALUE, false, () -> true) == BodyEquippedStorageTransfer.Result.DENIED,
                    "revision overflow rejected");
            require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == atMax,
                    "overflow preserves snapshot");
            helper.succeed();
        });
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
