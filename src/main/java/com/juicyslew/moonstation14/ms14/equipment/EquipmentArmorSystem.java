package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.damage.ArmorModifiers;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.Map;

/** Applies armor composition from occupied, declared equipment slots. */
public final class EquipmentArmorSystem {
    private EquipmentArmorSystem() { }

    public static List<EquipmentItemData.Armor> modifiers(Entity body) {
        return collect(ResolvedWornEquipment.entries(body));
    }

    public static Map<String, Float> apply(Entity body, Map<String, Float> delta) {
        return ArmorModifiers.apply(delta, modifiers(body));
    }

    static List<EquipmentItemData.Armor> collect(List<ResolvedWornEquipment.Entry> entries) {
        var result = new java.util.ArrayList<EquipmentItemData.Armor>();
        for (var entry : entries) {
            if (entry.slotFlags().isEmpty() || entry.slotFlags().stream().allMatch(flag -> flag == InventoryTemplateData.SlotFlag.POCKET)) continue;
            entry.profile().armor().ifPresent(result::add);
        }
        return List.copyOf(result);
    }
}
