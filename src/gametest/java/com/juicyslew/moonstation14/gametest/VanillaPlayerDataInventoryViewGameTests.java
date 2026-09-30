package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.VanillaPlayerDataInventoryView;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
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
public final class VanillaPlayerDataInventoryViewGameTests {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private static CompoundTag player() {
        CompoundTag data = new CompoundTag();
        data.putUUID("UUID", ACCOUNT);
        data.put("Inventory", new ListTag());
        data.put("EnderItems", new ListTag());
        data.putInt("SelectedItemSlot", 8);
        return data;
    }

    private static void putItem(CompoundTag data, RegistryAccess registries, int slot, ItemStack stack) {
        CompoundTag item = (CompoundTag) ItemStack.CODEC.encodeStart(
                RegistryOps.create(NbtOps.INSTANCE, registries), stack).getOrThrow();
        item.putByte("Slot", (byte) slot);
        data.getList("Inventory", 10).add(item);
    }

    private static CreativeInventorySnapshot snapshot(ItemStack mainItem, ItemStack armorItem,
                                                       ItemStack offhandItem, ItemStack cursor) {
        List<ItemStack> main = new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
        List<ItemStack> armor = new ArrayList<>(Collections.nCopies(4, ItemStack.EMPTY));
        main.set(35, mainItem);
        armor.set(3, armorItem);
        return CreativeInventorySnapshot.capture(main, armor, List.of(offhandItem), cursor, 8, List.of());
    }

    @GameTest(template = "empty")
    public static void sparseVanillaAndCursorUnknown(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag data = player();
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("component retained"));
        putItem(data, registries, 35, named);
        putItem(data, registries, 103, new ItemStack(Items.DIAMOND_HELMET));
        putItem(data, registries, 150, new ItemStack(Items.SHIELD));
        for (int i = 0; i < 3; i++) {
            CompoundTag item = data.getList("Inventory", 10).getCompound(i);
            require(!item.contains("count") || item.contains("count", net.minecraft.nbt.Tag.TAG_INT)
                    && item.getInt("count") == 1);
        }
        var view = VanillaPlayerDataInventoryView.inspect(data, registries, ACCOUNT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("changed after inspection"));
        var expected = snapshot(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.SHIELD), new ItemStack(Items.EMERALD));
        require(view.compareSupported(expected) == VanillaPlayerDataInventoryView.SupportedComparison.MISMATCH);
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("component retained"));
        var matching = snapshot(original, new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.SHIELD), new ItemStack(Items.EMERALD));
        require(view.compareSupported(matching) == VanillaPlayerDataInventoryView.SupportedComparison.MATCH);
        require(CreativeInventorySnapshot.decode(matching.encode(registries), registries).stackCopy(40).getCount() == 1);
        require(view.cursorComparison() == VanillaPlayerDataInventoryView.CursorComparison.UNKNOWN);
        var empty = VanillaPlayerDataInventoryView.inspect(player(), registries, ACCOUNT);
        require(empty.compareSupported(snapshot(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY)) == VanillaPlayerDataInventoryView.SupportedComparison.MATCH);
        var unavailable = VanillaPlayerDataInventoryView.inspect(null, registries, ACCOUNT);
        require(unavailable.availability() == VanillaPlayerDataInventoryView.Availability.UNAVAILABLE);
        require(unavailable.compareSupported(matching) == VanillaPlayerDataInventoryView.SupportedComparison.UNAVAILABLE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedPlayerdataFailsClosed(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag base = player();
        putItem(base, registries, 0, new ItemStack(Items.STONE));
        boolean canonicalHasCount = base.getList("Inventory", 10).getCompound(0).contains("count");
        CompoundTag noCount = base.copy();
        noCount.getList("Inventory", 10).getCompound(0).remove("count");
        require(ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries),
                noCount.getList("Inventory", 10).getCompound(0)).getOrThrow().getCount() == 1);
        if (canonicalHasCount) reject(noCount, registries);
        else require(VanillaPlayerDataInventoryView.inspect(noCount, registries, ACCOUNT).availability()
                == VanillaPlayerDataInventoryView.Availability.AVAILABLE);
        CompoundTag duplicate = base.copy();
        putItem(duplicate, registries, 0, new ItemStack(Items.STONE));
        reject(duplicate, registries);
        for (int slot : new int[] {36, 99, 104, 149, 151, 255}) {
            CompoundTag invalid = base.copy();
            invalid.getList("Inventory", 10).getCompound(0).putByte("Slot", (byte) slot);
            reject(invalid, registries);
        }
        CompoundTag missing = base.copy();
        missing.remove("Inventory");
        reject(missing, registries);
        missing = base.copy();
        missing.remove("SelectedItemSlot");
        reject(missing, registries);
        missing = base.copy();
        missing.remove("EnderItems");
        reject(missing, registries);
        CompoundTag mismatch = base.copy();
        mismatch.putUUID("UUID", UUID.randomUUID());
        reject(mismatch, registries);
        CompoundTag invalid = base.copy();
        invalid.getList("Inventory", 10).getCompound(0).putString("id", "moonstation14:missing_item");
        reject(invalid, registries);
        invalid = base.copy();
        invalid.getList("Inventory", 10).getCompound(0).putInt("count", 65);
        reject(invalid, registries);
        invalid = base.copy();
        invalid.getList("Inventory", 10).getCompound(0).putInt("count", 1);
        if (canonicalHasCount)
            require(VanillaPlayerDataInventoryView.inspect(invalid, registries, ACCOUNT).availability()
                    == VanillaPlayerDataInventoryView.Availability.AVAILABLE);
        else reject(invalid, registries);
        invalid = base.copy();
        invalid.getList("Inventory", 10).getCompound(0).putByte("count", (byte) 1);
        reject(invalid, registries);
        invalid = base.copy();
        invalid.getList("Inventory", 10).getCompound(0).putInt("count", 0);
        reject(invalid, registries);
        invalid = base.copy();
        invalid.getList("Inventory", 10).getCompound(0).putInt("count", -1);
        reject(invalid, registries);
        invalid = base.copy();
        CompoundTag components = new CompoundTag();
        components.putString("moonstation14:missing_component", "x");
        invalid.getList("Inventory", 10).getCompound(0).put("components", components);
        reject(invalid, registries);
        invalid = base.copy();
        invalid.getList("EnderItems", 10).add(new CompoundTag());
        reject(invalid, registries);
        invalid = base.copy();
        invalid.putInt("SelectedItemSlot", 9);
        reject(invalid, registries);
        invalid = base.copy();
        invalid.remove("UUID");
        require(VanillaPlayerDataInventoryView.inspect(invalid, registries, ACCOUNT).availability()
                == VanillaPlayerDataInventoryView.Availability.AVAILABLE);
        final CompoundTag noFile = base;
        expectFailure(() -> VanillaPlayerDataInventoryView.inspect(noFile, registries, null));
        invalid = base.copy();
        invalid.putString("padding", "x".repeat(CreativeInventorySnapshot.MAX_BYTES + 1));
        reject(invalid, registries);
        helper.succeed();
    }

    private static void reject(CompoundTag tag, RegistryAccess registries) {
        expectFailure(() -> VanillaPlayerDataInventoryView.inspect(tag, registries, ACCOUNT));
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) { return; }
        throw new net.minecraft.gametest.framework.GameTestAssertException("invalid playerdata accepted");
    }

    private static void require(boolean condition) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException("view comparison failed");
    }
}
