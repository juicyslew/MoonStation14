package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthAnalyzerAnimationResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test
    void modelLayersBaseIconAndAnimatedOverlayWithUpstreamFrameTiming() throws Exception {
        var model = read("models/item/handheld_health_analyzer.json");
        assertEquals("moonstation14:item/medical/analyzer/icon", model.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals("moonstation14:item/medical/analyzer/analyzer-animation", model.getAsJsonObject("textures").get("layer1").getAsString());
        var metadata = read("textures/item/medical/analyzer/analyzer-animation.png.mcmeta").getAsJsonObject("animation");
        var frames = metadata.getAsJsonArray("frames");
        assertEquals(12, frames.size());
        for (int i = 0; i < 11; i++) {
            assertEquals(i, frames.get(i).getAsJsonObject().get("index").getAsInt());
            assertEquals(2, frames.get(i).getAsJsonObject().get("time").getAsInt());
        }
        assertEquals(11, frames.get(11).getAsJsonObject().get("index").getAsInt());
        assertEquals(10, frames.get(11).getAsJsonObject().get("time").getAsInt());
        try (var image = getClass().getResourceAsStream(ROOT + "textures/item/medical/analyzer/analyzer-animation.png")) {
            assertTrue(image != null);
            var bitmap = javax.imageio.ImageIO.read(image);
            assertEquals(32, bitmap.getWidth());
            assertEquals(384, bitmap.getHeight());
        }
    }

    private static com.google.gson.JsonObject read(String path) throws Exception {
        try (var stream = HealthAnalyzerAnimationResourceTest.class.getResourceAsStream(ROOT + path)) {
            assertTrue(stream != null, "Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
