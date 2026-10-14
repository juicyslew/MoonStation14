package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class EmitterImpactParticleResourceTest {
    @Test void registeredSpriteSetSelectsPixelExactImportedFramesPerParticleAge() throws Exception {
        try (var in = getClass().getResourceAsStream("/assets/moonstation14/particles/emitter_impact.json")) {
            assertNotNull(in);
            var json = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var frames = json.getAsJsonArray("textures");
            assertEquals(5, frames.size());
            for (int i = 0; i < frames.size(); i++) {
                assertEquals("moonstation14:emitter_impact/frame_%02d".formatted(i), frames.get(i).getAsString());
                try (var frame = getClass().getResourceAsStream("/assets/moonstation14/textures/particle/emitter_impact/frame_%02d.png".formatted(i))) {
                    assertNotNull(frame);
                    var image = javax.imageio.ImageIO.read(frame);
                    assertEquals(32, image.getWidth()); assertEquals(32, image.getHeight());
                }
            }
        }
        var original = javax.imageio.ImageIO.read(getClass().getResourceAsStream(
                "/assets/moonstation14/provenance/emitter_impact/impact_bullet_source.png"));
        for (int i = 0; i < 5; i++) {
            var frame = javax.imageio.ImageIO.read(getClass().getResourceAsStream(
                    "/assets/moonstation14/textures/particle/emitter_impact/frame_%02d.png".formatted(i)));
            int ox = i % 2 * 32, oy = i / 2 * 32;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
                assertEquals(original.getRGB(ox + x, oy + y), frame.getRGB(x, y));
        }
        assertEquals(java.util.List.of(0,1,2,3,4), java.util.stream.IntStream.range(0,5)
                .map(com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy::index).boxed().toList());
        assertEquals(java.util.List.of(0,1,2,3,4,0,1,2,3,4), java.util.stream.IntStream.range(0,10)
                .map(com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy::index).boxed().toList());
        for (int age = 0; age < 5; age++)
            assertFalse(com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.shouldRemoveAfterTick(age,5));
        assertTrue(com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.shouldRemoveAfterTick(5,5));
        assertEquals(0, com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.index(0));
        assertTrue(com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.shouldRemoveAfterTick(1,1));
        assertTrue(com.juicyslew.moonstation14.entities.EmitterImpactParticleOptions.STREAM_CODEC != null);
        assertEquals("emitter_impact", com.juicyslew.moonstation14.entities.ModEmitterImpactParticles.EMITTER_IMPACT.getId().getPath());
    }
}
