package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MachineVisualStateBindingTest {
    @Test void grinderAndExtractorBindOnlySourceSupportedVisualStatesToInstalledModels() throws Exception {
        var grinder = read("/assets/moonstation14/blockstates/reagent_grinder.json");
        for (String working : new String[]{"false", "true"}) {
            assertEquals("moonstation14:block/machines/grinder/grinder_empty", grinder.getAsJsonObject("variants")
                    .getAsJsonObject("beaker_attached=false,working=" + working).get("model").getAsString());
            assertEquals("moonstation14:block/machines/grinder/grinder_beaker_attached", grinder.getAsJsonObject("variants")
                    .getAsJsonObject("beaker_attached=true,working=" + working).get("model").getAsString());
        }
        assertEquals(4, grinder.getAsJsonObject("variants").size(), "working reuses both installed beaker models; no running skin");
        var extractor = read("/assets/moonstation14/blockstates/seed_extractor.json");
        assertEquals("moonstation14:block/machines/seed_extractor/seedextractor_off", extractor.getAsJsonObject("variants")
                .getAsJsonObject("powered=false").get("model").getAsString());
        assertEquals("moonstation14:block/machines/seed_extractor/seedextractor_unlit", extractor.getAsJsonObject("variants")
                .getAsJsonObject("powered=true").get("model").getAsString());
        for (String model : new String[]{"/assets/moonstation14/models/block/machines/grinder/grinder_empty.json",
                "/assets/moonstation14/models/block/machines/grinder/grinder_beaker_attached.json",
                "/assets/moonstation14/models/block/machines/seed_extractor/seedextractor_off.json",
                "/assets/moonstation14/models/block/machines/seed_extractor/seedextractor_unlit.json"})
            try (var stream = getClass().getResourceAsStream(model)) { assertNotNull(stream, model); }
        var unlit = read("/assets/moonstation14/models/block/machines/seed_extractor/seedextractor_unlit.json");
        var elements = unlit.getAsJsonArray("elements");
        var inset = java.util.stream.StreamSupport.stream(elements.spliterator(), false)
                .map(com.google.gson.JsonElement::getAsJsonObject).filter(e -> e.has("name") && e.get("name").getAsString().equals("seed_display_inset"))
                .findFirst().orElseThrow();
        assertFalse(inset.get("shade").getAsBoolean());
        for (String face : new String[]{"north", "south"}) {
            var faceData = inset.getAsJsonObject("faces").getAsJsonObject(face).getAsJsonObject("neoforge_data");
            assertEquals(15, faceData.get("block_light").getAsInt());
            assertEquals(15, faceData.get("sky_light").getAsInt());
            assertFalse(faceData.get("ambient_occlusion").getAsBoolean());
        }
        assertFalse(inset.getAsJsonObject("faces").getAsJsonObject("east").has("neoforge_data"));
        for (var element : elements) {
            var object = element.getAsJsonObject();
            if (!object.get("name").getAsString().equals("seed_display_inset")) {
                assertTrue(object.get("shade").getAsBoolean(), object.get("name").getAsString());
                for (var face : object.getAsJsonObject("faces").entrySet()) assertFalse(face.getValue().getAsJsonObject().has("neoforge_data"));
            }
        }
        var off = read("/assets/moonstation14/models/block/machines/seed_extractor/seedextractor_off.json");
        assertTrue(off.getAsJsonArray("elements").get(0).getAsJsonObject().get("shade").getAsBoolean());
        try (var stream = getClass().getResourceAsStream("/assets/moonstation14/textures/block/machines/seed_extractor/seedextractor_unlit.png.mcmeta")) {
            assertNotNull(stream, "existing animated LED texture metadata");
            var metadata = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animation");
            assertEquals(8, metadata.get("frames").getAsJsonArray().size());
            for (var frame : metadata.get("frames").getAsJsonArray()) assertEquals(2, frame.getAsJsonObject().get("time").getAsInt());
        }
    }
    private com.google.gson.JsonObject read(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
