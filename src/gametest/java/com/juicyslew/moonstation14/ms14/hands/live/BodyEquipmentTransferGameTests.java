package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyEquipmentTransferGameTests {
    private BodyEquipmentTransferGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyHandEquipAndRemoveFilledBeltAfterSave(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands initial = LiveHands.initialize(body).orElseThrow();
            ItemStack belt = PouchContents.put(new ItemStack(ModItems.BELT.get()), "child",
                    new ItemStack(Items.DIAMOND, 11)).orElseThrow();
            LiveHands occupied = initial.putWhole(0, "left", new ItemToken("belt"), belt).state()
                    .putWhole(1, "right", new ItemToken("other"), new ItemStack(Items.APPLE, 3)).state();
            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
            CompoundTag oldTag = (CompoundTag) LiveHands.CODEC.encodeStart(ops, occupied).getOrThrow();
            oldTag.remove("equipment");
            LiveHands old = LiveHands.CODEC.parse(ops, oldTag).getOrThrow();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), old);
            require(!LiveHands.equipmentSnapshot(body).orElseThrow().persisted(), "old save has no equipment");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 2, true, () -> false)
                    == BodyEquipmentTransfer.Result.DENIED && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == old,
                    "lost authority preserves old snapshot");
            require(BodyEquipmentTransfer.equip(null, "left", "belt", "belt", 2)
                    == BodyEquipmentTransfer.Result.DENIED, "no active controller cannot equip");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 2, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "legacy equip succeeds");
            LiveHands equipped = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(equipped.revision() == 3 && equipped.stackCopy("left").isEmpty()
                    && equipped.token("right").orElseThrow().equals("other")
                    && LiveHands.equipmentSnapshot(body).orElseThrow().persisted(), "hands preserved and layout materialized");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 2, true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "stale revision denied");
            require(BodyEquipmentTransfer.transferOwned(body, "right", "belt", "other", 3, true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "occupied equipment denied");
            require(BodyEquipmentTransfer.transferOwned(body, "right", "belt", "belt", 3, false, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "occupied hand cannot swap");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "bad", 3, false, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "wrong token denied");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "other", 3, false, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED, "token already in other hand cannot move");
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            require(restored != null, "restored villager created");
            restored.load(body.saveWithoutId(new CompoundTag()));
            LiveHands saved = restored.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(saved != null && saved.revision() == 3 && saved.token("right").orElseThrow().equals("other")
                    && PouchContents.read(LiveHands.equipmentSnapshot(restored).orElseThrow().slots().get(0)
                    .stack().orElseThrow()).orElseThrow().child().orElseThrow().stack().getCount() == 11,
                    "save/load retains hands and belt child");
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "belt", 3, false, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "unequip into empty hand");
            LiveHands removed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(removed.revision() == 4 && removed.token("left").orElseThrow().equals("belt")
                    && PouchContents.read(removed.stackCopy("left").orElseThrow()).orElseThrow()
                    .child().orElseThrow().stack().getCount() == 11
                    && LiveHands.equipmentSnapshot(body).orElseThrow().slots().get(0).stack().isEmpty(),
                    "filled belt moves intact without spilling child");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void onlyRegisteredEquipmentAndValidContents(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            LiveHands empty = LiveHands.initialize(body).orElseThrow();
            for (ItemStack candidate : List.of(new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(ModItems.POUCH.get()), new ItemStack(ModItems.BAG.get()))) {
                LiveHands held = empty.putWhole(0, "left", new ItemToken("test"), candidate).state();
                body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
                require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "test", 1, true, () -> true)
                        == BodyEquipmentTransfer.Result.DENIED && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held,
                        "foreign belt item rejected");
            }
            ItemStack hostile = new ItemStack(ModItems.BELT.get());
            hostile.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE))));
            LiveHands held = empty.putWhole(0, "left", new ItemToken("hostile"), hostile).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "belt", "hostile", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held,
                    "native container cannot enter equipment");
            LiveHands bag = empty.putWhole(0, "left", new ItemToken("bag"), new ItemStack(ModItems.BAG.get())).state();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), bag);
            require(BodyEquipmentTransfer.transferOwned(body, "left", "back", "bag", 1, true, () -> true)
                    == BodyEquipmentTransfer.Result.SUCCESS, "registered bag accepted in back");
            LiveHands equipped = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(CharacterControlSystem.applyStun(body, 40), "bound body accepts stun");
            require(BodyEquipmentTransfer.transferOwned(body, "right", "back", "bag", 2, false, () -> true)
                    == BodyEquipmentTransfer.Result.DENIED
                    && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == equipped,
                    "stun blocks equipment action");
            helper.succeed();
        });
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
