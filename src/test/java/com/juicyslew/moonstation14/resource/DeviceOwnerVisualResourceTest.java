package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DeviceOwnerVisualResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test void configuratorRendererTranslationKeysResolveInEnglish() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream(ROOT+"lang/en_us.json"),StandardCharsets.UTF_8)){
            var translations=JsonParser.parseReader(reader).getAsJsonObject();
            for(String key:new String[]{"gui.moonstation14.devicenet.configurator.clear","gui.moonstation14.devicenet.configurator.link_defaults",
                    "gui.moonstation14.devicenet.configurator.done","gui.moonstation14.devicenet.configurator.link",
                    "gui.moonstation14.devicenet.configurator.sources","gui.moonstation14.devicenet.configurator.sinks",
                    "gui.moonstation14.devicenet.configurator.from","gui.moonstation14.devicenet.configurator.to",
                    "gui.moonstation14.devicenet.configurator.link_defaults.tooltip","gui.moonstation14.devicenet.configurator.defaults",
                    "gui.moonstation14.devicenet.configurator.configure","gui.moonstation14.devicenet.configurator.list",
                    "gui.moonstation14.devicenet.configurator.staged"})
                assertTrue(translations.has(key),"missing English translation "+key);
        }
    }

    @Test void configuratorUsesThirtyTwoUpstreamFramesAtTwoMinecraftTicksAndModeOverlayProperty() throws Exception {
        var model = read("models/item/network_configurator.json");
        assertEquals("moonstation14:item/network_configurator-strip", model.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals("moonstation14:item/network_configurator-mode-link", model.getAsJsonObject("textures").get("layer1").getAsString());
        assertEquals("moonstation14:configurator_mode", model.getAsJsonArray("overrides").get(0).getAsJsonObject()
                .getAsJsonObject("predicate").keySet().iterator().next());
        var list = read("models/item/network_configurator_list.json");
        assertEquals("moonstation14:item/network_configurator-mode-list", list.getAsJsonObject("textures").get("layer1").getAsString());
        var animation = read("textures/item/network_configurator-strip.png.mcmeta").getAsJsonObject().getAsJsonObject("animation");
        assertEquals(2, animation.get("frametime").getAsInt());
        assertEquals(32, animation.getAsJsonArray("frames").size());
        for (int i = 0; i < 32; i++) {
            assertEquals(i, animation.getAsJsonArray("frames").get(i).getAsJsonObject().get("index").getAsInt());
            assertEquals(2, animation.getAsJsonArray("frames").get(i).getAsJsonObject().get("time").getAsInt());
        }
        image("textures/item/network_configurator-strip.png", 32, 1024);
        image("textures/item/network_configurator-mode-link.png", 32, 32);
        image("textures/item/network_configurator-mode-list.png", 32, 32);
    }

    @Test void controlsUseSingleDirectionalFramesAndFlatGeneratedDropModels() throws Exception {
        for (String control : new String[]{"signal_button", "signal_switch"}) {
            var item = read("models/item/" + control + ".json");
            assertEquals("minecraft:item/generated", item.get("parent").getAsString());
            String state = read("blockstates/" + control + ".json").getAsJsonObject().getAsJsonObject("variants").toString();
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                assertTrue(state.contains(control + "_" + facing), "missing authored directional frame " + facing);
                if (control.equals("signal_button")) image("textures/block/signal_button_" + facing + ".png", 32, 32);
                else {
                    image("textures/block/signal_switch_off_" + facing + ".png", 32, 32);
                    image("textures/block/signal_switch_" + facing + ".png", 32, 32);
                }
                var block = read("models/block/" + control + "_" + facing + ".json");
                String expected = control.equals("signal_button") ? "signal_button_" + facing : "signal_switch_off_" + facing;
                assertTrue(block.getAsJsonObject("textures").get("front").getAsString().endsWith(expected));
            }
        }
        var switchState = read("blockstates/signal_switch.json").getAsJsonObject().getAsJsonObject("variants");
        for (String facing : new String[]{"north", "east", "south", "west"})
            assertTrue(switchState.get("facing=" + facing + ",on=true").getAsJsonObject().get("model").getAsString().contains("signal_switch_on_" + facing));
        for (String control : new String[]{"signal_button", "signal_switch"}) {
            var profile = readFromClasspath("data/moonstation14/moonstation14/loose_object_profile/" + control + ".json");
            assertEquals("flat_sprite", profile.get("render_kind").getAsString());
        }
    }

    @Test void directionalControlFacesCropToTheCompleteVisibleFrameBounds() throws Exception {
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            assertVisibleBounds("models/block/signal_button_" + facing + ".json",
                    "textures/block/signal_button_" + facing + ".png");
            String switchModel = "models/block/signal_switch_" + facing + ".json";
            assertVisibleBounds(switchModel, "textures/block/signal_switch_off_" + facing + ".png");
            assertInheritedVisibleBounds("models/block/signal_switch_on_" + facing + ".json",
                    "moonstation14:block/signal_switch_" + facing,
                    switchModel, "textures/block/signal_switch_" + facing + ".png");
        }
    }

    private static void assertInheritedVisibleBounds(String childPath, String expectedParent,
                                                    String parentModelPath, String texturePath) throws Exception {
        var child = read(childPath);
        assertEquals(expectedParent, child.get("parent").getAsString(), "live model inheritance for " + childPath);
        assertVisibleBounds(parentModelPath, texturePath);
    }

    private static void assertVisibleBounds(String modelPath, String texturePath) throws Exception {
        var model = read(modelPath);
        var uv = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces")
                .getAsJsonObject("north").getAsJsonArray("uv");
        try (var in = DeviceOwnerVisualResourceTest.class.getResourceAsStream(ROOT + texturePath)) {
            assertNotNull(in, "Missing image " + texturePath);
            var image = ImageIO.read(in);
            int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
            assertTrue(maxX >= minX && maxY >= minY, "Frame has no visible pixels: " + texturePath);
            assertEquals(minX / 2.0, uv.get(0).getAsDouble(), texturePath + " left UV");
            assertEquals(minY / 2.0, uv.get(1).getAsDouble(), texturePath + " top UV");
            assertEquals((maxX + 1) / 2.0, uv.get(2).getAsDouble(), texturePath + " right UV");
            assertEquals((maxY + 1) / 2.0, uv.get(3).getAsDouble(), texturePath + " bottom UV");
        }
    }

    private static com.google.gson.JsonObject read(String path) throws Exception {
        return readFromClasspath(ROOT + path);
    }

    private static com.google.gson.JsonObject readFromClasspath(String path) throws Exception {
        try (var in = DeviceOwnerVisualResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void image(String path, int width, int height) throws Exception {
        try (var in = DeviceOwnerVisualResourceTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(in, "Missing image " + path);
            var image = ImageIO.read(in);
            assertNotNull(image);
            assertEquals(width, image.getWidth(), path);
            assertEquals(height, image.getHeight(), path);
        }
    }
}
