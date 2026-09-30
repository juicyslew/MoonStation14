package com.juicyslew.moonstation14.ms14.damage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DamageTypeTagsTest {
    private static final String TAGS = "/data/minecraft/tags/damage_type/";
    private static final String REAGENT = "moonstation14:reagent";
    private static final String BYPASS = "moonstation14:reagent_bypass";

    @Test
    void reagentDamageNeverOptsIntoCreativeInvulnerabilityBypass() {
        JsonObject invulnerability = tag("bypasses_invulnerability");
        if (invulnerability != null) {
            assertFalse(invulnerability.get("replace").getAsBoolean());
            assertFalse(values(invulnerability).contains(REAGENT));
            assertFalse(values(invulnerability).contains(BYPASS));
        }
    }

    @Test
    void resistanceAndCooldownTagsRemainIndependentOfInvulnerability() {
        assertEquals(Set.of(BYPASS), values(tag("bypasses_effects")));
        assertEquals(Set.of(REAGENT, BYPASS), values(tag("bypasses_cooldown")));
        assertEquals(Set.of(BYPASS), values(tag("bypasses_armor")));
        assertEquals(Set.of(BYPASS), values(tag("bypasses_enchantments")));
        assertEquals(Set.of(BYPASS), values(tag("bypasses_shield")));
    }

    private static JsonObject tag(String name) {
        var stream = DamageTypeTagsTest.class.getResourceAsStream(TAGS + name + ".json");
        if (stream == null) return null;
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static Set<String> values(JsonObject tag) {
        assertNotNull(tag, "required damage type tag must exist");
        return StreamSupport.stream(tag.getAsJsonArray("values").spliterator(), false)
                .map(value -> value.getAsString()).collect(Collectors.toSet());
    }
}
