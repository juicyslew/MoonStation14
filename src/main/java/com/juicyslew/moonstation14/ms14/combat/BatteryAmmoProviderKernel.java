package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Optional;

/** Pure copy-on-write battery firing and cell-slot transitions. */
public final class BatteryAmmoProviderKernel {
    private BatteryAmmoProviderKernel() {}

    public record SlotResult(boolean accepted, ItemStack gun, Optional<ItemStack> returnedCell) {
        public SlotResult { gun = gun.copy(); returnedCell = returnedCell.map(ItemStack::copy); }
        @Override public ItemStack gun() { return gun.copy(); }
        @Override public Optional<ItemStack> returnedCell() { return returnedCell.map(ItemStack::copy); }
    }

    public static GunAmmoProviderKernel.TakeResult take(ItemStack stagedGun, GunProfile profile,
            Map<ResourceLocation, BatteryProfile> batteries, Map<ResourceLocation, ProjectileProfile> projectiles, int modeIndex) {
        var invalid = new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.INVALID, stagedGun, null, Optional.empty(), Optional.empty());
        if (profile.batteryAmmoProvider().isEmpty()) return invalid;
        ResourceLocation ammo; float cost;
        if (profile.batteryFireModes().isPresent()) {
            var modes = profile.batteryFireModes().orElseThrow();
            if (modeIndex < 0 || modeIndex >= modes.size()) return invalid;
            var mode = modes.get(modeIndex); ammo = mode.ammoPrototype(); cost = mode.cost();
        } else { var provider = profile.batteryAmmoProvider().orElseThrow(); ammo = provider.ammoPrototype(); cost = provider.cost(); }
        if (!projectiles.containsKey(ammo)) return invalid;
        ItemStack out = stagedGun.copy();
        boolean slotted = profile.cellSlot().isPresent();
        ItemStack power;
        BatteryProfile bp;
        if (slotted) {
            var slot = out.get(ModDataComponents.POWER_CELL_SLOT_STATE.get());
            if (slot == null || slot.cell().isEmpty()) return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.EMPTY,out,null,Optional.empty(),Optional.empty());
            power = slot.cell().orElseThrow();
            bp = batteries.get(GunAmmoProviderKernel.itemId(power));
            var slotDef = profile.cellSlot().orElseThrow();
            if (bp == null || !bp.tags().stream().anyMatch(slotDef.acceptedTags()::contains)) return invalid;
        } else { power = out; bp = batteries.get(GunAmmoProviderKernel.itemId(out)); }
        if (bp == null) return invalid;
        BatteryState state = power.get(ModDataComponents.BATTERY_STATE.get());
        if (state == null) state = new BatteryState(bp.startingCharge());
        if (state.charge() > bp.capacity()) return invalid;
        if (state.charge() < cost) return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.EMPTY,out,null,Optional.empty(),Optional.empty());
        power.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(state.charge()-cost));
        if (slotted) out.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(), new GunPowerCellSlotState(Optional.of(power)));
        else out.set(ModDataComponents.BATTERY_STATE.get(), power.get(ModDataComponents.BATTERY_STATE.get()));
        return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.READY,out,ammo,Optional.empty(),Optional.empty());
    }

    public static SlotResult insert(ItemStack gun, GunProfile profile, ItemStack offered, Map<ResourceLocation,BatteryProfile> batteries) {
        if(profile.cellSlot().isEmpty() || offered.isEmpty()) return new SlotResult(false,gun,Optional.empty());
        var bp=batteries.get(GunAmmoProviderKernel.itemId(offered)); var slot=profile.cellSlot().orElseThrow();
        if(bp==null || bp.tags().stream().noneMatch(slot.acceptedTags()::contains)) return new SlotResult(false,gun,Optional.empty());
        ItemStack out=gun.copy(); var old=out.get(ModDataComponents.POWER_CELL_SLOT_STATE.get());
        if(old!=null && old.cell().isPresent()) return new SlotResult(false,gun,Optional.empty());
        out.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.of(offered)));
        return new SlotResult(true,out,Optional.empty());
    }
    public static SlotResult eject(ItemStack gun) {
        var slot=gun.get(ModDataComponents.POWER_CELL_SLOT_STATE.get());
        if(slot==null || slot.cell().isEmpty()) return new SlotResult(false,gun,Optional.empty());
        ItemStack out=gun.copy(); out.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.empty()));
        return new SlotResult(true,out,slot.cell());
    }
}
