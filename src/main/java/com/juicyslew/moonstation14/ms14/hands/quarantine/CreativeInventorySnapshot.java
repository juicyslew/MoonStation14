package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detached, inert Creative carrier item data, NOT a transfer authorization or park presence marker.
 * Slots 0..35 are Inventory.items, 36..39 armor, 40 offhand, 41 menu carried cursor.
 * EnderItems and any other extra compartment are OUT OF SCOPE and must be empty at capture.
 * The player attachment binds an account and supplies the separate park presence marker.
 */
public final class CreativeInventorySnapshot {
    public static final int MAIN = 36;
    public static final int ARMOR = 4;
    public static final int OFFHAND = 1;
    public static final int SLOT_COUNT = 42;
    public static final int MAX_BYTES = 1_048_576;
    private static final int VERSION = 1;

    /** Uses the caller's registry-aware ops for native stack components, without losing NBT schema checks. */
    public static final Codec<CreativeInventorySnapshot> CODEC = new Codec<>() {
            @Override public <T> DataResult<T> encode(CreativeInventorySnapshot value, DynamicOps<T> ops, T prefix) {
                try {
                    return DataResult.success(NbtOps.INSTANCE.convertTo(ops, value.encodeWithOps(ops)));
                } catch (RuntimeException failure) {
                    return DataResult.error(failure::getMessage);
                }
            }

            @Override public <T> DataResult<Pair<CreativeInventorySnapshot, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    Tag tag = ops.convertTo(NbtOps.INSTANCE, input);
                    if (!(tag instanceof CompoundTag compound))
                        return DataResult.error(() -> "Snapshot must be a compound");
                    return DataResult.success(Pair.of(decodeWithOps(compound, ops), input));
                } catch (RuntimeException failure) {
                    return DataResult.error(failure::getMessage);
                }
            }
    };

    private final List<ItemStack> slots;
    private final int selected;

    private CreativeInventorySnapshot(List<ItemStack> slots, int selected) {
        if (slots.size() != SLOT_COUNT || selected < 0 || selected > 8)
            throw new IllegalArgumentException("Incomplete inventory or invalid hotbar selection");
        List<ItemStack> detached = new ArrayList<>(SLOT_COUNT);
        for (ItemStack stack : slots) {
            Objects.requireNonNull(stack, "slot");
            // getItem() masks the actual item as AIR on every empty stack, including zero-count
            // non-AIR items. Only the known vanilla empty singleton can prove its raw identity.
            if (stack.isEmpty() ? stack != ItemStack.EMPTY || stack.getCount() != 0
                    : stack.getCount() <= 0 || stack.getCount() > stack.getMaxStackSize()
                    || stack.getItem() == Items.AIR)
                throw new IllegalArgumentException("Invalid or overstacked item");
            detached.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        this.slots = List.copyOf(detached);
        this.selected = selected;
    }

    /** Explicit trusted-source input only. Neither the lists nor their stacks are mutated. */
    public static CreativeInventorySnapshot capture(List<ItemStack> main, List<ItemStack> armor,
                                                     List<ItemStack> offhand, ItemStack carried,
                                                     int selected, List<ItemStack> unsupportedExtras) {
        Objects.requireNonNull(main, "main");
        Objects.requireNonNull(armor, "armor");
        Objects.requireNonNull(offhand, "offhand");
        Objects.requireNonNull(carried, "carried");
        Objects.requireNonNull(unsupportedExtras, "unsupported extras (including EnderItems)");
        if (main.size() != MAIN || armor.size() != ARMOR || offhand.size() != OFFHAND
                || unsupportedExtras.stream().anyMatch(stack -> stack == null || stack != ItemStack.EMPTY
                        || stack.getCount() != 0))
            throw new IllegalArgumentException("Incomplete or unsupported occupied compartment");
        List<ItemStack> all = new ArrayList<>(SLOT_COUNT);
        all.addAll(main);
        all.addAll(armor);
        all.addAll(offhand);
        all.add(carried);
        return new CreativeInventorySnapshot(all, selected);
    }

    public int selectedHotbarIndex() { return selected; }

    /** Read-only value copy; never an insertion or transfer instruction. */
    public ItemStack stackCopy(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IllegalArgumentException("Invalid slot");
        ItemStack stack = slots.get(slot);
        // ItemStack.EMPTY.copy() returns the shared singleton, not a detached value.
        return stack.isEmpty() ? new ItemStack(Items.AIR, 0) : stack.copy();
    }

    public CompoundTag encode(RegistryAccess registries) {
        Objects.requireNonNull(registries, "registries");
        return encodeWithOps(RegistryOps.create(NbtOps.INSTANCE, registries));
    }

    private <T> CompoundTag encodeWithOps(DynamicOps<T> ops) {
        CompoundTag result = new CompoundTag();
        result.putInt("version", VERSION);
        result.putInt("selected", selected);
        ListTag entries = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty()) {
                Tag encoded = ops.convertTo(NbtOps.INSTANCE, ItemStack.CODEC.encodeStart(ops, stack)
                        .getOrThrow(message -> new IllegalArgumentException("Item encode: " + message)));
                if (!(encoded instanceof CompoundTag compound))
                    throw new IllegalArgumentException("Item codec returned non-compound");
                entry.put("stack", compound);
            }
            entries.add(entry);
        }
        result.put("slots", entries);
        checkSize(result);
        return result;
    }

    public static CreativeInventorySnapshot decode(CompoundTag data, RegistryAccess registries) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(registries, "registries");
        return decodeWithOps(data, RegistryOps.create(NbtOps.INSTANCE, registries));
    }

    private static <T> CreativeInventorySnapshot decodeWithOps(CompoundTag data, DynamicOps<T> ops) {
        checkSize(data);
        if (!data.getAllKeys().equals(java.util.Set.of("version", "selected", "slots"))
                || !data.contains("version", Tag.TAG_INT) || data.getInt("version") != VERSION
                || !data.contains("selected", Tag.TAG_INT) || !data.contains("slots", Tag.TAG_LIST))
            throw new IllegalArgumentException("Invalid snapshot schema");
        ListTag entries = data.getList("slots", Tag.TAG_COMPOUND);
        if (entries.size() != SLOT_COUNT) throw new IllegalArgumentException("Missing or extra slots");
        ItemStack[] slots = new ItemStack[SLOT_COUNT];
        for (Tag tag : entries) {
            CompoundTag entry = (CompoundTag) tag;
            if (!entry.contains("slot", Tag.TAG_INT)) throw new IllegalArgumentException("Missing slot ID");
            int index = entry.getInt("slot");
            if (index < 0 || index >= SLOT_COUNT || slots[index] != null)
                throw new IllegalArgumentException("Duplicate or out-of-range slot");
            if (entry.contains("stack")) {
                if (!entry.getAllKeys().equals(java.util.Set.of("slot", "stack"))
                        || !entry.contains("stack", Tag.TAG_COMPOUND))
                    throw new IllegalArgumentException("Invalid occupied slot");
                CompoundTag encoded = entry.getCompound("stack");
                if (!encoded.contains("id", Tag.TAG_STRING)
                        || encoded.contains("count") && !encoded.contains("count", Tag.TAG_INT))
                    throw new IllegalArgumentException("Missing item ID or invalid count type");
                int count = encoded.contains("count") ? encoded.getInt("count") : 1;
                if (count <= 0) throw new IllegalArgumentException("Invalid item count");
                if (!java.util.Set.of("id", "count", "components").containsAll(encoded.getAllKeys()))
                    throw new IllegalArgumentException("Unexpected item data");
                ResourceLocation id = ResourceLocation.tryParse(encoded.getString("id"));
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                        || id.equals(BuiltInRegistries.ITEM.getKey(Items.AIR)))
                    throw new IllegalArgumentException("Unknown item ID");
                ItemStack stack = ItemStack.CODEC.parse(ops, NbtOps.INSTANCE.convertTo(ops, encoded))
                        .getOrThrow(message -> new IllegalArgumentException("Item decode: " + message));
                if (stack.isEmpty() || stack.getCount() != count)
                    throw new IllegalArgumentException("Invalid item count");
                Tag canonical = ops.convertTo(NbtOps.INSTANCE, ItemStack.CODEC.encodeStart(ops, stack)
                        .getOrThrow(message -> new IllegalArgumentException("Item re-encode: " + message)));
                if (!encoded.equals(canonical))
                    throw new IllegalArgumentException("Noncanonical or unsupported item data");
                slots[index] = stack;
            } else {
                if (!entry.getAllKeys().equals(java.util.Set.of("slot")))
                    throw new IllegalArgumentException("Invalid empty slot");
                slots[index] = ItemStack.EMPTY;
            }
        }
        for (ItemStack slot : slots) if (slot == null) throw new IllegalArgumentException("Missing slot");
        return new CreativeInventorySnapshot(List.of(slots), data.getInt("selected"));
    }

    private static void checkSize(CompoundTag tag) {
        try {
            OutputStream bounded = new OutputStream() {
                private int count;
                @Override public void write(int value) throws IOException {
                    if (++count > MAX_BYTES) throw new IOException("Snapshot exceeds byte limit");
                }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (length > MAX_BYTES - count) throw new IOException("Snapshot exceeds byte limit");
                    count += length;
                }
            };
            NbtIo.write(tag, new DataOutputStream(bounded));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid or oversized snapshot NBT", exception);
        }
    }
}
