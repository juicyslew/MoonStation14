package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentProtectionProjectionTest {
    private static final BarotraumaComponent POLICY = new BarotraumaComponent(new BarotraumaComponent.Damage(Map.of("blunt", 1d)), 100);

    @Test void allRequiredSlotsMustBeCoveredAndProtectionUsesWeakestComponent() {
        var head = entry("head", new EquipmentItemData.PressureProtection(.6, 1000, 0, 0));
        var suit = entry("outerClothing", new EquipmentItemData.PressureProtection(.8, 700, 0, 0));
        assertTrue(EquipmentProtectionProjection.pressure(List.of(head), POLICY).isEmpty());
        assertTrue(EquipmentProtectionProjection.pressure(List.of(suit), POLICY).isEmpty());
        assertEquals(new EquipmentItemData.PressureProtection(.8, 700, 0, 0),
                EquipmentProtectionProjection.pressure(List.of(head, suit), POLICY).orElseThrow());
        assertTrue(EquipmentProtectionProjection.pressure(List.of(head, entry("outerClothing", null)), POLICY).isEmpty());
    }

    @Test void thermalProtectionMultipliesRelayEligibleNonPocketWornGear() {
        var helmet = entry("head", List.of(InventoryTemplateData.SlotFlag.HEAD), null,
                new EquipmentItemData.TemperatureProtection(.2, .2));
        var suit = entry("outerClothing", List.of(InventoryTemplateData.SlotFlag.OUTERCLOTHING), null,
                new EquipmentItemData.TemperatureProtection(.01, .01));
        var pocket = entry("pocket1", List.of(InventoryTemplateData.SlotFlag.POCKET), null,
                new EquipmentItemData.TemperatureProtection(0, 0));
        assertEquals(.002, EquipmentProtectionProjection.temperatureCoefficient(List.of(helmet, suit, pocket), 1), 1e-12);
        assertEquals(.002, EquipmentProtectionProjection.temperatureCoefficient(List.of(helmet, suit, pocket), -1), 1e-12);
    }

    private static ResolvedWornEquipment.Entry entry(String slot, EquipmentItemData.PressureProtection protection) {
        return entry(slot, List.of(), protection, null);
    }

    private static ResolvedWornEquipment.Entry entry(String slot, List<InventoryTemplateData.SlotFlag> flags,
            EquipmentItemData.PressureProtection pressure, EquipmentItemData.TemperatureProtection temperature) {
        var item = new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.empty(), Optional.empty(), List.of(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.ofNullable(pressure), Optional.ofNullable(temperature));
        return new ResolvedWornEquipment.Entry(slot, flags, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE), item);
    }
}
