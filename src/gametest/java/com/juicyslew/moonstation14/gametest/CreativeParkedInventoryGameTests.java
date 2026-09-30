package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeParkedInventoryGameTests {
    private CreativeParkedInventoryGameTests() { }

    @GameTest(template = "empty")
    public static void playerAttachmentSurvivesVanillaSaveAndLoad(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        ServerPlayer first = player(helper, account);
        require(!first.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()), "absence is not parked");
        require(CreativeParkedInventory.existingFor(first).isEmpty(), "unregistered absence cannot claim authority");
        require(!first.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()), "query does not create attachment");
        require(CreativeParkedInventory.existingFor(null).isEmpty(), "null cannot claim authority");
        List<ItemStack> main = new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("parked only"));
        main.set(0, named);
        main.set(35, new ItemStack(Items.STONE, 32));
        List<ItemStack> armor = List.of(new ItemStack(Items.DIAMOND_BOOTS), ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND_CHESTPLATE), ItemStack.EMPTY);
        ItemStack cursor = new ItemStack(Items.EMERALD, 7);
        CreativeInventorySnapshot snapshot = CreativeInventorySnapshot.capture(main, armor,
                List.of(new ItemStack(Items.SHIELD)), cursor, 8, List.of());
        first.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(),
                new CreativeParkedInventory(account, snapshot));
        named.set(DataComponents.CUSTOM_NAME, Component.literal("changed source"));
        cursor.shrink(3);
        CompoundTag saved = first.saveWithoutId(new CompoundTag());
        require(saved.contains("Inventory", Tag.TAG_LIST), "vanilla inventory co-saved");
        require(saved.getList("Inventory", Tag.TAG_COMPOUND).isEmpty(), "park never writes vanilla inventory");
        require(saved.contains("neoforge:attachments", Tag.TAG_COMPOUND), "attachment co-saved");

        ServerPlayer second = player(helper, account);
        second.load(saved);
        CreativeParkedInventory loaded = second.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
        require(loaded != null && loaded.account().equals(account), "account-bound attachment restored");
        require(CreativeParkedInventory.existingFor(second).isEmpty(), "matching unregistered fixture is not connected authority");
        require(second.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == loaded,
                "denied query does not change restored park");
        require(loaded.snapshot().selectedHotbarIndex() == 8, "selection restored");
        require(loaded.snapshot().stackCopy(0).getHoverName().getString().equals("parked only"), "component isolated");
        require(loaded.snapshot().stackCopy(35).getCount() == 32, "main restored");
        require(loaded.snapshot().stackCopy(36).is(Items.DIAMOND_BOOTS)
                && loaded.snapshot().stackCopy(38).is(Items.DIAMOND_CHESTPLATE), "armor restored");
        require(loaded.snapshot().stackCopy(40).is(Items.SHIELD), "offhand restored");
        require(loaded.snapshot().stackCopy(41).getCount() == 7, "cursor restored");
        require(second.getInventory().items.get(0).isEmpty(), "player inventory not populated from park");
        loaded.snapshot().stackCopy(0).set(DataComponents.CUSTOM_NAME, Component.literal("changed output"));
        require(first.getData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()).snapshot()
                .stackCopy(0).getHoverName().getString().equals("parked only"), "old player not aliased");
        require(loaded.snapshot().stackCopy(0).getHoverName().getString().equals("parked only"), "read isolated");
        var body = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new net.minecraft.core.BlockPos(1, 1, 1));
        require(!body.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()), "body is not account carrier");
        body.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(), loaded);
        require(CreativeParkedInventory.existingFor(second).isEmpty(), "pig attachment cannot satisfy player query");
        require(body.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == loaded,
                "denial does not mutate pig attachment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyPresenceAndMalformedPayload(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        ServerPlayer source = player(helper, account);
        CreativeInventorySnapshot empty = CreativeInventorySnapshot.capture(
                Collections.nCopies(36, ItemStack.EMPTY), Collections.nCopies(4, ItemStack.EMPTY),
                List.of(ItemStack.EMPTY), ItemStack.EMPTY, 0, List.of());
        source.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(),
                new CreativeParkedInventory(account, empty));
        CompoundTag saved = source.saveWithoutId(new CompoundTag());
        ServerPlayer restored = player(helper, account);
        restored.load(saved);
        require(restored.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()), "empty park persists");
        for (int i = 0; i < 42; i++)
            require(restored.getData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()).snapshot()
                    .stackCopy(i).isEmpty(), "empty slot " + i);
        require(CreativeParkedInventory.CODEC.parse(net.minecraft.resources.RegistryOps.create(
                net.minecraft.nbt.NbtOps.INSTANCE, helper.getLevel().registryAccess()), new CompoundTag())
                .error().isPresent(), "missing account or snapshot rejected");
        var ops = net.minecraft.resources.RegistryOps.create(
                net.minecraft.nbt.NbtOps.INSTANCE, helper.getLevel().registryAccess());
        Tag encoded = CreativeParkedInventory.CODEC.encodeStart(ops,
                new CreativeParkedInventory(account, empty)).getOrThrow();
        require(CreativeParkedInventory.CODEC.parse(ops, encoded).result()
                .filter(park -> park.account().equals(account)).isPresent(), "matching park codec round-trip");
        CompoundTag extra = ((CompoundTag) encoded).copy();
        extra.putInt("unexpected", 1);
        require(CreativeParkedInventory.CODEC.parse(ops, extra).error().isPresent(),
                "unknown outer field rejected");
        UUID wrongAccount = UUID.randomUUID();
        ServerPlayer mismatchedSource = player(helper, account);
        mismatchedSource.setData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get(),
                new CreativeParkedInventory(wrongAccount, empty));
        CompoundTag mismatchedSave = mismatchedSource.saveWithoutId(new CompoundTag());
        ServerPlayer mismatchedRestored = player(helper, account);
        mismatchedRestored.load(mismatchedSave);
        CreativeParkedInventory wrong = mismatchedRestored.getExistingDataOrNull(
                ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
        require(wrong != null && wrong.account().equals(wrongAccount), "mismatched saved park restored as data");
        require(CreativeParkedInventory.existingFor(mismatchedRestored).isEmpty(),
                "mismatched saved account cannot claim holder authority");
        require(mismatchedRestored.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) == wrong,
                "denial does not remove or replace mismatched park");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(id, "parked-fixture"), ClientInformation.createDefault());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
