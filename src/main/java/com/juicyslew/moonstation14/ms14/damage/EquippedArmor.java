package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentArmorSystem;
import com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/** Applies resolved armor from equipped slots only; storage contents are never traversed. */
public final class EquippedArmor {
    private EquippedArmor() {}

    public static Map<String, Float> modify(LivingEntity wearer, Map<String, Float> damage) {
        return EquipmentArmorSystem.apply(wearer, damage);
    }

    /** Shared production-consumer path, with resolved composed inputs explicit for focused tests. */
    public static Map<String, Float> modify(LiveHands.EquipmentSnapshot snapshot,
            InventoryTemplateData template, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<EquipmentItemData> catalog,
            Map<String, Float> damage) {
        return EquipmentArmorSystem.apply(snapshot, template, catalog, damage);
    }

    static void apply(Map<String, Float> damage, EquipmentItemData.Armor armor) {
        var modified = com.juicyslew.moonstation14.ms14.damage.ArmorModifiers.apply(damage, java.util.List.of(armor));
        damage.clear();
        damage.putAll(modified);
    }
}
