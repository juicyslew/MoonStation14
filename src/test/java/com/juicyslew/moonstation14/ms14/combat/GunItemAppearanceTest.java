package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class GunItemAppearanceTest {
    private static ResourceLocation id(String s) { return ResourceLocation.withDefaultNamespace(s); }
    private static GunProfile gun(Optional<GunProfile.CellSlotData> slot, Optional<java.util.List<GunProfile.BatteryFireModeData>> modes) {
        return new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()), Optional.empty(),false,true,
                Optional.empty(),Optional.empty(),Optional.of(new GunProfile.BatteryAmmoProviderData(id("arrow"),50)),Optional.empty(),Optional.empty(),false,1,slot,modes);
    }
    @Test void insertedCellChargeFractionAndDepletionRemainReadOnly() {
        var slot = new GunProfile.CellSlotData(id("cell"), java.util.Set.of(id("cell")), Optional.of(id("paper")));
        var profile = gun(Optional.of(slot), Optional.of(java.util.List.of(new GunProfile.BatteryFireModeData(id("arrow"),50,false),
                new GunProfile.BatteryFireModeData(id("arrow"),100,false))));
        var catalog = Map.of(id("paper"), new BatteryProfile(360,360,java.util.Set.of(id("cell"))));
        var source = new ItemStack(Items.STICK);
        var cell = new ItemStack(Items.PAPER); cell.set(ModDataComponents.BATTERY_STATE.get(),new BatteryState(10));
        source.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.of(cell)));
        source.set(ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL.withBatteryModeIndex(1));
        var projected = GunItemAppearance.project(source,Map.of(id("stick"),profile),Map.of(),catalog);
        assertEquals(10f/360f,projected.chargeFraction(),.00001f);
        assertEquals(0, projected.containerFill());
        assertEquals(1,projected.batteryMode());
        assertEquals(10,source.get(ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell().orElseThrow().get(ModDataComponents.BATTERY_STATE.get()).charge());
        cell.set(ModDataComponents.BATTERY_STATE.get(),new BatteryState(360));
        source.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.of(cell)));
        assertEquals(1, GunItemAppearance.project(source,Map.of(id("stick"),profile),Map.of(),catalog).containerFill());
        cell.set(ModDataComponents.BATTERY_STATE.get(),new BatteryState(0));
        source.set(ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.of(cell)));
        assertEquals(0,GunItemAppearance.project(source,Map.of(id("stick"),profile),Map.of(),catalog).chargeFraction());
    }

    @Test void standaloneContainersProjectFullPartialAndEmptyWithoutMutation() {
        var containers = Map.of(id("paper"), new AmmoContainerProfile(4, java.util.Set.of()));
        var full = new ItemStack(Items.PAPER);
        full.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(java.util.List.of(new ItemStack(Items.ARROW, 4))));
        var partial = new ItemStack(Items.PAPER);
        partial.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(java.util.List.of(new ItemStack(Items.ARROW, 2))));
        var empty = new ItemStack(Items.PAPER);
        empty.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(java.util.List.of()));
        assertEquals(1, GunItemAppearance.project(full, Map.of(), containers, Map.of()).containerFill());
        assertEquals(.5f, GunItemAppearance.project(partial, Map.of(), containers, Map.of()).containerFill());
        assertEquals(0, GunItemAppearance.project(empty, Map.of(), containers, Map.of()).containerFill());
        assertEquals(4, full.get(ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().getFirst().getCount());
    }

    @Test void magazineAndCellAbsenceAreIndependentAndChamberDoesNotAffectFill() {
        var slot = new GunProfile.CellSlotData(id("cell"), java.util.Set.of(id("cell")), Optional.empty());
        var profile = gun(Optional.of(slot), Optional.empty());
        var gun = new ItemStack(Items.STICK);
        gun.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(), new MagazineChamberState(ItemStack.EMPTY, ItemStack.EMPTY, true));
        var absent = GunItemAppearance.project(gun, Map.of(id("stick"), profile), Map.of(), Map.of());
        assertTrue(absent.magazineAbsent());
        assertTrue(absent.cellAbsent());
        var magazine = new ItemStack(Items.PAPER);
        magazine.set(ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(java.util.List.of()));
        gun.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(), new MagazineChamberState(magazine, new ItemStack(Items.ARROW), false));
        var present = GunItemAppearance.project(gun, Map.of(id("stick"), profile), Map.of(id("paper"), new AmmoContainerProfile(4, java.util.Set.of())), Map.of());
        assertFalse(present.magazineAbsent());
        assertTrue(present.cellAbsent());
        assertEquals(0, present.containerFill());
        assertFalse(gun.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get()).chamber().isEmpty());
    }
}
