package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GunAmmoCounterProjectionTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static GunProfile revolver() {
        var data = GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("""
                        {"gun":{"fire_rate":1,"projectile_speed":1,"use_key":false,"firing_mode":"SEMI_AUTO"},
                         "revolver_ammo_provider":{"capacity":6,"fill_cartridge_prototype":"moonstation14:cartridge_magnum",
                         "accepted_cartridge_tags":[],"accepted_speedloader_tags":[]},"ammo_counter":true}
                        """)).getOrThrow();
        return data;
    }

    private static GunProfile magazineGun() {
        return GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("""
                        {"gun":{"fire_rate":1,"projectile_speed":1,"use_key":false,"firing_mode":"SEMI_AUTO"},
                         "magazine_chamber_provider":{"magazine_tags":["minecraft:paper"],"cartridge_tags":["minecraft:stick"],"starting_magazine":"minecraft:paper","auto_cycle":true,"can_rack":true,"bolt_closed":false},"ammo_counter":true}
                        """)).getOrThrow();
    }

    private static Map<ResourceLocation, AmmoContainerProfile> pistolMagazineCatalog() {
        return Map.of(ResourceLocation.withDefaultNamespace("paper"),
                new AmmoContainerProfile(10, java.util.Set.of(), java.util.Set.of(),
                        ResourceLocation.withDefaultNamespace("stick"), null));
    }

    private static ItemStack gunWithMagazine(ItemStack magazine) {
        var gun = new ItemStack(Items.STICK);
        gun.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(), new MagazineChamberState(magazine,
                ItemStack.EMPTY, false));
        return gun;
    }

    @Test void standaloneMagazineProjectsWithoutGunAndNeverMutatesSource() {
        var raw = new ItemStack(Items.PAPER);
        var catalog = pistolMagazineCatalog();
        var fresh = GunAmmoCounterProjection.project(raw, null, catalog, Map.of());
        assertTrue(fresh.visible()); assertEquals(10, fresh.count()); assertEquals(10, fresh.capacity());
        assertNull(raw.get(ModDataComponents.AMMO_CONTAINER_STATE.get()));
        var partialSource = new ItemStack(Items.PAPER);
        partialSource.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(java.util.List.of(new ItemStack(Items.STICK, 3))));
        var partial = GunAmmoCounterProjection.project(partialSource, null, catalog, Map.of());
        assertEquals(3, partial.count()); assertEquals(10, partial.capacity());
        var emptySource = new ItemStack(Items.PAPER);
        emptySource.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(java.util.List.of()));
        assertEquals(0, GunAmmoCounterProjection.project(emptySource, null, catalog, Map.of()).count());
    }

    @Test void magazineCounterProjectsFreshPartialAndInitializedEmptyContentsWithoutRefilling() {
        var catalog = pistolMagazineCatalog();
        var fresh = GunAmmoCounterProjection.project(gunWithMagazine(new ItemStack(Items.PAPER)), magazineGun(), catalog, Map.of());
        assertTrue(fresh.visible()); assertEquals(10, fresh.count()); assertEquals(10, fresh.capacity());
        assertEquals("10/10", fresh.text());

        var partialMag = new ItemStack(Items.PAPER);
        partialMag.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(
                java.util.List.of(new ItemStack(Items.STICK, 3))));
        var partial = GunAmmoCounterProjection.project(gunWithMagazine(partialMag), magazineGun(), catalog, Map.of());
        assertEquals(3, partial.count()); assertEquals(10, partial.capacity()); assertEquals("3/10", partial.text());

        var emptyMag = new ItemStack(Items.PAPER);
        emptyMag.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(java.util.List.of()));
        var empty = GunAmmoCounterProjection.project(gunWithMagazine(emptyMag), magazineGun(), catalog, Map.of());
        assertEquals(0, empty.count()); assertEquals(10, empty.capacity()); assertEquals("0/10", empty.text());
        assertTrue(emptyMag.get(ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().isEmpty());
    }

    @Test
    void freshRevolverProjectsSixWithoutMutatingInput() {
        ItemStack source = new ItemStack(Items.STICK);
        var result = GunAmmoCounterProjection.project(source, revolver(), Map.of(), Map.of());

        assertTrue(result.visible());
        assertEquals(6, result.count());
        assertEquals(6, result.capacity());
        assertNull(source.get(ModDataComponents.REVOLVER_STATE.get()));
    }

    @Test
    void presentEmptyRevolverStateRemainsEmpty() {
        ItemStack source = new ItemStack(Items.STICK);
        source.set(ModDataComponents.REVOLVER_STATE.get(), RevolverState.empty(6));

        var result = GunAmmoCounterProjection.project(source, revolver(), Map.of(), Map.of());

        assertTrue(result.visible());
        assertEquals(0, result.count());
        assertEquals(6, result.capacity());
        assertEquals(0, source.get(ModDataComponents.REVOLVER_STATE.get()).chambers().stream()
                .filter(chamber -> chamber.physical().isPresent() || chamber.virtualLive()).count());
    }

    @Test
    void revolverCounterUsesSharedPresentationForDeferredSpentCasing() {
        ItemStack source = new ItemStack(Items.STICK);
        source.set(ModDataComponents.REVOLVER_STATE.get(), new RevolverState(0, java.util.List.of(
                RevolverState.Chamber.spentDeferred(id("cartridge_magnum")),
                RevolverState.Chamber.empty(), RevolverState.Chamber.empty(), RevolverState.Chamber.empty(),
                RevolverState.Chamber.empty(), RevolverState.Chamber.empty())));
        var result = GunAmmoCounterProjection.project(source, revolver(), Map.of(), Map.of());
        assertTrue(result.visible());
        assertEquals(0, result.count());
        assertEquals(6, result.capacity());
        assertEquals(GunAmmoCounterProjection.Chamber.SPENT, result.chambers().get(0));
        assertNull(source.get(ModDataComponents.GUN_RUNTIME_STATE.get()));
    }

    private static GunProfile batteryProfile(float cost, boolean counter, Optional<GunProfile.CellSlotData> slot,
            Optional<java.util.List<GunProfile.BatteryFireModeData>> modes) {
        return new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),
                Optional.empty(),false,counter,Optional.empty(),Optional.empty(),
                Optional.of(new GunProfile.BatteryAmmoProviderData(id("red_light_laser"),cost)),Optional.empty(),Optional.empty(),false,1,slot,modes);
    }

    @Test void energyModesProjectInjectedBatteryCapacityWithoutMutatingSource() {
        ItemStack source=new ItemStack(Items.STICK);
        var modes=java.util.List.of(new GunProfile.BatteryFireModeData(id("red_light_laser"),80,false),
                new GunProfile.BatteryFireModeData(id("red_light_laser"),48,true));
        var profile=batteryProfile(80,true,Optional.empty(),Optional.of(modes));
        var batteries=Map.of(ResourceLocation.withDefaultNamespace("stick"),new BatteryProfile(480,480,java.util.Set.of()));
        for(int i=0;i<2;i++) {
            var result=GunAmmoCounterProjection.project(source,profile,
                    GunRuntimeState.INITIAL.withBatteryModeIndex(i),Map.of(),batteries);
            assertTrue(result.visible()); assertEquals(i==0?6:10,result.count()); assertEquals(i==0?6:10,result.capacity());
        }
        assertNull(source.get(ModDataComponents.BATTERY_STATE.get()));
    }

    @Test void cellSlotMissingAndDepletedRemainAtZeroWithoutRefill() {
        var gunId=ResourceLocation.withDefaultNamespace("wooden_hoe");
        var cellId=ResourceLocation.withDefaultNamespace("paper"); var accepted=id("small_cell");
        var cellProfile=new BatteryProfile(360,360,java.util.Set.of(accepted));
        var slot=new GunProfile.CellSlotData(id("power_cell"),java.util.Set.of(accepted),Optional.of(cellId));
        var gunProfile=batteryProfile(50,true,Optional.of(slot),Optional.empty());
        var catalogs=Map.of(cellId,cellProfile);
        var absent=new ItemStack(Items.WOODEN_HOE);
        absent.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.empty()));
        var absentResult=GunAmmoCounterProjection.project(absent,gunProfile,Map.of(),catalogs);
        assertTrue(absentResult.visible()); assertEquals(0,absentResult.count()); assertEquals(7,absentResult.capacity());
        assertTrue(absent.get(ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell().isEmpty());

        var depleted=new ItemStack(Items.WOODEN_HOE); var cell=new ItemStack(Items.PAPER);
        cell.set(ModDataComponents.BATTERY_STATE.get(),new BatteryState(0));
        depleted.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.of(cell)));
        var result=GunAmmoCounterProjection.project(depleted,gunProfile,Map.of(),catalogs);
        assertTrue(result.visible()); assertEquals(0,result.count()); assertEquals(7,result.capacity());
        assertEquals(0,depleted.get(ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell().orElseThrow().get(ModDataComponents.BATTERY_STATE.get()).charge());
    }
}
