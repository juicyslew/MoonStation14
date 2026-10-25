package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.damage.ArmorModifiers;
import net.minecraft.world.entity.Entity;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies armor composition from occupied, declared equipment slots. */
public final class EquipmentArmorSystem {
    private EquipmentArmorSystem() { }

    public static List<EquipmentItemData.Armor> modifiers(Entity body) {
        return collect(ResolvedWornEquipment.entries(body));
    }

    public static List<EquipmentItemData.Armor> modifiers(LiveHands.EquipmentSnapshot snapshot,
            InventoryTemplateData template, PrototypeCatalog<EquipmentItemData> catalog) {
        return collect(ResolvedWornEquipment.entries(snapshot, template, catalog));
    }

    public static Map<String, Float> apply(Entity body, Map<String, Float> delta) {
        return ArmorModifiers.apply(delta, modifiers(body));
    }

    public static Map<String, Float> apply(LiveHands.EquipmentSnapshot snapshot, InventoryTemplateData template,
            PrototypeCatalog<EquipmentItemData> catalog, Map<String, Float> delta) {
        return ArmorModifiers.apply(delta, modifiers(snapshot, template, catalog));
    }

    public static List<EquipmentItemData.Armor> collect(List<ResolvedWornEquipment.Entry> entries) {
        var result = new ArrayList<EquipmentItemData.Armor>();
        for (var entry : entries) {
            if (entry.slotFlags().isEmpty()
                    || entry.slotFlags().contains(InventoryTemplateData.SlotFlag.POCKET)
                    || entry.slotFlags().contains(InventoryTemplateData.SlotFlag.SUITSTORAGE)) continue;
            entry.profile().armor().ifPresent(result::add);
        }
        return List.copyOf(result);
    }
}
