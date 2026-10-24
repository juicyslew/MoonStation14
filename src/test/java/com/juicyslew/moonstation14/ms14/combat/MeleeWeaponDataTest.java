package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MeleeWeaponDataTest {
    @Test void defaultsAndExplicitValuesDecode() {
        var defaults = MeleeWeaponData.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"damage\":{\"blunt\":5}}" )).getOrThrow();
        assertEquals(1f, defaults.attackRate()); assertEquals(1.5f, defaults.range());
        assertFalse(defaults.resistanceBypass()); assertFalse(defaults.autoAttack());
        assertEquals(1f, defaults.clickDamageModifier()); assertFalse(defaults.mustBeEquippedToUse());
        assertEquals("moonstation14:weapon_arc_thrust", defaults.arc().toString());
        assertEquals(1f, defaults.animationOffset()); assertEquals(0f, defaults.wideAnimationRotation());
        assertEquals(.5f, defaults.bluntStaminaDamageFactor());
        assertEquals(60f, defaults.angleDegrees()); assertEquals("moonstation14:weapon_arc_slash", defaults.wideArc().toString()); assertTrue(defaults.altDisarm());
        var explicit = MeleeWeaponData.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"damage\":{\"slash\":8},\"attack_rate\":2,\"range\":3,\"resistance_bypass\":true,\"auto_attack\":true,\"click_damage_modifier\":0,\"must_be_equipped_to_use\":true,\"arc\":\"moonstation14:weapon_arc_main\",\"animation_offset\":2,\"wide_animation_rotation\":60,\"blunt_stamina_damage_factor\":0,\"angle_degrees\":0,\"wide_arc\":\"moonstation14:weapon_arc_fist\",\"alt_disarm\":false}")).getOrThrow();
        assertEquals(Map.of(DamageKeys.SLASH, 8f), explicit.damage()); assertEquals(2f, explicit.attackRate());
        assertEquals(3f, explicit.range()); assertTrue(explicit.resistanceBypass()); assertTrue(explicit.autoAttack());
        assertEquals(0f, explicit.clickDamageModifier()); assertTrue(explicit.mustBeEquippedToUse());
        assertEquals("moonstation14:weapon_arc_main", explicit.arc().toString());
        assertEquals(2f, explicit.animationOffset()); assertEquals(60f, explicit.wideAnimationRotation());
        assertEquals(0f, explicit.bluntStaminaDamageFactor());
        assertEquals(0f, explicit.angleDegrees()); assertEquals("moonstation14:weapon_arc_fist", explicit.wideArc().toString()); assertFalse(explicit.altDisarm());
    }

    @Test void mapIsImmutableAndInputsAreValidated() {
        var mutable = new HashMap<String, Float>(); mutable.put("blunt", 2f);
        var data = new MeleeWeaponData(mutable, 1, 1, false, false, 1, false);
        mutable.put("slash", 4f); assertEquals(Map.of("blunt", 2f), data.damage());
        assertThrows(UnsupportedOperationException.class, () -> data.damage().put("slash", 1f));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponData(Map.of("unknown", 1f), 1, 1, false, false, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponData(Map.of("blunt", 1f), 0, 1, false, false, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponData(Map.of("blunt", 1f), Float.NaN, 1, false, false, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponData(Map.of("blunt", 1f), 1, 0, false, false, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponData(Map.of("blunt", 1f), 1, Float.POSITIVE_INFINITY, false, false, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponData(Map.of("blunt", 1f), 1, 1, false, false, 1, false, MeleeSoundData.DEFAULT, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "arc"), 1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new MeleeWeaponData(Map.of("blunt", 1f), 1, 1, false, false, 1, false, MeleeSoundData.DEFAULT, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "arc"), 1, 0, Float.NaN));
    }
}
