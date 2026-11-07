package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Optional;

/** Selects integral battery first, otherwise the actual nested Svalinn cell stack. */
public final class ChargerBatteryResolver {
    private ChargerBatteryResolver() { }
    public record Target(ItemStack owner, boolean nested, BatteryProfile profile, BatteryState state) {
        public Target { owner = owner.copy(); }
        @Override public ItemStack owner() { return owner.copy(); }
        public ItemStack apply(float charge) {
            ItemStack result = owner.copy();
            result.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(charge));
            return result;
        }
    }
    public static Optional<Target> resolve(ItemStack item, Map<ResourceLocation, GunProfile> guns,
                                           Map<ResourceLocation, BatteryProfile> batteries) {
        if (item == null || item.isEmpty() || guns == null || batteries == null) return Optional.empty();
        ResourceLocation itemId = GunAmmoProviderKernel.itemId(item);
        GunProfile gun = guns.get(itemId);
        if (gun != null && gun.batteryAmmoProvider().isPresent()) {
            BatteryProfile own = batteries.get(itemId);
            if (own != null) return Optional.of(new Target(item, false, own, item.get(ModDataComponents.BATTERY_STATE.get())));
            GunPowerCellSlotState slot = item.get(ModDataComponents.POWER_CELL_SLOT_STATE.get());
            if (slot == null || slot.cell().isEmpty()) return Optional.empty();
            ItemStack cell = slot.cell().orElseThrow();
            BatteryProfile nested = batteries.get(GunAmmoProviderKernel.itemId(cell));
            return nested == null ? Optional.empty() : Optional.of(new Target(cell, true, nested, cell.get(ModDataComponents.BATTERY_STATE.get())));
        }
        BatteryProfile cell = batteries.get(itemId);
        if (cell == null || gun != null) return Optional.empty();
        return Optional.of(new Target(item, false, cell, item.get(ModDataComponents.BATTERY_STATE.get())));
    }

    /** One-time slot admission initialization; caller commits returned copy, never call per tick. */
    public static Optional<ItemStack> prepareAcceptedStack(ItemStack item, ChargerProfile charger,
            Map<ResourceLocation, GunProfile> guns, Map<ResourceLocation, BatteryProfile> batteries) {
        var target = resolve(item, guns, batteries);
        if (target.isEmpty() || !accepted(item, target.get(), charger, guns)) return Optional.empty();
        Target value = target.get();
        BatteryState state = value.state() == null ? new BatteryState(value.profile().startingCharge()) : value.state();
        return Optional.of(value.nested() ? writeBack(item, value, state.charge()) : value.state() == null ? value.apply(state.charge()) : item.copy());
    }

    public static boolean accepted(ItemStack item, Target target, ChargerProfile charger, Map<ResourceLocation, GunProfile> guns) {
        if (item == null || item.isEmpty() || target == null || charger == null) return false;
        GunProfile gun = guns.get(GunAmmoProviderKernel.itemId(item));
        if (gun != null && gun.batteryAmmoProvider().isPresent())
            return charger.acceptedCapabilities().contains(ChargerCapability.BATTERY_AMMO_PROVIDER);
        return gun == null && charger.acceptedCapabilities().contains(ChargerCapability.POWER_CELL);
    }
    /** Applies an updated nested cell without replacing its identity or other item data. */
    public static ItemStack writeBack(ItemStack original, Target target, float charge) {
        if (target == null || !Float.isFinite(charge) || charge < 0 || charge > target.profile().capacity())
            throw new IllegalArgumentException("charge outside resolved battery capacity");
        ItemStack changed = target.apply(charge);
        if (!target.nested()) return changed;
        ItemStack gun = original.copy();
        gun.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(), new GunPowerCellSlotState(Optional.of(changed)));
        return gun;
    }

    public static ItemStack writeBatteryCharge(ItemStack original, Target target, float charge) {
        return writeBack(original, target, charge);
    }
}
