package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PlantModelManifestResourceTest {
    private static final String ASSET_ROOT = "/assets/moonstation14/";
    private static final Set<String> EXPECTED_PLANTS = Set.of(
            "aloe", "ambrosia_deus", "ambrosia_vulgaris", "anomaly_berry", "apple", "banana", "berries",
            "blood_tomato", "bloonion", "blue_pumpkin", "blue_tomato", "bungo", "cabbage", "cannabis",
            "capfruit", "carrot", "chanterelle", "cherry", "chili", "chilly", "cocoa", "corn", "cotton",
            "death_nettle", "eggplant", "eggy", "extradimensional_orange", "fly_amanita", "galaxythistle",
            "garlic", "gatfruit", "glasstle", "golden_apple", "grape", "holymelon", "koibean", "laughin_pea",
            "lemon", "lemoon", "lily", "lime", "lingzhi", "meatwheat", "mimana", "nettle", "oat", "onion",
            "onion_red", "orange", "papercane", "pea", "pineapple", "poppy", "potato", "pumpkin", "pyrotton",
            "rainbow_cannabis", "rice", "soybeans", "spacemans_trumpet", "steelcap", "sugarcane", "tea_plant",
            "tobacco", "tomato", "towercap", "watermelon", "wheat", "world_pea");
    @Test void manifestHasAllRequiredModelBindingsAndDistinctPerPlantAliases() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/moonstation14/botany/plant-models.json")) {
            assertNotNull(stream);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1, root.get("formatVersion").getAsInt());
            var plants = root.getAsJsonObject("plants");
            assertEquals(EXPECTED_PLANTS,
                    plants.keySet().stream().collect(Collectors.toSet()));
            assertEquals(69, plants.size());
            assertEquals(Set.of("stage-1", "stage-2", "stage-3", "stage-4", "stage-5", "stage-6", "harvest", "dead"),
                    plants.getAsJsonObject("wheat").keySet().stream().collect(Collectors.toSet()));
            var refs = new HashSet<String>();
            var modelIds = new HashSet<String>();
            int[] stateCount = {0};
            plants.entrySet().forEach(plant -> {
                var states = plant.getValue().getAsJsonObject();
                stateCount[0] += states.size();
                for (String required : new String[]{"stage-1", "harvest", "dead"}) assertTrue(states.has(required), plant.getKey() + "/" + required);
                states.entrySet().forEach(entry -> {
                    String modelId = entry.getValue().getAsString();
                    refs.add(modelId);
                    modelIds.add(modelId);
                });
                assertEquals(states.size(), states.entrySet().stream().map(e -> e.getValue().getAsString()).distinct().count(),
                        "each appearance state has its own model alias for " + plant.getKey());
            });
            assertEquals(stateCount[0], refs.size(), "every declared appearance state has a unique model alias");
            assertEquals(428, stateCount[0]);
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

    @Test void registeredPlantModelsAndObjMaterialDependenciesAreClosed() throws Exception {
        var registered = com.juicyslew.moonstation14.ms14.botany.client.HydroponicsTrayBlockEntityRenderer
                .additionalModels();
        assertFalse(registered.isEmpty());
        assertTrue(registered.stream().allMatch(id -> id.getVariant().equals("standalone")));
        var manifest = com.juicyslew.moonstation14.ms14.botany.client.PlantModelManifest.load();
        var manifestIds = manifest.plants().values().stream().flatMap(states -> states.values().stream())
                .map(id -> id.id().toString()).collect(Collectors.toSet());
        manifestIds.addAll(manifest.indicators().values().stream().map(id -> id.id().toString()).toList());
        assertEquals(manifestIds, registered.stream().map(id -> id.id().toString()).collect(Collectors.toSet()));
        int jsonCount = 0, objCount = 0, mtlCount = 0, materialTextureCount = 0;
        for (var id : registered) {
            String jsonPath = "models/" + id.id().getPath() + ".json";
            assertResource(jsonPath);
            jsonCount++;
            var model = JsonParser.parseString(readText(jsonPath)).getAsJsonObject();
            if (!model.has("loader")) {
                assertTrue(model.has("parent") || model.has("elements"), "Unsupported regular model shape " + jsonPath);
                continue;
            }
            assertEquals("neoforge:obj", model.get("loader").getAsString(), jsonPath);
            assertTrue(model.has("model"), jsonPath);
            var objId = net.minecraft.resources.ResourceLocation.parse(model.get("model").getAsString());
            assertEquals(id.id().getNamespace(), objId.getNamespace(), jsonPath);
            String objPath = objId.getPath();
            String obj = readText(objPath);
            objCount++;
            var libraries = java.util.regex.Pattern.compile("(?m)^\\s*mtllib\\s+(\\S+)").matcher(obj);
            while (libraries.find()) {
                String mtlPath = objPath.substring(0, objPath.lastIndexOf('/') + 1) + libraries.group(1);
                mtlCount++;
                var textures = java.util.regex.Pattern.compile("(?m)^\\s*map_Kd\\s+(\\S+)").matcher(readText(mtlPath));
                while (textures.find()) {
                    String texture = textures.group(1);
                    if (texture.startsWith("#")) {
                        String key = texture.substring(1);
                        assertTrue(model.has("textures") && model.getAsJsonObject("textures").has(key), jsonPath + " missing material texture alias " + texture);
                        texture = model.getAsJsonObject("textures").get(key).getAsString();
                        assertFalse(texture.startsWith("#"), "Unresolved material texture alias " + texture);
                    }
                    var textureId = net.minecraft.resources.ResourceLocation.parse(texture);
                    assertResource("textures/" + textureId.getPath() + ".png");
                    materialTextureCount++;
                }
            }
        }
        assertTrue(jsonCount > 0, "No registered model JSON checked");
        assertTrue(objCount > 0, "No registered OBJ wrappers checked");
        assertTrue(mtlCount > 0, "No OBJ material libraries checked");
        assertTrue(materialTextureCount > 0, "No MTL texture dependencies checked");
    }

    private static void assertResource(String path) {
        try (var stream = PlantModelManifestResourceTest.class.getResourceAsStream(ASSET_ROOT + path)) {
            assertNotNull(stream, "Missing plant renderer dependency " + path);
        } catch (IOException exception) { throw new AssertionError(exception); }
    }
    private static String readText(String path) throws IOException {
        try (var stream = PlantModelManifestResourceTest.class.getResourceAsStream(ASSET_ROOT + path)) {
            assertNotNull(stream, "Missing plant renderer dependency " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
