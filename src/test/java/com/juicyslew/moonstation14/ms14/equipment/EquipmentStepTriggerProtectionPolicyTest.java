package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.character.components.ProtectedFromStepTriggersComponent;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentStepTriggerProtectionPolicyTest {
    private static ResolvedWornEquipment.Entry entry(List<InventoryTemplateData.SlotFlag> mask, InventoryTemplateData.SlotFlag worn) {
        var item = new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                Optional.of(new ProtectedFromStepTriggers(mask)));
        return new ResolvedWornEquipment.Entry("test", List.of(worn), net.minecraft.world.item.ItemStack.EMPTY, item);
    }
    private static boolean protects(List<ResolvedWornEquipment.Entry> entries) {
        return EquipmentStepTriggerProtectionPolicy.protects(Optional.empty(), entries);
    }
    @Test void directPresenceProtectsEvenWithEmptyMask() {
        assertTrue(EquipmentStepTriggerProtectionPolicy.protects(Optional.of(new ProtectedFromStepTriggersComponent(new ProtectedFromStepTriggers(List.of()))), List.of()));
    }
    @Test void wornMaskIntersectsDeclaredSlotFlags() {
        assertTrue(protects(List.of(entry(List.of(InventoryTemplateData.SlotFlag.FEET), InventoryTemplateData.SlotFlag.FEET))));
        assertFalse(protects(List.of(entry(List.of(InventoryTemplateData.SlotFlag.FEET), InventoryTemplateData.SlotFlag.POCKET))));
        assertTrue(protects(List.of(entry(List.of(InventoryTemplateData.SlotFlag.FEET, InventoryTemplateData.SlotFlag.POCKET), InventoryTemplateData.SlotFlag.POCKET))));
        assertTrue(protects(List.of(entry(List.of(InventoryTemplateData.SlotFlag.HEAD), InventoryTemplateData.SlotFlag.HEAD))));
        assertFalse(protects(List.of(entry(List.of(), InventoryTemplateData.SlotFlag.FEET))));
    }
}
