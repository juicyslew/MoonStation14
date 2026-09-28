package com.juicyslew.moonstation14.ms14.station.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-owned durable one-time placement marker. */
public final class StarterStationSavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_debug_starter_station";
    public static final Factory<StarterStationSavedData> FACTORY = new Factory<>(
            StarterStationSavedData::new, StarterStationSavedData::load, null);

    private StarterStationPlacementState.State state = StarterStationPlacementState.State.DISABLED;
    private BlockPos selectedOrigin;

    public static StarterStationSavedData createPending() {
        StarterStationSavedData data = new StarterStationSavedData();
        data.state = StarterStationPlacementState.State.PENDING;
        data.setDirty();
        return data;
    }

    public static StarterStationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StarterStationSavedData data = new StarterStationSavedData();
        int raw = tag.getInt("state");
        if (raw >= 0 && raw < StarterStationPlacementState.State.values().length)
            data.state = StarterStationPlacementState.State.values()[raw];
        if (tag.contains("origin")) data.selectedOrigin = BlockPos.of(tag.getLong("origin"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("state", state.ordinal());
        if (selectedOrigin != null) tag.putLong("origin", selectedOrigin.asLong());
        return tag;
    }

    public StarterStationPlacementState.State state() { return state; }
    public BlockPos selectedOrigin() { return selectedOrigin; }

    public void select(BlockPos origin) {
        if (state != StarterStationPlacementState.State.PENDING || origin == null) return;
        selectedOrigin = origin.immutable();
        state = StarterStationPlacementState.State.SELECTED;
        setDirty();
    }

    public void markPendingFromNewWorld() {
        if (state != StarterStationPlacementState.State.DISABLED) return;
        state = StarterStationPlacementState.State.PENDING;
        setDirty();
    }

    public void complete() {
        if (state != StarterStationPlacementState.State.SELECTED) return;
        state = StarterStationPlacementState.State.COMPLETED;
        setDirty();
    }
}
