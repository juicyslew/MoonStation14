package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Applies resolved armor from equipped slots only; storage contents are never traversed. */
public final class EquippedArmor {
    private EquippedArmor() {}

    public static Map<String, Float> modify(LivingEntity wearer, Map<String, Float> damage) {
        var snapshot = LiveHands.equipmentSnapshot(wearer);
        var identity = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(wearer);
        var component = identity.flatMap(value -> value.component(
                com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent.class));
        var templateId = component.flatMap(value -> value.template());
        var template = templateId.map(id -> ModInventoryTemplates.catalog(wearer.level()).get(id));
        return modify(snapshot.orElse(null), template.orElse(null), ModEquipmentItems.catalog(wearer.level()), damage);
    }

    /** Shared production-consumer path, with resolved composed inputs explicit for focused tests. */
    public static Map<String, Float> modify(LiveHands.EquipmentSnapshot snapshot,
            InventoryTemplateData template, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<EquipmentItemData> catalog,
            Map<String, Float> damage) {
        if (snapshot == null || template == null || catalog == null) return damage;
        var result = new LinkedHashMap<>(damage);
        for (var slot : snapshot.slots()) {
            var declaration = template.slot(slot.id()).orElse(null);
            if (declaration == null || declaration.flags().contains(InventoryTemplateData.SlotFlag.POCKET)
                    || declaration.flags().contains(InventoryTemplateData.SlotFlag.SUITSTORAGE)) continue;
            var stack = slot.stack().orElse(null);
            if (stack == null || stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) continue;
            // ModEquipmentItems are registered in the same namespace as their typed prototypes.
            EquipmentItemData data;
            try { data = ModEquipmentItems.require(catalog, id); }
            catch (IllegalArgumentException absentOptionalPrototype) { continue; }
            if (data.armor().isEmpty()) continue;
            apply(result, data.armor().get());
        }
        return Map.copyOf(result);
    }

    static void apply(Map<String, Float> damage, EquipmentItemData.Armor armor) {
        armor.modifiers().forEach((type, modifier) -> {
            Float amount = damage.get(type);
            if (amount != null && amount > 0f) {
                float next = Math.max(0f, amount - modifier.flatReduction()) * modifier.coefficient();
                if (next == 0f) damage.remove(type); else damage.put(type, next);
            }
        });
    }
}

