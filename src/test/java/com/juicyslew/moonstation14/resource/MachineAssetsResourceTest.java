package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MachineAssetsResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test
    void existingBlockAndItemIdsAliasDefaultMachineModels() {
        assertEquals("moonstation14:block/machines/grinder/default", read("models/block/reagent_grinder.json").get("parent").getAsString());
        assertEquals("moonstation14:block/machines/seed_extractor/default", read("models/block/seed_extractor.json").get("parent").getAsString());
        assertEquals("moonstation14:block/reagent_grinder", read("models/item/reagent_grinder.json").get("parent").getAsString());
        assertEquals("moonstation14:block/seed_extractor", read("models/item/seed_extractor.json").get("parent").getAsString());
        assertEquals("moonstation14:block/reagent_grinder", read("blockstates/reagent_grinder.json").getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertEquals("moonstation14:block/seed_extractor", read("blockstates/seed_extractor.json").getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void importedMachineModelDependenciesAndNativeObjLoaderExist() {
        for (String family : Set.of("grinder", "seed_extractor")) {
            for (String model : family.equals("grinder")
                    ? Set.of("default", "grinder_beaker_attached", "grinder_empty")
                    : Set.of("default", "seedextractor")) {
                JsonObject json = read("models/block/machines/" + family + "/" + model + ".json");
                if (json.has("loader")) {
                    assertEquals("neoforge:obj", json.get("loader").getAsString());
                    String obj = json.get("model").getAsString().replace("moonstation14:", "");
                    assertNotNull(resource(obj));
                    assertNotNull(resource(obj.substring(0, obj.length() - 4) + ".mtl"));
                }
                for (var texture : json.getAsJsonObject("textures").entrySet()) {
                    String id = texture.getValue().getAsString();
                    if (id.startsWith("moonstation14:")) {
                        String path = id.substring("moonstation14:".length());
                        assertNotNull(resource("textures/" + path + ".png"), path);
                    }
                }
            }
        }
        assertNotNull(resource("textures/block/machines/seed_extractor/seedextractor.png.mcmeta"));
        assertNotNull(resource("textures/block/machines/seed_extractor/seedextractor_unlit.png.mcmeta"));
    }

    private static JsonObject read(String path) {
        try (var stream = resource(path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) { throw new AssertionError("Unable to read " + path, exception); }
    }

    private static java.io.InputStream resource(String path) {
        return MachineAssetsResourceTest.class.getResourceAsStream(ROOT + path);
    }
}
