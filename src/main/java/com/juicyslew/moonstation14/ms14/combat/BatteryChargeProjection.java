package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Read-only charge display for a cell. An absent state uses the resolved fresh-cell default. */
public final class BatteryChargeProjection {
    public record Result(float charge, float capacity, int percent, String text) { }
    private BatteryChargeProjection() { }

    public static Result project(ItemStack cell, Map<ResourceLocation, BatteryProfile> profiles) {
        if (cell == null || cell.isEmpty() || profiles == null) return null;
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cell.getItem());
        BatteryProfile profile = profiles.get(id);
        if (profile == null || !profile.tags().contains(ModBatteryProfiles.POWER_CELL_TAG)) return null;
        BatteryState state = cell.get(ModDataComponents.BATTERY_STATE.get());
        float charge = state == null ? profile.startingCharge() : Math.min(state.charge(), profile.capacity());
        int percent = Math.max(0, Math.min(100, Math.round(charge * 100f / profile.capacity())));
        return new Result(charge, profile.capacity(), percent,
                String.format(java.util.Locale.ROOT, "%.0f/%.0f J  %d%%", charge, profile.capacity(), percent));
    }
}
