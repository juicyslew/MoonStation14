package com.juicyslew.moonstation14.ms14.atmos.tank;

import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record CanisterSessionSnapshot(UUID sessionId, UUID machineId, UUID bodyId, long authorityEpoch,
        ResourceLocation dimension, double x, double y, double z, long stateRevision, long slotRevision,
        long ledgerRevision, double pressureKpa, double minimumPressureKpa, double maximumPressureKpa,
        boolean valveClosed, Map<String, Double> gasMoles, double temperatureKelvin,
        ItemStack activeTank, boolean accepted) {
    public CanisterSessionSnapshot { gasMoles = Map.copyOf(gasMoles); activeTank = activeTank.copy(); }
    @Override public ItemStack activeTank() { return activeTank.copy(); }
}

