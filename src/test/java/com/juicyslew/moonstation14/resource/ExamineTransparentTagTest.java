package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamineTransparentTagTest {
    @Test
    void explicitlyClassifiesClearGlassAndPanesButNotTintedGlass() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/moonstation14/tags/block/examine_transparent.json")) {
            assertTrue(stream != null, "Missing examine transparency tag");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> values = json.getAsJsonArray("values").asList().stream()
                    .map(element -> element.getAsString()).collect(Collectors.toSet());
            assertTrue(values.contains("minecraft:glass"));
            assertTrue(values.contains("minecraft:glass_pane"));
            assertTrue(values.contains("minecraft:blue_stained_glass"));
            assertTrue(values.contains("minecraft:blue_stained_glass_pane"));
            assertFalse(values.contains("minecraft:tinted_glass"));
        }
    }
}
