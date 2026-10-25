package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ArmorModifiersTest {
    private static EquipmentItemData.Armor armor(float blunt, float caustic) {
        return EquipmentItemData.Armor.fromCoefficients(Map.of(DamageKeys.BLUNT, blunt, DamageKeys.CAUSTIC, caustic));
    }
    @Test void appliesSequentiallyQuantizedCoefficientsOnlyToPositiveConfiguredDamage() {
        assertEquals(4.51f, ArmorModifiers.apply(Map.of("blunt", 5f), List.of(armor(.95f, .95f), armor(.95f, .95f))).get("blunt"));
        var result = ArmorModifiers.apply(Map.of("blunt", 5f, "caustic", -2f, "heat", 3f), List.of(armor(0f, 1.5f)));
        assertFalse(result.containsKey("blunt"));
        assertEquals(-2f, result.get("caustic"));
        assertEquals(3f, result.get("heat"));
        assertEquals(Map.of("blunt", 5f), ArmorModifiers.apply(Map.of("blunt", 5f), List.of()));
        assertEquals(.5f, ArmorModifiers.apply(Map.of("blunt", 1.009f), List.of(armor(.5f, .5f))).get("blunt"));
    }
    @Test void validatesCanonicalFiniteNonnegativeImmutableCoefficients() {
        assertThrows(IllegalArgumentException.class, () -> EquipmentItemData.Armor.fromCoefficients(Map.of("Blunt", 1f)));
        assertThrows(IllegalArgumentException.class, () -> EquipmentItemData.Armor.fromCoefficients(Map.of("blunt", Float.NaN)));
        assertThrows(IllegalArgumentException.class, () -> EquipmentItemData.Armor.fromCoefficients(Map.of("blunt", -0.1f)));
        var source = new java.util.HashMap<String, Float>(); source.put("blunt", 0f);
        var armor = EquipmentItemData.Armor.fromCoefficients(source); source.put("blunt", 2f);
        assertEquals(0f, armor.coefficients().get("blunt"));
        assertThrows(UnsupportedOperationException.class, () -> armor.coefficients().put("blunt", 1f));
        assertEquals(1.5f, EquipmentItemData.Armor.fromCoefficients(Map.of("blunt", 1.5f)).coefficients().get("blunt"));
    }
}
