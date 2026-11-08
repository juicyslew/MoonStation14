package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Focused native bindings checks for atmos-machine model states and their source dependencies. */
class AtmosMachineModelResourceTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final String DATA = "/data/moonstation14/moonstation14/atmos_machine/";
    private static final String[] PROFILE_RESOURCES = {"atmos_machine_base", "atmos_machine_base_powered",
            "constructible_machine_base", "gas_pipe_sans_layers", "gas_pipe_base", "gas_binary_base",
            "gas_trinary_base", "gas_thermo_base", "teg_center", "teg_circulator", "gas_pressure_pump",
            "gas_volume_pump", "gas_valve", "gas_mixer", "gas_filter", "gas_thermo_heater",
            "gas_thermo_freezer", "passive_heat_exchanger"};
    private static final String[] BLOCKS = {"teg_center", "teg_circulator", "gas_pressure_pump",
            "gas_volume_pump", "gas_valve", "gas_mixer", "gas_filter", "gas_thermo_heater",
            "gas_thermo_freezer", "passive_heat_exchanger"};
    private static final String[] FACINGS = {"south", "north", "east", "west"};
    private static final Map<String, ExpectedVisual> VISUALS = Map.ofEntries(
            Map.entry("teg_center", new ExpectedVisual("Structures/Power/Generation/teg.rsi", "teg")),
            Map.entry("teg_circulator", new ExpectedVisual("Structures/Power/Generation/teg.rsi", "circ-0")),
            Map.entry("gas_pressure_pump", new ExpectedVisual("Structures/Piping/Atmospherics/pump.rsi", "pumpPressure")),
            Map.entry("gas_volume_pump", new ExpectedVisual("Structures/Piping/Atmospherics/pump.rsi", "pumpVolume")),
            Map.entry("gas_valve", new ExpectedVisual("Structures/Piping/Atmospherics/pump.rsi", "pumpManualValve")),
            Map.entry("gas_mixer", new ExpectedVisual("Structures/Piping/Atmospherics/gasmixer.rsi", "gasMixer")),
            Map.entry("gas_filter", new ExpectedVisual("Structures/Piping/Atmospherics/gasfilter.rsi", "gasFilter")),
            Map.entry("gas_thermo_heater", new ExpectedVisual("Structures/Piping/Atmospherics/thermomachine.rsi", "heater")),
            Map.entry("gas_thermo_freezer", new ExpectedVisual("Structures/Piping/Atmospherics/thermomachine.rsi", "freezer")),
            Map.entry("passive_heat_exchanger", new ExpectedVisual("Structures/Piping/Atmospherics/heatexchanger.rsi", "heStraight")));

    @Test
    void everyRegisteredMachineUsesItsResolvedProfileVisualAndCompleteNativeVariantBindings() {
        var resolved = resolvedProfiles();
        assertEquals(Set.of(BLOCKS), resolved.keySet());
        for (String block : BLOCKS) {
            AtmosMachineProfile profile = resolved.get(block);
            ExpectedVisual expected = VISUALS.get(block);
            assertEquals(expected.sprite(), profile.visual().sprite(), block + " resolved visual sprite");
            assertEquals(expected.state(), profile.visual().state(), block + " resolved visual state");

            JsonObject variants = read("blockstates/" + block + ".json").getAsJsonObject("variants");
            assertEquals(360, variants.size(), block + " facing/display/pipe_layer selector count");
            var checkedModels = new java.util.HashSet<String>();
            for (String facing : FACINGS) for (int display = 0; display <= 29; display++)
                for (int layer = 0; layer <= 2; layer++) {
                    String key = "facing=" + facing + ",display=" + display + ",pipe_layer=" + layer;
                    JsonObject variant = variants.getAsJsonObject(key);
                    assertNotNull(variant, block + " " + key);
                    String modelId = variant.get("model").getAsString();
                    if (Set.of("teg_center", "teg_circulator").contains(block)) {
                        int expectedYaw = switch (facing) { case "north" -> 0; case "east" -> 90; case "south" -> 180; case "west" -> 270; default -> throw new AssertionError(facing); };
                        assertEquals(expectedYaw, variant.get("y").getAsInt(), block + " R5 facing yaw");
                    } else assertFalse(variant.has("y"), block + " uses direction-specific art without an extra rotation");
                    if (checkedModels.add(modelId)) {
                        JsonObject model = read("models/" + modelId.substring(modelId.indexOf(':') + 1) + ".json");
                        if (Set.of("teg_center", "teg_circulator").contains(block)) {
                            assertEquals("neoforge:obj", model.get("loader").getAsString(), block + " R5 OBJ wrapper");
                            String obj = model.get("model").getAsString().substring("moonstation14:".length());
                            assertNotNull(resource(obj), block + " R5 OBJ");
                            String text = readText(obj);
                            String mtl = java.util.regex.Pattern.compile("(?m)^mtllib\\s+(.+)$").matcher(text).results()
                                    .map(m -> m.group(1).trim()).findFirst().orElseThrow();
                            String mtlPath = obj.substring(0, obj.lastIndexOf('/') + 1) + mtl;
                            assertNotNull(resource(mtlPath), block + " R5 MTL");
                            String texture = java.util.regex.Pattern.compile("(?m)^map_Kd\\s+(.+)$").matcher(readText(mtlPath)).results()
                                    .map(m -> m.group(1).trim()).findFirst().orElseThrow();
                            assertTrue(texture.startsWith("#"), "R5 MTL uses its wrapper atlas key");
                            String texturePath = "textures/" + model.getAsJsonObject("textures").get(texture.substring(1)).getAsString().substring("moonstation14:".length()) + ".png";
                            assertNotNull(resource(texturePath), block + " R5 atlas");
                            continue;
                        }
                        if (Set.of("gas_pressure_pump", "gas_volume_pump", "gas_mixer", "gas_filter", "gas_valve", "passive_heat_exchanger", "gas_thermo_heater", "gas_thermo_freezer").contains(block)) {
                            assertEquals("neoforge:obj", model.get("loader").getAsString(), block + " OBJ wrapper loader");
                            String dependency = model.get("model").getAsString();
                            String objPath = dependency.substring(dependency.indexOf(':') + 1);
                            String mtlName = java.util.regex.Pattern.compile("(?m)^mtllib\\s+(.+)$").matcher(readText(objPath)).results()
                                    .map(m -> m.group(1).trim()).findFirst().orElseThrow(() -> new AssertionError(block + " OBJ missing mtllib"));
                            String mtlPath = objPath.substring(0, objPath.lastIndexOf('/') + 1) + mtlName;
                            assertNotNull(resource(mtlPath), block + " OBJ material dependency");
                            String texture = java.util.regex.Pattern.compile("(?m)^map_Kd\\s+(.+)$").matcher(readText(mtlPath)).results()
                                    .map(m -> m.group(1).trim()).findFirst().orElseThrow(() -> new AssertionError(block + " MTL missing map_Kd"));
                            String texturePath;
                            if (texture.startsWith("#")) {
                                JsonObject textures = model.getAsJsonObject("textures");
                                var seen = new java.util.HashSet<String>();
                                String textureKey;
                                while (texture.startsWith("#")) {
                                    textureKey = texture.substring(1);
                                    assertTrue(seen.add(textureKey), block + " cyclic OBJ texture reference " + texture);
                                    assertTrue(textures.has(textureKey), block + " missing OBJ texture key " + textureKey);
                                    texture = textures.get(textureKey).getAsString();
                                }
                                assertTrue(texture.startsWith("moonstation14:"), block + " unexpected OBJ texture resource " + texture);
                                texturePath = "textures/" + texture.substring("moonstation14:".length()) + ".png";
                            } else {
                                texturePath = java.nio.file.Path.of(mtlPath).getParent().resolve(texture).normalize().toString().replace('\\', '/');
                                assertFalse(texturePath.equals("..") || texturePath.startsWith("../"), block + " MTL texture escapes asset root");
                            }
                            assertNotNull(resource(texturePath), block + " MTL texture dependency");
                            continue;
                        }
                        assertTrue(modelId.endsWith("_" + facing), block + " must select matching RSI direction: " + key);
                        assertEquals("minecraft:cutout", model.get("render_type").getAsString(), block + " cutout render type");
                        assertResolvedTextures(model, block + " " + key);
                    }
                }
            JsonObject item = read("models/item/" + block + ".json");
            String itemParent = item.get("parent").getAsString();
            assertNotNull(resource("models/" + itemParent.substring(itemParent.indexOf(':') + 1) + ".json"), block + " item parent");
        }
    }

    @Test
    void meaningfulDisplaysComposeSourceStatesAndLayerVariants() {
        assertObjSelector("gas_pressure_pump", 14, 0, "north", "pumppressure_l0_north");
        assertObjSelector("gas_pressure_pump", 13, 0, "north", "pumppressureon_l0_north");
        assertObjSelector("gas_pressure_pump", 14, 1, "north", "pumppressure_l1_north");
        assertObjSelector("gas_volume_pump", 15, 0, "east", "pumpvolume_l0_east");
        assertObjSelector("gas_volume_pump", 16, 0, "east", "pumpvolumeblocked_l0_east");
        assertObjSelector("gas_volume_pump", 17, 0, "east", "pumpvolumeon_l0_east");
        assertObjSelector("gas_valve", 22, 0, "west", "pumpmanualvalveon_l0_west");
        assertObjSelector("gas_valve", 23, 0, "west", "pumpmanualvalve_l0_west");
        assertObjSelector("gas_mixer", 18, 2, "south", "gasmixeron_l2_south");
        assertObjSelector("gas_filter", 21, 1, "north", "gasfilter_l1_north");
        assertObjSelector("gas_thermo_heater", 24, 0, "east", "on_pipe_0_east");
        assertObjSelector("gas_thermo_heater", 25, 0, "east", "off_pipe_0_east");
        assertObjSelector("gas_thermo_freezer", 24, 2, "west", "on_pipe_2_west");
        assertObjSelector("gas_thermo_freezer", 25, 1, "south", "off_pipe_1_south");
        assertObjSelector("passive_heat_exchanger", 0, 0, "south", "hestraight_l0_south");

        assertEquals("moonstation14:block/power_collection/generation_teg/unpowered", selector("teg_center", 0, 0, "north"));
        for (int output=1; output<=12; output++)
            assertEquals("moonstation14:block/power_collection/generation_teg/powered_l"+String.format("%02d", output-1), selector("teg_center", output, 2, "east"));
        assertEquals("moonstation14:block/power_collection/generation_teg__tegcirculator/still_unlit", selector("teg_circulator", 26, 1, "south"));
        assertEquals("moonstation14:block/power_collection/generation_teg__tegcirculator/still_lit", selector("teg_circulator", 27, 0, "north"));
        assertEquals("moonstation14:block/power_collection/generation_teg__tegcirculator/slow_lit", selector("teg_circulator", 28, 0, "west"));
        assertEquals("moonstation14:block/power_collection/generation_teg__tegcirculator/fast_lit", selector("teg_circulator", 29, 0, "east"));
        assertEquals(selector("teg_center", 0, 0, "north"), selector("teg_center", 29, 2, "south"));
        assertEquals(selector("teg_circulator", 26, 0, "north"), selector("teg_circulator", 25, 2, "south"));
    }

    private static String selector(String block, int display, int layer, String facing) {
        return read("blockstates/" + block + ".json").getAsJsonObject("variants")
                .getAsJsonObject("facing="+facing+",display="+display+",pipe_layer="+layer).get("model").getAsString();
    }

    @Test
    void everyR5SemanticAndFallbackSelectorUsesCanonicalFacingYaw() {
        var yaw = Map.of("north", 0, "east", 90, "south", 180, "west", 270);
        for (String block : java.util.List.of("teg_center", "teg_circulator"))
            for (int display=0; display<30; display++) for (var facing : yaw.entrySet())
                for (int layer=0; layer<3; layer++) {
                    String key="facing="+facing.getKey()+",display="+display+",pipe_layer="+layer;
                    JsonObject binding=read("blockstates/"+block+".json").getAsJsonObject("variants").getAsJsonObject(key);
                    assertEquals(facing.getValue(),binding.get("y").getAsInt(),block+" "+key);
                }
    }

    @Test
    void convertedAnimationMetadataMatchesRsiTimingAndRepresentativeFramesRemainExact() throws Exception {
        assertAnimation("piping/atmospherics/pump/pumpPressureOn_north.png.mcmeta", 2, 5);
        assertAnimation("piping/atmospherics/pump/pumpVolumeBlocked_south.png.mcmeta", 20, 2);
        assertAnimation("power/generation/teg/circ-1_north.png.mcmeta", 4, 4);
        assertAnimation("piping/atmospherics/thermomachine/heaterOn_south.png.mcmeta", 2, 10);
        assertFrameEquals("textures/ss14/structures/piping/atmospherics/pump.rsi/pumpPressure.png",
                "textures/block/atmos_machine/piping/atmospherics/pump/pumpPressure_east.png", 2);
        assertFrameEquals("textures/ss14/structures/piping/atmospherics/thermomachine.rsi/heaterOff.png",
                "textures/block/atmos_machine/piping/atmospherics/thermomachine/heaterOff_south.png", 0);
        assertFrameEquals("textures/ss14/structures/piping/atmospherics/pipe.rsi/pipeStraight.png",
                "textures/block/atmos_machine/piping/atmospherics/pipe/pipeStraight_west.png", 3);
    }

    @Test
    void semanticTegStatesUseCompleteNativeObjSnapshots() {
        for (int display : java.util.stream.IntStream.rangeClosed(0, 12).toArray())
            assertEquals("neoforge:obj", model("teg_center", display, 0, "north").get("loader").getAsString());
        for (int display : java.util.List.of(26, 27, 28, 29))
            assertEquals("neoforge:obj", model("teg_circulator", display, 0, "north").get("loader").getAsString());
    }

    private static Map<String, AtmosMachineProfile> resolvedProfiles() {
        var manager = new PrototypeManager();
        manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String id : PROFILE_RESOURCES) raw.put(ModAtmosMachines.id(id), readData(id));
        manager.reload(ModAtmosMachines.TYPE, raw);
        var result = new LinkedHashMap<String, AtmosMachineProfile>();
        for (String id : BLOCKS) result.put(id, manager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id(id)));
        return result;
    }

    private static JsonObject readData(String id) {
        try (var stream = AtmosMachineModelResourceTest.class.getResourceAsStream(DATA + id + ".json")) {
            assertNotNull(stream, "Missing profile resource " + id);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) { throw new AssertionError("Unable to load profile " + id, exception); }
    }

    private static JsonObject model(String block, int display, int layer, String facing) {
        JsonObject variant = read("blockstates/" + block + ".json").getAsJsonObject("variants")
                .getAsJsonObject("facing=" + facing + ",display=" + display + ",pipe_layer=" + layer);
        assertNotNull(variant, block + " display/layer/facing resource binding");
        String model = variant.get("model").getAsString();
        return read("models/" + model.substring(model.indexOf(':') + 1) + ".json");
    }

    private static void assertTexture(String block, int display, int layer, String facing, String suffix) {
        assertTrue(model(block, display, layer, facing).getAsJsonObject("textures").entrySet().stream()
                .anyMatch(e -> e.getValue().getAsString().endsWith(suffix)), block + "/" + display + "/" + layer + " expected " + suffix);
    }

    private static void assertObjSelector(String block, int display, int layer, String facing, String state) {
        String model = read("blockstates/" + block + ".json").getAsJsonObject("variants")
                .getAsJsonObject("facing=" + facing + ",display=" + display + ",pipe_layer=" + layer)
                .get("model").getAsString();
        assertTrue(model.toLowerCase(java.util.Locale.ROOT).contains(state), block + " selector should bind " + state);
    }

    private static String readText(String path) {
        try (var stream = resource(path)) {
            assertNotNull(stream, "Missing dependency " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new AssertionError("Unable to read dependency " + path, exception); }
    }

    private static boolean hasTexture(JsonObject model, String suffix) {
        return model.getAsJsonObject("textures").entrySet().stream().anyMatch(e -> e.getValue().getAsString().endsWith(suffix));
    }

    private static boolean hasFullBrightIndicator(JsonObject model) { return firstFullBrightFace(model) != null; }

    private static JsonObject firstFullBrightFace(JsonObject model) {
        for (var element : model.getAsJsonArray("elements"))
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet())
                if (face.getValue().getAsJsonObject().has("neoforge_data")) return face.getValue().getAsJsonObject();
        return null;
    }

    private static void assertResolvedTextures(JsonObject model, String context) {
        JsonObject textures = model.getAsJsonObject("textures");
        for (var entry : textures.entrySet()) {
            String value = entry.getValue().getAsString();
            if (value.startsWith("#")) assertTrue(textures.has(value.substring(1)), context + " unresolved texture variable " + value);
            else if (value.startsWith("moonstation14:")) {
                String path = "textures/" + value.substring("moonstation14:".length()) + ".png";
                assertNotNull(resource(path), context + " missing texture file " + path);
            }
        }
    }

    private static void assertAnimation(String path, int ticks, int frames) {
        JsonObject animation = read("textures/block/atmos_machine/" + path).getAsJsonObject("animation");
        assertEquals(ticks, animation.get("frametime").getAsInt(), path + " frame ticks");
        assertEquals(frames, animation.getAsJsonArray("frames").size(), path + " frame count");
        for (int i = 0; i < frames; i++) {
            var frame = animation.getAsJsonArray("frames").get(i);
            if (frame.isJsonObject()) {
                assertEquals(i, frame.getAsJsonObject().get("index").getAsInt(), path + " frame sequence");
                assertEquals(ticks, frame.getAsJsonObject().get("time").getAsInt(), path + " per-frame timing");
            } else assertEquals(i, frame.getAsInt(), path + " frame sequence");
        }
    }

    private static void assertFrameEquals(String sourcePath, String outputPath, int frameIndex) throws Exception {
        BufferedImage source = image(sourcePath);
        BufferedImage derived = image(outputPath);
        int columns = source.getWidth() / 32;
        int sourceX = (frameIndex % columns) * 32;
        int sourceY = (frameIndex / columns) * 32;
        assertEquals(32, derived.getWidth(), outputPath + " width");
        assertEquals(32, derived.getHeight(), outputPath + " height");
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            assertEquals(source.getRGB(sourceX + x, sourceY + y), derived.getRGB(x, y), outputPath + " exact pixel " + x + "," + y);
    }

    private static BufferedImage image(String path) throws Exception {
        try (InputStream stream = resource(path)) {
            assertNotNull(stream, "Missing image " + path);
            return ImageIO.read(stream);
        }
    }

    private static JsonObject read(String path) {
        try (var stream = resource(path)) {
            assertNotNull(stream, "Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) { throw new AssertionError("Unable to read resource " + path, exception); }
    }

    private static InputStream resource(String path) {
        return AtmosMachineModelResourceTest.class.getResourceAsStream(ROOT + path);
    }

    private record ExpectedVisual(String sprite, String state) { }
}
