package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class PlantModelManifestResourceTest {
    @Test void manifestHasAllRequiredModelBindingsAndDistinctPerPlantAliases() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/moonstation14/botany/plant-models.json")) {
            assertNotNull(stream);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1, root.get("formatVersion").getAsInt());
            var plants = root.getAsJsonObject("plants");
            assertEquals(5, plants.size());
            var refs = new HashSet<String>();
            var modelIds = new HashSet<String>();
            plants.entrySet().forEach(plant -> {
                var states = plant.getValue().getAsJsonObject();
                for (String required : new String[]{"stage-1", "harvest", "dead"}) assertTrue(states.has(required), plant.getKey() + "/" + required);
                states.entrySet().forEach(entry -> {
                    String modelId = entry.getValue().getAsString();
                    refs.add(modelId);
                    modelIds.add(modelId);
                });
                assertEquals(states.size(), states.entrySet().stream().map(e -> e.getValue().getAsString()).distinct().count(),
                        "each appearance state has its own model alias for " + plant.getKey());
            });
            assertEquals(34, refs.size());
            assertEquals("moonstation14:block/botany/hydroponics_tray", root.get("tray").getAsString());
            modelIds.add(root.get("tray").getAsString());
            for (String modelId : modelIds) {
                String[] modelParts = modelId.split(":", 2);
                String modelPath = "/assets/" + modelParts[0] + "/models/" + modelParts[1] + ".json";
                try (var modelStream = getClass().getResourceAsStream(modelPath)) {
                    assertNotNull(modelStream, modelPath);
                    var model = JsonParser.parseReader(new InputStreamReader(modelStream, StandardCharsets.UTF_8)).getAsJsonObject();
                    assertEquals("minecraft:cutout", model.get("render_type").getAsString(), modelPath);
                    var textures = model.getAsJsonObject("textures");
                    for (var texture : textures.entrySet()) {
                        String textureId = texture.getValue().getAsString();
                        if (textureId.startsWith("#")) continue;
                        String[] textureParts = textureId.split(":", 2);
                        String texturePath = "/assets/" + textureParts[0] + "/textures/" + textureParts[1] + ".png";
                        try (var textureStream = getClass().getResourceAsStream(texturePath)) {
                            assertNotNull(textureStream, texturePath);
                        }
                    }
                }
            }
        }
    }
}
