package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentTimedInteractionTest {
    @Test void occupiedHeadDoesNotSuppressFortyTickQuickEquipDelayAndZeroDefaultIsImmediate() {
        var clothing = new EquipmentItemData.Clothing(List.of(InventoryTemplateData.SlotFlag.HEAD),
                EquipmentItemData.AppearanceRegion.HEAD, 0, 40, 40, Optional.empty(), Optional.empty(),
                true, Optional.empty(), 0, List.of());
        var item = new EquipmentItemData(new EquipmentItemData.Item("Small"), Optional.of(clothing),
                Optional.empty(), List.of());
        // Slot occupancy is deliberately not an input to delay selection: quick replacement still waits.
        assertEquals(40, EquipmentTimedInteraction.delay(item, true));
        assertEquals(40, EquipmentTimedInteraction.delay(item, false));
        assertEquals(0, EquipmentTimedInteraction.delay((EquipmentItemData) null, true));
        assertEquals(0, EquipmentTimedInteraction.delay(new EquipmentItemData(new EquipmentItemData.Item("Small"),
                Optional.empty(), Optional.empty(), List.of()), false));
    }
}
