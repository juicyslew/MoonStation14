package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Read-only appearance data projected from resolved profiles and the item's actual component state. */
public final class GunItemAppearance {
    private GunItemAppearance() {}
    public record Projection(boolean magazineAbsent, boolean boltOpen, float containerFill,
                             float chargeFraction, boolean cellAbsent, int batteryMode) {}

    public static Projection project(ItemStack source, Map<ResourceLocation, GunProfile> guns,
            Map<ResourceLocation, AmmoContainerProfile> containers, Map<ResourceLocation, BatteryProfile> batteries) {
        if (source == null || source.isEmpty()) return empty();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(source.getItem());
        GunProfile gun = guns.get(id);
        ItemStack owned = new GunItemFactory(new PrototypeCatalog<>(guns), new PrototypeCatalog<>(containers),
                new PrototypeCatalog<>(batteries)).initializeOwnedCopy(source);
        boolean magazineAbsent = false, cellAbsent = false, boltOpen = false;
        float fill = 0, charge = 0;
        int mode = 0;
        if (gun != null) {
            var magazineState = owned.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            if (magazineState != null) {
                magazineAbsent = magazineState.magazine().isEmpty();
                boltOpen = !magazineState.boltClosed();
                ItemStack magazine = magazineState.magazine();
                if (!magazine.isEmpty()) {
                    fill = containerFill(magazine, containers);
                }
            }
            if (gun.cellSlot().isPresent()) {
                var slot = owned.get(ModDataComponents.POWER_CELL_SLOT_STATE.get());
                cellAbsent = slot == null || slot.cell().isEmpty();
            }
            if (gun.batteryAmmoProvider().isPresent() && gun.batteryFireModes().isPresent())
                mode = GunModePolicy.selectedBatteryMode(owned.get(ModDataComponents.GUN_RUNTIME_STATE.get()), gun);
            ItemStack power = owned;
            if (gun.cellSlot().isPresent()) {
                var slot = owned.get(ModDataComponents.POWER_CELL_SLOT_STATE.get());
                if (slot != null && slot.cell().isPresent()) power = slot.cell().orElseThrow();
            }
            BatteryProfile bp = batteries.get(GunAmmoProviderKernel.itemId(power));
            if (bp == null && gun.batteryAmmoProvider().isPresent()) bp = batteries.get(id);
            if (bp != null) {
                var bs = power.get(ModDataComponents.BATTERY_STATE.get());
                float value = bs == null ? bp.startingCharge() : bs.charge();
                charge = Math.max(0, Math.min(1, value / bp.capacity()));
            }
            if (gun.batteryAmmoProvider().isPresent()) {
                var counter = GunAmmoCounterProjection.project(owned, gun, containers, batteries);
                if (counter.visible() && counter.kind() == GunAmmoCounterProjection.Kind.BATTERY)
                    fill = counter.capacity() <= 0 ? 0 : Math.max(0, Math.min(1, counter.count() / (float) counter.capacity()));
            }
        } else {
            fill = containerFill(owned, containers);
            BatteryProfile bp = batteries.get(id);
            if (bp != null) {
                var bs = owned.get(ModDataComponents.BATTERY_STATE.get());
                float value = bs == null ? bp.startingCharge() : bs.charge();
                charge = Math.max(0, Math.min(1, value / bp.capacity()));
            }
        }
        return new Projection(magazineAbsent, boltOpen, fill, charge, cellAbsent, mode);
    }
    private static float containerFill(ItemStack container, Map<ResourceLocation, AmmoContainerProfile> profiles) {
        AmmoContainerProfile profile = profiles.get(GunAmmoProviderKernel.itemId(container));
        var state = container.get(ModDataComponents.AMMO_CONTAINER_STATE.get());
        if (profile == null || state == null) return 0;
        return Math.max(0, Math.min(1, state.rounds().stream().mapToInt(ItemStack::getCount).sum() / (float) profile.capacity()));
    }
    private static Projection empty() { return new Projection(false, false, 0, 0, false, 0); }
}
