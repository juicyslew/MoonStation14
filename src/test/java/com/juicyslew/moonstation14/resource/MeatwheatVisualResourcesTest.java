package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

class MeatwheatVisualResourcesTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final String SOURCE = ROOT + "textures/ss14/botany/Objects/Specific/Hydroponics/";

    @Test void plantManifestModelsTexturesAndSourceFramesAreComplete() throws Exception {
        var manifest = json(ROOT + "botany/plant-models.json");
        var plants = manifest.getAsJsonObject("plants");
        var states = plants.getAsJsonObject("meatwheat");
        assertNotNull(states);
        assertEquals(8, states.size());
        for (String state : new String[]{"stage-1", "stage-2", "stage-3", "stage-4", "stage-5", "stage-6", "harvest", "dead"}) {
            String id = states.get(state).getAsString();
            String[] parts = id.split(":", 2);
            String modelPath = "/assets/" + parts[0] + "/models/" + parts[1] + ".json";
            var model = json(modelPath);
            assertEquals("minecraft:cutout", model.get("render_type").getAsString(), modelPath);
            var textures = model.getAsJsonObject("textures");
            String texture = textures.get("all").getAsString();
            assertEquals("moonstation14:block/botany/plants/meatwheat/" + state, texture);
            assertImage(texturePath(texture), 32, 32);
            assertArrayEquals(bytes(SOURCE + "meatwheat.rsi/" + state + ".png"),
                    bytes(ROOT + "textures/block/botany/plants/meatwheat/" + state + ".png"), state + " imported frame");
        }
        var metadata = json(SOURCE + "meatwheat.rsi/meta.json");
        assertEquals("CC-BY-SA-3.0", metadata.get("license").getAsString());
        assertTrue(metadata.get("copyright").getAsString().contains("Growth stages, harvest, dead, and produce sprites created by Chaoticaa (GitHub)"));
    }

    @Test void itemModelsUseCanonicalMeatwheatAndPacketLayers() throws Exception {
        var seeds = json(ROOT + "models/item/meatwheat_seeds.json").getAsJsonObject("textures");
        assertEquals("moonstation14:item/botany/meatwheat_packet_seed", seeds.get("layer0").getAsString());
        assertEquals("moonstation14:item/botany/meatwheat_seed", seeds.get("layer1").getAsString());
        assertEquals("moonstation14:item/botany/meatwheat_packet_paper", seeds.get("layer2").getAsString());
        var bushel = json(ROOT + "models/item/meatwheat_bushel.json").getAsJsonObject("textures");
        assertEquals("moonstation14:item/botany/meatwheat_bushel", bushel.get("layer0").getAsString());
        for (String name : new String[]{"meatwheat_packet_seed", "meatwheat_seed", "meatwheat_packet_paper", "meatwheat_bushel"})
            assertImage(ROOT + "textures/item/botany/" + name + ".png", 32, 32);
        assertArrayEquals(bytes(SOURCE + "meatwheat.rsi/seed.png"), bytes(ROOT + "textures/item/botany/meatwheat_seed.png"));
        assertArrayEquals(bytes(SOURCE + "meatwheat.rsi/produce.png"), bytes(ROOT + "textures/item/botany/meatwheat_bushel.png"));
        var seedsMeta = json(SOURCE + "seeds.rsi/meta.json");
        assertEquals("CC-BY-SA-3.0", seedsMeta.get("license").getAsString());
        assertTrue(seedsMeta.get("copyright").getAsString().contains("paper layer by Ian321"));
    }

    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = resource(path)) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertImage(String path, int width, int height) throws Exception {
        try (var stream = resource(path)) {
            var image = ImageIO.read(stream);
            assertNotNull(image, path);
            assertEquals(width, image.getWidth(), path);
            assertEquals(height, image.getHeight(), path);
        }
    }

    private static byte[] bytes(String path) throws Exception {
        try (var stream = resource(path)) {
            return MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes());
        }
    }

    private static InputStream resource(String path) {
        var stream = MeatwheatVisualResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }

    private static String texturePath(String id) {
        String[] parts = id.split(":", 2);
        return "/assets/" + parts[0] + "/textures/" + parts[1] + ".png";
    }
}
