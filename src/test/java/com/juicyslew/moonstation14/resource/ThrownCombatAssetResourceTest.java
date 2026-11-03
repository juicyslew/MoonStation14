package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class ThrownCombatAssetResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test
    void allThrownCombatItemsHaveGeneratedModelsAndTheirImportedIcons() {
        var expected = java.util.Map.of(
                "throwing_knife", "moonstation14:ss14/throwing_knife/icon",
                "dart", "moonstation14:ss14/dart/icon",
                "hypo_dart", "moonstation14:ss14/dart/icon",
                "bola", "moonstation14:ss14/bola/icon",
                "armor_vest", "moonstation14:ss14/armor_vest/icon",
                "basic_helmet", "moonstation14:ss14/basic_helmet/icon");
        var atlas = readMinecraft("atlases/blocks.json");
        var atlasSprites = new java.util.HashSet<String>();
        atlas.getAsJsonArray("sources").forEach(source -> {
            var object = source.getAsJsonObject();
            if (object.get("type").getAsString().equals("single"))
                atlasSprites.add(object.get("resource").getAsString());
        });
        for (var entry : expected.entrySet()) {
            String item = entry.getKey();
            var model = read("models/item/" + item + ".json");
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            String icon = model.getAsJsonObject("textures").get("layer0").getAsString();
            assertEquals(entry.getValue(), icon, item);
            assertTrue(atlasSprites.contains(icon), "Active minecraft blocks atlas lacks " + icon);
        }
        assertEquals(5, atlasSprites.size(), "Only the five unique thrown-combat icons belong in this atlas patch");
        for (String icon : atlasSprites) validateIcon(icon);
    }

    @Test
    void soundEventResourcesAndAttributionArePresent() {
        var sounds = read("sounds.json");
        for (String id : new String[]{"ss14/weapons/star_hit", "ss14/weapons/pierce", "ss14/weapons/bolathrow", "ss14/effects/snap"}) {
            var entries = sounds.getAsJsonObject(id).getAsJsonArray("sounds");
            assertFalse(entries.isEmpty());
            var sample = entries.get(0);
            String eventName = sample.isJsonObject() ? sample.getAsJsonObject().get("name").getAsString() : sample.getAsString();
            var sound = net.minecraft.resources.ResourceLocation.parse(eventName);
            assertNotNull(getClass().getResource("/assets/" + sound.getNamespace() + "/sounds/" + sound.getPath() + ".ogg"), sound.toString());
        }
        assertNotNull(getClass().getResource(ROOT + "attributions/thrown-combat-assets.txt"));
    }

    private static com.google.gson.JsonObject read(String path) {
        try (var stream = ThrownCombatAssetResourceTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(stream, "Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static com.google.gson.JsonObject readMinecraft(String path) {
        try (var stream = ThrownCombatAssetResourceTest.class.getResourceAsStream("/assets/minecraft/" + path)) {
            assertNotNull(stream, "Missing active Minecraft resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static void validateIcon(String location) {
        var id = net.minecraft.resources.ResourceLocation.parse(location);
        String base = "/assets/" + id.getNamespace() + "/textures/" + id.getPath();
        try (var stream = ThrownCombatAssetResourceTest.class.getResourceAsStream(base + ".png")) {
            assertNotNull(stream, "Missing PNG " + location);
            var image = ImageIO.read(stream);
            assertNotNull(image, "Undecodable PNG " + location);
            assertEquals(32, image.getWidth(), location);
            assertEquals(32, image.getHeight(), location);
            boolean alpha = false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) >>> 24) != 0) alpha = true;
            assertTrue(alpha, "PNG has no nontransparent pixels: " + location);
        } catch (Exception e) { throw new AssertionError(e); }
        try (var stream = ThrownCombatAssetResourceTest.class.getResourceAsStream(base.substring(0, base.lastIndexOf('/')) + "/meta.json")) {
            assertNotNull(stream, "Missing source metadata for " + location);
            var meta = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("CC-BY-SA-3.0", meta.get("license").getAsString(), location);
            assertTrue(meta.has("copyright"), "Missing copyright attribution " + location);
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
