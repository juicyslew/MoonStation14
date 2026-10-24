package com.juicyslew.moonstation14.ms14.equipment;

import net.minecraft.world.entity.Entity;

/** Equipment-specific feet immunity query; does not change slip admission or response policy. */
public final class EquipmentNoSlipPolicy {
    private EquipmentNoSlipPolicy() { }

    public static boolean hasWornFeetNoSlip(Entity body) {
        return hasWornFeetNoSlip(ResolvedWornEquipment.entries(body));
    }

    static boolean hasWornFeetNoSlip(java.util.List<ResolvedWornEquipment.Entry> entries) {
        return entries.stream().anyMatch(entry -> entry.slotFlags().contains(InventoryTemplateData.SlotFlag.FEET)
                && entry.profile().noSlip().isPresent());
    }
}
