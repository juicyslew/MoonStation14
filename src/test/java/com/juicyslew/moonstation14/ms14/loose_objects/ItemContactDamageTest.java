package com.juicyslew.moonstation14.ms14.loose_objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemContactDamageTest {
    private static final String PROFILE = "\"mass\":1,\"friction\":0.5,\"restitution\":0,\"throw_speed\":0.75,\"throw_spin\":0,\"geometry\":\"cube\",\"render_kind\":\"flat_sprite\",\"break_on_impact\":false,\"break_threshold\":0,\"throw_range\":8,\"minimum_throw_range\":0.1,\"throw_cooldown\":10,\"physical_width\":0.65,\"physical_height\":0.08,\"visual\":{\"sprite_scale_x\":0.65,\"sprite_scale_y\":0.65,\"sprite_offset_x\":0,\"sprite_offset_y\":0,\"model_scale\":1,\"model_floor_offset\":0}";

    @Test void codecUsesVerifiedDefaultsAndRejectsInvalidValues() {
        var data = decode("{\"damage\":{\"piercing\":2}}" );
        assertEquals(Map.of("piercing", 2f), data.damage());
        assertTrue(data.damageActive());
        assertEquals(0f, data.interactTimerSeconds());
        assertFalse(data.ignoreResistances());
        assertEquals(LooseObjectProfile.DamageOnInteractData.DEFAULT_SOUND, data.soundEvent());
        assertNull(data.popupText());
        assertEquals(0f, data.stunChance());
        assertEquals(0f, data.stunSeconds());
        assertFalse(data.throwSource());
        assertEquals(10f, data.throwSpeed());
        for (String invalid : new String[]{"not_real", ""}) {
            JsonObject value = JsonParser.parseString("{\"damage\":{\"" + invalid + "\":2}}").getAsJsonObject();
            assertThrows(RuntimeException.class, () -> LooseObjectProfile.DamageOnInteractData.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow());
        }
        for (String field : new String[]{"interact_timer_seconds", "stun_seconds", "throw_speed"}) {
            JsonObject value = JsonParser.parseString("{\"damage\":{\"piercing\":1},\"" + field + "\":-1}").getAsJsonObject();
            assertThrows(RuntimeException.class, () -> LooseObjectProfile.DamageOnInteractData.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow());
        }
        JsonObject chance = JsonParser.parseString("{\"damage\":{\"piercing\":1},\"stun_chance\":2}").getAsJsonObject();
        assertThrows(RuntimeException.class, () -> LooseObjectProfile.DamageOnInteractData.CODEC.parse(JsonOps.INSTANCE, chance).getOrThrow());
    }

    @Test void optionalRootCompositionInheritsAndPartiallyOverridesThroughResolver() {
        JsonObject parent = JsonParser.parseString("{\"id\":\"moonstation14:contact_parent\",\"type\":\"moonstation14:loose_object_profile\"," + PROFILE + ",\"damage_on_interact\":{\"damage\":{\"piercing\":2},\"damage_active\":false,\"ignore_resistances\":true}}" ).getAsJsonObject();
        JsonObject child = JsonParser.parseString("{\"id\":\"moonstation14:contact_child\",\"type\":\"moonstation14:loose_object_profile\",\"parent\":\"moonstation14:contact_parent\",\"damage_on_interact\":{\"stun_seconds\":3}}" ).getAsJsonObject();
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.parse("moonstation14:contact_parent"), parent);
        raw.put(ResourceLocation.parse("moonstation14:contact_child"), child);
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        JsonObject result = resolved.get(ResourceLocation.parse("moonstation14:contact_child"));
        var profile = LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, result).getOrThrow();
        assertEquals(Map.of("piercing", 2f), profile.damageOnInteract().damage());
        assertFalse(profile.damageOnInteract().damageActive());
        assertTrue(profile.damageOnInteract().ignoreResistances());
        assertEquals(3f, profile.damageOnInteract().stunSeconds());
        assertNull(LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + PROFILE + "}")).getOrThrow().damageOnInteract());
    }

    private static LooseObjectProfile.DamageOnInteractData decode(String json) {
        return LooseObjectProfile.DamageOnInteractData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
