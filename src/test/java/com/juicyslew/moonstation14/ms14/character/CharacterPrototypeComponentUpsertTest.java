package com.juicyslew.moonstation14.ms14.character;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import org.junit.jupiter.api.Test;

class CharacterPrototypeComponentUpsertTest {
    @Test void wideOriginHeightRatioIsStrictlyValidatedAndInheritedInResolvedHumanComposition() throws Exception {
        var human = JsonParser.parseReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.audit(
                net.minecraft.resources.ResourceLocation.parse("moonstation14:human"), human);
        var data = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                .parse(JsonOps.INSTANCE, human).getOrThrow();
        assertEquals(.6f, data.component(com.juicyslew.moonstation14.ms14.character.components.MeleeWeaponComponent.class)
                .orElseThrow().weapon().wideOriginHeightRatio());

        for (String value : List.of("-0.1", "1.1", "\"NaN\"")) {
            var invalid = JsonParser.parseString("{\"type\":\"MeleeWeapon\",\"weapon\":{\"wide_origin_height_ratio\":" + value + "}}")
                    .getAsJsonObject();
            assertThrows(IllegalArgumentException.class, () ->
                    com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponentFragment(invalid, "$.components[0]"));
        }
        var unknown = JsonParser.parseString("{\"type\":\"MeleeWeapon\",\"weapon\":{\"wide_origin_height_ratio\":0.6,\"arbitrary\":1}}")
                .getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () ->
                com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponentFragment(unknown, "$.components[0]"));

        var parentId = net.minecraft.resources.ResourceLocation.parse("moonstation14:ratio_parent");
        var humanId = net.minecraft.resources.ResourceLocation.parse("moonstation14:human");
        var parent = JsonParser.parseString("{\"abstract\":true,\"components\":[{\"type\":\"MeleeWeapon\",\"weapon\":{\"damage\":{},\"wide_origin_height_ratio\":0.6}}]}").getAsJsonObject();
        var child = JsonParser.parseString("{\"parent\":\"ratio_parent\"}").getAsJsonObject();
        var manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, java.util.Map.of(parentId, parent, humanId, child));
        assertEquals(.6f, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(humanId)
                .component(com.juicyslew.moonstation14.ms14.character.components.MeleeWeaponComponent.class)
                .orElseThrow().weapon().wideOriginHeightRatio());
    }

    @Test void productionHumanCombatOverridesKeepUniqueCompositionAndUnrelatedPolicies() throws Exception {
        var resource = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json");
        assertNotNull(resource);
        var root = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
        var components = root.getAsJsonArray("components");
        int before = components.size();
        boolean alreadyHasMelee = components.asList().stream().anyMatch(e ->
                e.getAsJsonObject().get("type").getAsString().equals("MeleeWeapon"));

        CharacterPrototypeComponentUpsert.upsert(components,
                JsonParser.parseString("{\"type\":\"CombatMode\",\"can_disarm\":true}").getAsJsonObject());
        CharacterPrototypeComponentUpsert.upsert(components,
                JsonParser.parseString("{\"type\":\"MeleeWeapon\",\"weapon\":{\"damage\":{\"blunt\":5}}}").getAsJsonObject());

        var decoded = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                .parse(JsonOps.INSTANCE, root).getOrThrow();
        assertEquals(before + (alreadyHasMelee ? 0 : 1), components.size(),
                "upsert replaces existing typed entries and adds only a missing component");
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
