package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BotanyImportedAttributionResourceTest {
    private static final String ROOT = "/assets/moonstation14/provenance/botany_imports/";
    private static final String REVISION = "c9df5ef5d675b0d1d226828bddf6b78c28502d91";

    @Test void packagedPlantAttributionsCoverUniqueFamiliesAndWearableFrames() throws Exception {
        var families = new HashSet<String>();
        for (int part = 1; part <= 5; part++) {
            var record = json("part%02d.json".formatted(part));
            assertEquals(REVISION, record.get("source_revision").getAsString());
            for (var entry : record.getAsJsonObject("plants").entrySet()) {
                assertTrue(families.add(entry.getKey()), "duplicate attribution family: " + entry.getKey());
                var source = entry.getValue().getAsJsonObject();
                assertFalse(source.get("source_rsi").getAsString().isBlank(), entry.getKey());
                assertFalse(source.get("license").getAsString().isBlank(), entry.getKey());
                assertFalse(source.get("copyright").getAsString().isBlank(), entry.getKey());
                assertTrue(source.get("source_rsi").getAsString().endsWith("/" + entry.getKey() + ".rsi"), entry.getKey());
            }
        }
        var manifest = jsonAt("/assets/moonstation14/botany/plant-models.json").getAsJsonObject("plants");
        var expectedFamilies = new HashSet<>(manifest.keySet());
        expectedFamilies.removeAll(Set.of("wheat", "tomato", "potato", "poppy", "aloe", "meatwheat"));
        assertEquals(expectedFamilies, families, "attribution must match the plant manifest except explicit legacy families");
        assertEquals(63, families.size()); // 69 manifest families minus the six explicitly legacy exclusions.

        String notice;
        try (var stream = resource("ATTRIBUTION.md")) {
            notice = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String frame : new String[]{"botany_ambrosia_deus.png", "botany_ambrosia_vulgaris.png", "botany_lily.png",
                "botany_banana_peel.png", "botany_mimana_peel.png", "hat_watermelon.png", "hat_holy_watermelon.png",
                "hat_holy_watermelon__f1.png", "hat_holy_watermelon__f2.png", "hat_holy_watermelon__f3.png"})
            assertTrue(notice.contains(frame), "wearable attribution missing frame: " + frame);
    }

    private static com.google.gson.JsonObject json(String name) throws Exception {
        try (var stream = resource(name)) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static com.google.gson.JsonObject jsonAt(String path) throws Exception {
        try (var stream = BotanyImportedAttributionResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static java.io.InputStream resource(String name) {
        var stream = BotanyImportedAttributionResourceTest.class.getResourceAsStream(ROOT + name);
        assertNotNull(stream, ROOT + name);
        return stream;
    }
}
