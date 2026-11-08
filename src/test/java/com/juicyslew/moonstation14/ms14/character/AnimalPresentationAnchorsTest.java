package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.animals.AnimalPresentationAnchors;
import com.juicyslew.moonstation14.ms14.character.components.AnimalAppearanceComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimalPresentationAnchorsTest {
    @Test void optionalAnchorsDefaultAndExplicitValuesReachRuntimeHelpersThroughInheritance() {
        var defaultAppearance = decode("{\"type\":\"AnimalAppearance\",\"model_ids\":[\"fixture\"]}");
        assertNull(defaultAppearance.eyeHeight());
        assertNull(defaultAppearance.speechHeight());
        assertEquals(1.7, AnimalPresentationAnchors.eyeHeight(defaultAppearance, 1.7));
        assertEquals(1.6, AnimalPresentationAnchors.speechHeight(defaultAppearance, 1.6));

        var explicitAppearance = decode("{\"type\":\"AnimalAppearance\",\"model_ids\":[\"fixture\"],\"eye_height\":1.25,\"speech_height\":1.45}");
        assertEquals(1.25, AnimalPresentationAnchors.eyeHeight(explicitAppearance, 1.7), 1e-6);
        assertEquals(1.45, AnimalPresentationAnchors.speechHeight(explicitAppearance, 1.6), 1e-6);

        var definitions = new LinkedHashMap<ResourceLocation, JsonObject>();
        definitions.put(ResourceLocation.parse("test:anchor_parent"), JsonParser.parseString(
                "{\"abstract\":true,\"components\":[{\"type\":\"AnimalAppearance\",\"model_ids\":[\"fixture\"],\"eye_height\":1.25,\"speech_height\":1.45}]} ").getAsJsonObject());
        definitions.put(ResourceLocation.parse("test:anchor_child"), JsonParser.parseString(
                "{\"parent\":\"test:anchor_parent\",\"host_entity_types\":[]}").getAsJsonObject());
        var manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, definitions);
        var inherited = manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ResourceLocation.parse("test:anchor_child"))
                .component(AnimalAppearanceComponent.class).orElseThrow();
        assertEquals(1.25, AnimalPresentationAnchors.eyeHeight(inherited, 1.7), 1e-6);
        assertEquals(1.45, AnimalPresentationAnchors.speechHeight(inherited, 1.6), 1e-6);
    }

    @Test void codecRejectsInvalidAnchorsAndCanonicalAnimalsParseAuthoredValues() throws Exception {
        for (String field : java.util.List.of("eye_height", "speech_height")) {
            for (String invalid : java.util.List.of("-1", "0", "1e999")) {
                String json = "{\"type\":\"AnimalAppearance\",\"model_ids\":[\"fixture\"],\"" + field + "\":" + invalid + "}";
                assertTrue(AnimalAppearanceComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                        JsonParser.parseString(json)).error().isPresent(), field + "=" + invalid);
            }
        }
        Path root = Path.of(System.getProperty("moonstation14.projectDir"), "src/main/resources/data/moonstation14/moonstation14/character");
        assertCanonical(root, "kobold", 1.19f, 1.42f);
        assertCanonical(root, "monkey", 1.06f, 1.23f);
    }

    private static AnimalAppearanceComponent decode(String json) {
        return AnimalAppearanceComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString(json)).getOrThrow(IllegalArgumentException::new);
    }

    private static void assertCanonical(Path root, String name, float eye, float speech) throws Exception {
        JsonObject definition = JsonParser.parseString(Files.readString(root.resolve(name + ".json"))).getAsJsonObject();
        var appearance = definition.getAsJsonArray("components").asList().stream()
                .map(e -> e.getAsJsonObject()).filter(c -> "AnimalAppearance".equals(c.get("type").getAsString()))
                .map(c -> decode(c.toString())).findFirst().orElseThrow();
        assertEquals(eye, appearance.eyeHeight());
        assertEquals(speech, appearance.speechHeight());
    }
}
