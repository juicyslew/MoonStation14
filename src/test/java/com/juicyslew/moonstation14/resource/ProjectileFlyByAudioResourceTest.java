package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileFlyByAudioResourceTest {
    @Test void soundEventClosesOverAllAttributedMissSamples() throws Exception {
        List<String> expected = List.of("bullet_miss1", "bullet_miss2", "bullet_miss3", "bullet_miss4");
        var jsonPath = "/assets/moonstation14/sounds.json";
        try (var input = getClass().getResourceAsStream(jsonPath)) {
            assertNotNull(input);
            var sounds = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var event = sounds.getAsJsonObject("projectile/fly_by");
            assertNotNull(event);
            var samples = event.getAsJsonArray("sounds");
            assertEquals(expected.size(), samples.size());
            for (int i = 0; i < samples.size(); i++) {
                JsonElement sample = samples.get(i);
                String name = sample.isJsonObject() ? sample.getAsJsonObject().get("name").getAsString() : sample.getAsString();
                assertEquals("moonstation14:ss14/weapons/guns/miss/" + expected.get(i), name);
                assertNotNull(getClass().getResource("/assets/moonstation14/sounds/ss14/weapons/guns/miss/" + expected.get(i) + ".ogg"));
            }
        }
        try (var attribution = getClass().getResourceAsStream(
                "/assets/moonstation14/sounds/ss14/weapons/guns/miss/attributions.yml")) {
            assertNotNull(attribution);
            String text = new String(attribution.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("CC-BY-SA-3.0"));
            assertTrue(text.contains("Taken from CM-SS13"));
            assertTrue(text.contains("0535055a7abcd3016123f2be2cd3db428c122dac/sound/bullets"));
            for (String file : expected) assertTrue(text.contains(file + ".ogg"));
        }
    }
}
