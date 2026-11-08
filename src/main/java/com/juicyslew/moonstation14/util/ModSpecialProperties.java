package com.juicyslew.moonstation14.util;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class ModSpecialProperties {
    public static void addCustomItemProperties(FMLClientSetupEvent event) {
        // Now you can pass any item that implements IReagentItem
        registerFillProperty(ModItems.BOTTLE.get());
        ItemProperties.register(ModItems.PILL.get(), ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "pill_type"),
                (stack, level, entity, seed) -> {
                    var data = stack.get(ModDataComponents.PILL_TYPE.get());
                    return data == null ? 0f : data.type();
                });
        registerFillProperty(ModItems.SYRINGE.get());
        ItemProperties.register(ModItems.SYRINGE.get(), ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "syringe_mode"),
                (stack, level, entity, seed) -> {
                    if (level == null) return 0f;
                    var data = stack.get(ModDataComponents.SYRINGE.get());
                    if (data == null) data = com.juicyslew.moonstation14.item.custom.SyringeItem.initialData(level);
                    return com.juicyslew.moonstation14.item.custom.SyringeItem.isInjectMode(data.mode(), level) ? 1f : 0f;
                });
        ItemProperties.register(ModItems.JUG.get(),
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "fill"),
                (stack, level, entity, seed) -> {
                    var reagent = stack.get(ModDataComponents.REAGENT.get());
                    return com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.fillTier(reagent,
                            com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.capacityCents(level)) / 6f;
                });
        registerBeakerFill(ModItems.BEAKER.get());
        registerBeakerFill(ModItems.BEAKER_LARGE.get());
        registerNetworkConfiguratorProperty();
        registerGunAppearanceProperties();
        registerCartridgeSpentProperty(ModItems.CARTRIDGE_PISTOL.get());
        registerCartridgeSpentProperty(ModItems.CARTRIDGE_RIFLE.get());
        registerCartridgeSpentProperty(ModItems.CARTRIDGE_MAGNUM.get());
        registerCartridgeSpentProperty(ModItems.CARTRIDGE_LIGHT_RIFLE.get());
        registerCartridgeSpentProperty(ModItems.SHELL_SHOTGUN.get());
    }

    private static void registerGunAppearanceProperties() {
        var items = java.util.List.of(ModItems.WEAPON_PISTOL_MK58.get(), ModItems.WEAPON_RIFLE_LECTER.get(),
                ModItems.WEAPON_REVOLVER_INSPECTOR.get(), ModItems.WEAPON_SHOTGUN_KAMMERER.get(),
                ModItems.WEAPON_SNIPER_MOSIN.get(), ModItems.WEAPON_LASER_GUN.get(),
                ModItems.WEAPON_ENERGY_SHOTGUN.get(), ModItems.WEAPON_LASER_SVALINN.get(),
                ModItems.MAGAZINE_PISTOL.get(), ModItems.MAGAZINE_RIFLE.get(), ModItems.SPEEDLOADER_MAGNUM.get(),
                ModItems.POWER_CELL_SMALL.get());
        registerGunProperty(items, "magazine_absent", p -> p.magazineAbsent() ? 1 : 0);
        registerGunProperty(items, "bolt_open", p -> p.boltOpen() ? 1 : 0);
        registerGunProperty(items, "ammo_fill", com.juicyslew.moonstation14.ms14.combat.GunItemAppearance.Projection::containerFill);
        registerGunProperty(items, "charge", com.juicyslew.moonstation14.ms14.combat.GunItemAppearance.Projection::chargeFraction);
        registerGunProperty(items, "cell_absent", p -> p.cellAbsent() ? 1 : 0);
        registerGunProperty(items, "battery_mode", com.juicyslew.moonstation14.ms14.combat.GunItemAppearance.Projection::batteryMode);
        ItemProperties.register(ModItems.WEAPON_SNIPER_MOSIN.get(), ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "solution_present"),
                (stack, level, entity, seed) -> com.juicyslew.moonstation14.util.MapOperations.getTotal(
                        stack.getOrDefault(ModDataComponents.REAGENT.get(), new ReagentComponent()).contents()) > 0 ? 1f : 0f);
    }
    private static void registerGunProperty(java.util.List<Item> items, String name,
            java.util.function.ToDoubleFunction<com.juicyslew.moonstation14.ms14.combat.GunItemAppearance.Projection> value) {
        var key = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name);
        for (Item item : items) ItemProperties.register(item, key, (stack, level, entity, seed) -> {
            var appearance = com.juicyslew.moonstation14.ms14.combat.GunItemAppearance.project(stack,
                    com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.clientCatalog().asMap(),
                    com.juicyslew.moonstation14.ms14.combat.ModAmmoContainerProfiles.clientCatalog().asMap(),
                    com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.clientCatalog().asMap());
            return (float) value.applyAsDouble(appearance);
        });
    }
    private static void registerCartridgeSpentProperty(Item item) {
        ItemProperties.register(item, ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "cartridge_spent"),
                (stack, level, entity, seed) -> com.juicyslew.moonstation14.ms14.combat.SharedCartridgeItemPresentation.spent(stack));
    }
    private static void registerBeakerFill(com.juicyslew.moonstation14.item.custom.BeakerItem item) {
        ItemProperties.register(item, ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "fill"),
                (stack, level, entity, seed) -> {
                    var data = com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers.resolved(level, item.prototypeId());
                    if (!com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.supported(data)) return 0f;
                    var reagent = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
                    return com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerItemAppearance.fillTier(reagent, data);
                });
    }

    private static void registerNetworkConfiguratorProperty() {
        ItemProperties.register(ModItems.NETWORK_CONFIGURATOR.get(),
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "configurator_mode"),
                (stack, level, entity, seed) -> {
                    var data = stack.get(ModDataComponents.NETWORK_CONFIGURATOR.get());
                    return data != null && data.mode() == NetworkConfiguratorStackData.Mode.LIST ? 1f : 0f;
                });
    }

    private static <T extends Item & IReagentTrait> void registerFillProperty(T item) {
        ItemProperties.register(
                item,
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "fill"),
                (stack, level, entity, seed) -> {
                    float currentVolume = MapOperations.getTotal(
                            stack.getOrDefault(ModDataComponents.REAGENT.get(), new ReagentComponent()).contents()
                    );
                    // Use the capacity defined in your interface!
                    float capacity;
                    if (level != null) capacity = item instanceof com.juicyslew.moonstation14.item.custom.JugItem jug
                            ? jug.getCapacity(level) : item.getCapacity(stack, level);
                    else {
                        // Null-level queries are client UI callbacks; ensure bundled resolved defaults
                        // are available before authoritative prototype sync.
                        com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.bootstrapClientBundled();
                        com.juicyslew.moonstation14.ms14.medical.ModInjectorModes.bootstrapClientBundled();
                        capacity = item.getCapacity();
                    }
                    return capacity > 0f ? Math.min(1f, currentVolume / capacity) : 0f;
                }
        );
    }
}
