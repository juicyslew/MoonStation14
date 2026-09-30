package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Inert inspection of an already-loaded, account-bound 1.21.1 vanilla player tag. Not an
 * authorization, file reader, restore instruction, or proof of a completed save. The menu
 * carried cursor is not persisted in playerdata and is never inferred from this view.
 */
public final class VanillaPlayerDataInventoryView {
    public enum Availability { UNAVAILABLE, AVAILABLE }
    public enum SupportedComparison { UNAVAILABLE, MATCH, MISMATCH }
    public enum CursorComparison { UNKNOWN }

    private static final int SLOTS = 41;
    private static final Set<String> ITEM_FIELDS = Set.of("id", "count", "components");
    private final Availability availability;
    private final ItemStack[] slots;
    private final int selected;

    private VanillaPlayerDataInventoryView(Availability availability, ItemStack[] slots, int selected) {
        this.availability = availability;
        this.slots = slots;
        this.selected = selected;
    }

    /**
     * A null tag means the trusted caller found no player file; it is NOT an empty inventory.
     * The required expectedAccount is the trusted server's binding to that loaded tag, even
     * when the optional vanilla UUID field is absent. Callers must not derive it from this tag.
     */
    public static VanillaPlayerDataInventoryView inspect(CompoundTag loaded, RegistryAccess registries,
                                                          UUID expectedAccount) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(expectedAccount, "trusted account binding");
        if (loaded == null)
            return new VanillaPlayerDataInventoryView(Availability.UNAVAILABLE, null, -1);
        checkSize(loaded);
        if (loaded.contains("UUID")) {
            if (!loaded.contains("UUID", Tag.TAG_INT_ARRAY) || loaded.getIntArray("UUID").length != 4
                    || !loaded.getUUID("UUID").equals(expectedAccount))
                throw new IllegalArgumentException("Playerdata UUID mismatch or malformed UUID");
        }
        if (!loaded.contains("Inventory", Tag.TAG_LIST)
                || !loaded.contains("SelectedItemSlot", Tag.TAG_INT)
                || !loaded.contains("EnderItems", Tag.TAG_LIST))
            throw new IllegalArgumentException("Missing required playerdata inventory fields");
        ListTag inventory = loaded.getList("Inventory", Tag.TAG_COMPOUND);
        ListTag ender = loaded.getList("EnderItems", Tag.TAG_COMPOUND);
        // getList returns a new empty list on an element-type mismatch. Check the actual list type.
        if (loaded.get("Inventory") instanceof ListTag rawInventory
                && !rawInventory.isEmpty() && rawInventory.getElementType() != Tag.TAG_COMPOUND
                || loaded.get("EnderItems") instanceof ListTag rawEnder
                && !rawEnder.isEmpty() && rawEnder.getElementType() != Tag.TAG_COMPOUND
                || !ender.isEmpty())
            throw new IllegalArgumentException("Malformed inventory or occupied unsupported compartment");
        int selected = loaded.getInt("SelectedItemSlot");
        if (selected < 0 || selected > 8) throw new IllegalArgumentException("Invalid selected hotbar slot");
        ItemStack[] slots = new ItemStack[SLOTS];
        Arrays.fill(slots, ItemStack.EMPTY);
        boolean[] seen = new boolean[SLOTS];
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (Tag entryTag : inventory) {
            CompoundTag entry = (CompoundTag) entryTag;
            if (!entry.contains("Slot", Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing byte Slot");
            int raw = entry.getByte("Slot") & 255;
            int slot = raw <= 35 ? raw : raw >= 100 && raw <= 103 ? raw - 100 + 36
                    : raw == 150 ? 40 : -1;
            if (slot < 0 || seen[slot]) throw new IllegalArgumentException("Duplicate or unsupported inventory Slot");
            seen[slot] = true;
            CompoundTag item = entry.copy();
            item.remove("Slot");
            if (!item.contains("id", Tag.TAG_STRING)
                    || item.contains("count") && !item.contains("count", Tag.TAG_INT)
                    || !ITEM_FIELDS.containsAll(item.getAllKeys()))
                throw new IllegalArgumentException("Malformed saved item");
            int count = item.contains("count") ? item.getInt("count") : 1;
            if (count <= 0) throw new IllegalArgumentException("Invalid saved item count");
            ResourceLocation id = ResourceLocation.tryParse(item.getString("id"));
            if (id == null || !registries.registryOrThrow(Registries.ITEM).containsKey(id)
                    || id.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.AIR)))
                throw new IllegalArgumentException("Unknown or empty item ID");
            ItemStack stack = ItemStack.CODEC.parse(ops, item)
                    .getOrThrow(message -> new IllegalArgumentException("Saved item decode: " + message));
            if (stack.isEmpty() || stack.getCount() != count
                    || stack.getCount() > stack.getMaxStackSize())
                throw new IllegalArgumentException("Invalid saved item count");
            Tag canonical = ItemStack.CODEC.encodeStart(ops, stack)
                    .getOrThrow(message -> new IllegalArgumentException("Saved item re-encode: " + message));
            if (!item.equals(canonical)) throw new IllegalArgumentException("Noncanonical saved item");
            slots[slot] = stack.copy();
        }
        return new VanillaPlayerDataInventoryView(Availability.AVAILABLE, slots, selected);
    }

    public Availability availability() { return availability; }
    public CursorComparison cursorComparison() { return CursorComparison.UNKNOWN; }

    /** Only the persisted 41 supported slots and selection are compared; slot 41 is never read. */
    public SupportedComparison compareSupported(CreativeInventorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (availability == Availability.UNAVAILABLE) return SupportedComparison.UNAVAILABLE;
        if (selected != snapshot.selectedHotbarIndex()) return SupportedComparison.MISMATCH;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack actual = slots[i];
            ItemStack expected = snapshot.stackCopy(i);
            if (actual.getCount() != expected.getCount()
                    || !ItemStack.isSameItemSameComponents(actual, expected))
                return SupportedComparison.MISMATCH;
        }
        return SupportedComparison.MATCH;
    }

    private static void checkSize(CompoundTag tag) {
        checkStrings(tag, 0, new int[] {CreativeInventorySnapshot.MAX_BYTES});
        try {
            OutputStream bounded = new OutputStream() {
                private int count;
                @Override public void write(int value) throws IOException {
                    if (++count > CreativeInventorySnapshot.MAX_BYTES) throw new IOException("Oversized playerdata");
                }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (length > CreativeInventorySnapshot.MAX_BYTES - count) throw new IOException("Oversized playerdata");
                    count += length;
                }
            };
            NbtIo.write(tag, new DataOutputStream(bounded));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid or oversized playerdata NBT", exception);
        }
    }

    // NbtIo's UTF fallback can swallow an oversized writeUTF error; account for text
    // independently before invoking its byte-counting writer. Bound nesting as well.
    private static void checkStrings(Tag tag, int depth, int[] remaining) {
        if (depth > 64) throw new IllegalArgumentException("Playerdata NBT nesting exceeds limit");
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                charge(key, remaining);
                checkStrings(compound.get(key), depth + 1, remaining);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) checkStrings(element, depth + 1, remaining);
        } else if (tag.getId() == Tag.TAG_STRING) {
            charge(tag.getAsString(), remaining);
        }
    }

    private static void charge(String text, int[] remaining) {
        // UTF-16 character count is a lower bound on the encoded byte count.
        remaining[0] -= text.length();
        if (remaining[0] < 0) throw new IllegalArgumentException("Oversized playerdata text");
    }
}
