package com.juicyslew.moonstation14.ms14.character;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CharacterPrototypeComponentUpsertTest {
    @Test void productionHumanCombatOverridesKeepUniqueCompositionAndUnrelatedPolicies() throws Exception {
        var resource = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json");
        assertNotNull(resource);
        var root = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
        var components = root.getAsJsonArray("components");
        int before = components.size();

        CharacterPrototypeComponentUpsert.upsert(components,
                JsonParser.parseString("{\"type\":\"CombatMode\",\"can_disarm\":true}").getAsJsonObject());
        CharacterPrototypeComponentUpsert.upsert(components,
                JsonParser.parseString("{\"type\":\"MeleeWeapon\",\"weapon\":{\"damage\":{\"blunt\":5}}}").getAsJsonObject());

        var decoded = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                .parse(JsonOps.INSTANCE, root).getOrThrow();
        assertEquals(before + 1, components.size(), "CombatMode is replaced; only absent MeleeWeapon is added");
        assertEquals(1, components.asList().stream().filter(e -> e.getAsJsonObject().get("type").getAsString().equals("CombatMode")).count());
        assertEquals(1, components.asList().stream().filter(e -> e.getAsJsonObject().get("type").getAsString().equals("Stamina")).count());
        assertEquals(java.util.Optional.of(true), decoded.component(
                com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent.class)
                .orElseThrow().canDisarm());
        assertTrue(decoded.component(com.juicyslew.moonstation14.ms14.character.components.VocalComponent.class).isPresent());
        assertTrue(decoded.component(com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent.class).isPresent());
        assertTrue(decoded.component(com.juicyslew.moonstation14.ms14.character.components.MeleeWeaponComponent.class).isPresent());
    }
}
