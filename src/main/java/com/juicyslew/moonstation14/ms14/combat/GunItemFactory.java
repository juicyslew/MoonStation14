package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Materializes profile-owned initial gun state on a private copy. */
public final class GunItemFactory {
    private final PrototypeCatalog<GunProfile> guns;
    private final PrototypeCatalog<AmmoContainerProfile> containers;
    private final PrototypeCatalog<BatteryProfile> batteries;

    public GunItemFactory(PrototypeCatalog<GunProfile> guns, PrototypeCatalog<AmmoContainerProfile> containers,
                          PrototypeCatalog<BatteryProfile> batteries) {
        this.guns = guns; this.containers = containers; this.batteries = batteries;
    }

    public static ItemStack initializeCopy(ItemStack source) {
        return new GunItemFactory(ModGunProfiles.serverCatalog(), ModAmmoContainerProfiles.serverCatalog(),
                ModBatteryProfiles.serverCatalog()).initializeOwnedCopy(source);
    }

    public ItemStack create(ResourceLocation nativeId) {
        var item = BuiltInRegistries.ITEM.getOptional(nativeId).orElseThrow(() -> new IllegalArgumentException("Unknown item " + nativeId));
        return initializeOwnedCopy(new ItemStack(item));
    }

    /** Invalid/cyclic nested profile composition throws; source is never modified. */
    public ItemStack initializeOwnedCopy(ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = source.copy();
        initialize(result, new java.util.HashSet<>());
        return result;
    }

    private void initialize(ItemStack stack, java.util.Set<ResourceLocation> path) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!path.add(id)) throw new IllegalArgumentException("Cyclic item composition: " + id);
        try {
            GunProfile gun = guns.get(id);
            if (gun != null) {
                if (gun.revolverAmmoProvider().isPresent() && stack.get(ModDataComponents.REVOLVER_STATE.get()) == null)
                    stack.set(ModDataComponents.REVOLVER_STATE.get(), RevolverProviderKernel.initialize(gun));
                gun.ballisticProvider().ifPresent(p -> { if (stack.get(ModDataComponents.BALLISTIC_STATE.get()) == null) stack.set(ModDataComponents.BALLISTIC_STATE.get(), BallisticState.full(p.capacity(), p.fillCartridgePrototype())); });
                gun.magazineChamberProvider().ifPresent(p -> {
                    if (stack.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get()) != null) return;
                    ItemStack magazine = p.startingMagazine().map(this::item).orElse(ItemStack.EMPTY);
                    ItemStack chamber = p.startingChamber().map(this::item).orElse(ItemStack.EMPTY);
                    if (!magazine.isEmpty()) initialize(magazine, path);
                    if (!chamber.isEmpty()) initialize(chamber, path);
                    stack.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(), new MagazineChamberState(magazine, chamber, p.boltClosed()));
                });
                gun.cellSlot().ifPresent(c -> { if (stack.get(ModDataComponents.POWER_CELL_SLOT_STATE.get()) == null) {
                    ItemStack cell = c.startingCell().map(this::item).orElse(ItemStack.EMPTY);
                    if (!cell.isEmpty()) initialize(cell, path);
                    stack.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(), new GunPowerCellSlotState(cell.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(cell)));
                }});
            }
            AmmoContainerProfile container = containers.get(id);
            if (container != null && stack.get(ModDataComponents.AMMO_CONTAINER_STATE.get()) == null) {
                var rounds = new java.util.ArrayList<ItemStack>();
                if (container.fillCartridgePrototype() != null) for (int i=0;i<container.capacity();i++) rounds.add(item(container.fillCartridgePrototype()));
                stack.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(rounds));
            }
            BatteryProfile battery = batteries.get(id);
            if (battery != null && stack.get(ModDataComponents.BATTERY_STATE.get()) == null) stack.set(ModDataComponents.BATTERY_STATE.get(), new BatteryState(battery.startingCharge()));
        } finally { path.remove(id); }
    }

    private ItemStack item(ResourceLocation id) {
        var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Unknown nested item " + id));
        return new ItemStack(item);
    }
}
