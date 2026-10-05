package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Objects;
import java.util.Optional;

/** Per-dimension durable owner for detached pipe gas snapshots. */
public final class PipeGasInventorySavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_pipe_gas_inventory";
    public static final Factory<PipeGasInventorySavedData> FACTORY = new Factory<>(
            PipeGasInventorySavedData::new, PipeGasInventorySavedData::load, null);

    private PipeGasInventory inventory;
    private PendingRawSwap pendingRawSwap;

    static final class PendingRawSwap {
        private final PipeGasInventory before;
        private final PipeGasInventory after;
        private boolean completed;
        private PendingRawSwap(PipeGasInventory before, PipeGasInventory after) { this.before = before; this.after = after; }
    }

    public PipeGasInventorySavedData() { this(new PipeGasInventory()); }

    private PipeGasInventorySavedData(PipeGasInventory inventory) {
        this.inventory = Objects.requireNonNull(inventory);
    }

    /** Non-creating read. */
    public static Optional<PipeGasInventorySavedData> existing(ServerLevel level) {
        return Optional.ofNullable(Objects.requireNonNull(level).getDataStorage().get(FACTORY, DATA_NAME));
    }

    /** The sole creating access; mutations are confined to the server thread. */
    public static PipeGasInventorySavedData forMutation(ServerLevel level) {
        Objects.requireNonNull(level);
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Pipe gas inventory mutation requires the server thread");
        PipeGasInventorySavedData loaded = level.getDataStorage().get(FACTORY, DATA_NAME);
        if (loaded != null) return loaded;
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public PipeGasInventory snapshot() { return inventory; }
    public long revision() { return inventory.revision(); }

    /** Internal server-thread compare-and-replace; this does not validate gas conservation. */
    boolean replace(long expectedRevision, PipeGasInventory beforeInventory, PipeGasInventory replacement) {
        if (pendingRawSwap != null) throw new IllegalStateException("raw pipe gas transaction is pending");
        Objects.requireNonNull(beforeInventory);
        Objects.requireNonNull(replacement);
        long nextRevision;
        try {
            nextRevision = Math.addExact(inventory.revision(), 1L);
        } catch (ArithmeticException overflow) {
            return false;
        }
        if (beforeInventory != inventory || expectedRevision != inventory.revision()
                || replacement == inventory || replacement.revision() != nextRevision) return false;
        inventory = replacement;
        setDirty();
        return true;
    }

    /** Installs one unpublished transaction image. All calls are confined to the server thread by its endpoint. */
    PendingRawSwap beginRawSwap(PipeGasInventory expectedBefore, PipeGasInventory after) {
        Objects.requireNonNull(expectedBefore); Objects.requireNonNull(after);
        long nextRevision;
        try { nextRevision = Math.addExact(expectedBefore.revision(), 1L); }
        catch (ArithmeticException overflow) { return null; }
        if (pendingRawSwap != null || inventory != expectedBefore || after == expectedBefore
                || after.revision() != nextRevision) return null;
        PendingRawSwap token = new PendingRawSwap(expectedBefore, after);
        pendingRawSwap = token;
        inventory = after;
        return token;
    }

    boolean rollbackRawSwap(PendingRawSwap token) {
        if (token == null) return false;
        if (token.completed) return inventory == token.before;
        if (pendingRawSwap == null && inventory == token.before) { token.completed = true; return true; }
        if (pendingRawSwap != token || inventory != token.after) return false;
        inventory = token.before;
        pendingRawSwap = null;
        token.completed = true;
        return true;
    }

    void publishRawSwap(PendingRawSwap token) {
        if (token == null || token.completed || pendingRawSwap != token || inventory != token.after)
            throw new IllegalStateException("raw pipe gas transaction publication invalidated");
        pendingRawSwap = null;
        token.completed = true;
        setDirty();
    }

    public static PipeGasInventorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("inventory", CompoundTag.TAG_COMPOUND))
            throw new IllegalArgumentException("Missing pipe gas inventory compound");
        PipeGasInventory decoded = PipeGasInventory.CODEC.parse(NbtOps.INSTANCE, tag.get("inventory"))
                .getOrThrow(message -> new IllegalArgumentException("Invalid pipe gas inventory: " + message));
        return new PipeGasInventorySavedData(decoded);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (pendingRawSwap != null)
            throw new IllegalStateException("Cannot save pipe gas inventory while a raw transaction is pending");
        tag.put("inventory", PipeGasInventory.CODEC.encodeStart(NbtOps.INSTANCE, inventory)
                .getOrThrow(message -> new IllegalStateException("Could not encode pipe gas inventory: " + message)));
        return tag;
    }
}
