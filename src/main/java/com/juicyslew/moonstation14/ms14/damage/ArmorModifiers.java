package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure sequential coefficient armor math; callers supply already-resolved worn equipment order. */
public final class ArmorModifiers {
    private ArmorModifiers() { }

    public static Map<String, Float> apply(Map<String, Float> delta, List<EquipmentItemData.Armor> armor) {
        Map<String, Float> result = new LinkedHashMap<>(DamageKeys.validateDelta(delta));
        List<EquipmentItemData.Armor> modifiers = List.copyOf(armor);
        for (var item : modifiers) {
            if (item == null) throw new IllegalArgumentException("null armor modifier");
            result = new LinkedHashMap<>(DamageModifierData.apply(result, new DamageModifierData(item.coefficients(), Map.of())));
        }
        result.entrySet().removeIf(entry -> entry.getValue() == 0f);
        return Map.copyOf(result);
    }

}
