package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** CPU-only closure checks for all source-derived static/dynamic recharger resources. */
class CapacitorRechargerResourceTest {
    private static final String ASSET = "/assets/moonstation14/";
    private static final String SPRITE = "moonstation14:ss14/power/recharger.rsi/";
    private static final Map<String, String> VISUALS = Map.of(
            "off_empty", "light-off", "off_full", "light-off", "empty", "light-empty",
            "charging", "light-charging", "charged", "light-charged");

    @Test
    void fiveVisualVariantsCloseTheirNativeBlockModelAndItemModelAssets() throws Exception {
        JsonObject states = read("blockstates/weapon_capacitor_recharger.json");
        assertEquals(Set.of("visual=off_empty", "visual=off_full", "visual=empty", "visual=charging", "visual=charged"),
                states.getAsJsonObject("variants").keySet());
        Map<String, String> expectedBase = Map.of("off_empty", "empty", "off_full", "full", "empty", "empty",
                "charging", "full", "charged", "full");
        for (var visual : VISUALS.entrySet()) {
            String modelId = states.getAsJsonObject("variants").getAsJsonObject("visual=" + visual.getKey())
                    .get("model").getAsString();
            String modelSuffix = visual.getKey().equals("off_empty") ? "off" : visual.getKey();
            assertEquals("moonstation14:block/weapon_capacitor_recharger_" + modelSuffix, modelId);
            JsonObject model = read("models/block/weapon_capacitor_recharger_" + modelSuffix + ".json");
            assertEquals(SPRITE + expectedBase.get(visual.getKey()),
                    model.getAsJsonObject("textures").get("base").getAsString(), "base art " + visual.getKey());
            assertEquals(SPRITE + visual.getValue(), model.getAsJsonObject("textures").get("status").getAsString(),
                    "light overlay " + visual.getKey());
            var elements = model.getAsJsonArray("elements");
            assertEquals(2, elements.size(), "base body plus transparent front-light overlay");
            var base = elements.get(0).getAsJsonObject();
            assertBounds(base, new double[]{2, 0, 2}, new double[]{14, 8, 14});
            var overlay = elements.get(1).getAsJsonObject();
            assertBounds(overlay, new double[]{2, 0, 1.99}, new double[]{14, 8, 1.99});
            assertEquals(Set.of("north"), overlay.getAsJsonObject("faces").keySet(), "source overlay is front-only");
             try (var input = resource("models/block/weapon_capacitor_recharger_" + modelSuffix + ".json");
                 var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                var parsed = net.minecraft.client.renderer.block.model.BlockModel.fromStream(reader);
                assertEquals(2, parsed.getElements().size());
            }
        }
        assertEquals("moonstation14:block/weapon_capacitor_recharger_off",
                read("models/item/weapon_capacitor_recharger.json").get("parent").getAsString());
        assertNotNull(resource("models/block/weapon_capacitor_recharger_off.json"));
    }

    @Test
    void liveSlotAndPowerProjectionSelectsOccupiedBaseForPoweredAndUnpoweredStates() {
        assertEquals(com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.Visual.OFF_EMPTY,
                com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.projectVisual(false, false, 0f, 360f));
        assertEquals(com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.Visual.OFF_FULL,
                com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.projectVisual(false, true, 120f, 360f));
        assertEquals(com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.Visual.EMPTY,
                com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.projectVisual(true, false, 0f, 360f));
        assertEquals(com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.Visual.CHARGING,
                com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.projectVisual(true, true, 120f, 360f));
        assertEquals(com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.Visual.CHARGED,
                com.juicyslew.moonstation14.ms14.combat.WeaponCapacitorRechargerBlock.projectVisual(true, true, 360f, 360f));
    }

    @Test
    void nativeAtlasLoadsEveryImportedSourceStateAndExactPngFrames() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("src/main/resources/assets"))) root = root.getParent();
        assertNotNull(root, "production resources root");
        Path resourcesRoot = root.resolve("src/main/resources");
        List<PackResources> packs = List.of(new PathPackResources(
                new PackLocationInfo("production", Component.literal("production"), PackSource.DEFAULT, Optional.empty()), resourcesRoot));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs)) {
            var suppliers = SpriteSourceList.load(manager, ResourceLocation.withDefaultNamespace("blocks")).list(manager);
            var ids = new java.util.HashSet<ResourceLocation>();
            SpriteResourceLoader capture = (id, resource, constructor) -> {
                ids.add(id);
                if (id.getNamespace().equals("moonstation14") && id.getPath().startsWith("ss14/power/recharger.rsi/")) {
                    try (var input = resource.open()) { assertNotNull(ImageIO.read(input), "decode atlas PNG " + id); }
                    catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                }
                return null;
            };
            for (int i = 1; i < suppliers.size(); i++) suppliers.get(i).apply(capture);
            for (String state : List.of("empty", "full", "light-off", "light-empty", "light-charging", "light-charged")) {
                ResourceLocation id = ResourceLocation.parse(SPRITE + state);
                assertTrue(ids.contains(id), "block atlas includes exact RSI sprite id " + id);
                var png = manager.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png"))
                        .orElseThrow(() -> new AssertionError("missing atlas PNG " + id));
                try (var input = png.open()) {
                    var image = ImageIO.read(input);
                    int[] dimensions = switch (state) {
                        case "empty", "full", "light-off" -> new int[]{32, 32};
                        case "light-empty", "light-charged" -> new int[]{64, 32};
                        case "light-charging" -> new int[]{96, 96};
                        default -> throw new AssertionError(state);
                    };
                    assertEquals(dimensions[0], image.getWidth(), state + " source width");
                    assertEquals(dimensions[1], image.getHeight(), state + " source height / frame sheet");
                }
            }
        }
    }

    @Test
    void sourceRsiMetadataAttributionAndMinecraftAnimationMetadataStayInSync() {
        JsonObject meta = read("textures/ss14/power/recharger.rsi/meta.json");
        assertEquals(32, meta.getAsJsonObject("size").get("x").getAsInt());
        assertEquals(32, meta.getAsJsonObject("size").get("y").getAsInt());
        assertEquals("CC-BY-SA-3.0", meta.get("license").getAsString());
        assertTrue(meta.get("copyright").getAsString().contains("CEV-Eris"));
        assertTrue(meta.get("copyright").getAsString().contains("spanky-spanky"));
        Set<String> states = new java.util.HashSet<>();
        meta.getAsJsonArray("states").forEach(element -> states.add(element.getAsJsonObject().get("name").getAsString()));
        assertEquals(Set.of("empty", "full", "light-off", "light-empty", "light-charging", "light-charged"), states);
        assertAnimation("light-empty", new int[]{20, 4});
        assertAnimation("light-charged", new int[]{4, 2});
        assertAnimation("light-charging", new int[]{2, 2, 2, 2, 2, 2, 2, 2});
        for (String state : List.of("light-empty", "light-charging", "light-charged")) {
            var authored = meta.getAsJsonArray("states").asList().stream()
                    .map(com.google.gson.JsonElement::getAsJsonObject)
                    .filter(value -> state.equals(value.get("name").getAsString())).findFirst().orElseThrow();
            var sourceFrames = authored.getAsJsonArray("delays").get(0).getAsJsonArray();
            var runtimeFrames = read("textures/ss14/power/recharger.rsi/" + state + ".png.mcmeta")
                    .getAsJsonObject("animation").getAsJsonArray("frames");
            assertEquals(sourceFrames.size(), runtimeFrames.size(), state + " source/runtime frame count");
            for (int i = 0; i < sourceFrames.size(); i++)
                assertEquals(Math.round(sourceFrames.get(i).getAsFloat() * 20),
                        runtimeFrames.get(i).getAsJsonObject().get("time").getAsInt(), state + " source/runtime delay " + i);
        }
        assertFalse(Files.exists(Path.of("src/main/resources/assets/moonstation14/textures/ss14/power/recharger.rsi/inhand-left.png")));
        assertTrue(Files.exists(projectRoot().resolve("docs/guns-sprint/RECHARGER-ASSET-PROVENANCE.md")));
        Map<String, String> hashes = Map.of(
                "empty", "869316efc6948345733cc93fdd80b5ab8fe36130a9af3cd50d4242dce8bbd33f",
                "full", "869316efc6948345733cc93fdd80b5ab8fe36130a9af3cd50d4242dce8bbd33f",
                "light-off", "ba9b58054872944cdc32a387fc18ab5e00eca9957fd38de721d14960afd8d9d5",
                "light-empty", "24a8076c22c139e402a64c5603922ad5a2c65512b0bb039152c0015d56fd72e4",
                "light-charging", "ebab39717eb1f15fdbfc2898368665a2efb4f8b01f765852eec881d2485daaab",
                "light-charged", "b84250e802a2d90eb9920e02cca11dddbf1b725ac31e2c9a67df0e24ba483fa3");
        hashes.forEach((name, hash) -> assertEquals(hash, sha256(readBytes("textures/ss14/power/recharger.rsi/" + name + ".png")), name));
    }

    @Test
    void bundledTypedChargerCompositionResolvesItsRuntimeDefaults() {
        var type = com.juicyslew.moonstation14.ms14.combat.ModChargerProfiles.TYPE;
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(type);
        var source = new java.util.LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : List.of("base_recharger", "base_item_recharger", "weapon_capacitor_recharger"))
            source.put(ResourceLocation.fromNamespaceAndPath("moonstation14", name), readData("charger_profile/" + name + ".json"));
        manager.stage(Map.of(type, source), resolved -> {
            var raw = resolved.get(type.typeId()).get(ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger"));
            var profile = com.juicyslew.moonstation14.ms14.combat.ChargerProfile.CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow();
            assertEquals(1f, profile.idlePower());
            assertEquals(20f, profile.chargeRate());
            assertEquals("charger_slot", profile.slotId());
            assertTrue(profile.ejectOnInteract());
            assertEquals(Set.of(com.juicyslew.moonstation14.ms14.combat.ChargerCapability.BATTERY_AMMO_PROVIDER,
                            com.juicyslew.moonstation14.ms14.combat.ChargerCapability.POWER_CELL),
                    profile.acceptedCapabilities());
        });
    }

    private static void assertAnimation(String state, int[] expectedFrameTicks) {
        JsonObject animation = read("textures/ss14/power/recharger.rsi/" + state + ".png.mcmeta").getAsJsonObject("animation");
        assertEquals(32, animation.get("width").getAsInt());
        assertEquals(32, animation.get("height").getAsInt());
        var frames = animation.getAsJsonArray("frames");
        assertEquals(expectedFrameTicks.length, frames.size(), state);
        for (int i = 0; i < expectedFrameTicks.length; i++)
            assertEquals(expectedFrameTicks[i], frames.get(i).getAsJsonObject().get("time").getAsInt(), state + " frame " + i);
    }

    private static void assertBounds(JsonObject element, double[] from, double[] to) {
        for (int i = 0; i < 3; i++) {
            assertEquals(from[i], element.getAsJsonArray("from").get(i).getAsDouble());
            assertEquals(to[i], element.getAsJsonArray("to").get(i).getAsDouble());
        }
    }

    private static JsonObject read(String path) {
        try (var stream = resource(path)) {
            if (stream == null) throw new AssertionError("Missing charger resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) { throw new AssertionError("Unable to read charger resource " + path, exception); }
    }

    private static JsonObject readData(String path) {
        try (var stream = CapacitorRechargerResourceTest.class.getResourceAsStream("/data/moonstation14/moonstation14/" + path)) {
            if (stream == null) throw new AssertionError("Missing charger prototype " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) { throw new AssertionError("Unable to read charger prototype " + path, exception); }
    }

    private static byte[] readBytes(String path) {
        try (var stream = resource(path)) {
            if (stream == null) throw new AssertionError("Missing charger resource " + path);
            return stream.readAllBytes();
        } catch (Exception exception) { throw new AssertionError("Unable to read charger resource " + path, exception); }
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("src/main/resources/assets"))) current = current.getParent();
        assertNotNull(current, "project root containing production resources");
        return current;
    }

    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static java.io.InputStream resource(String path) {
        return CapacitorRechargerResourceTest.class.getResourceAsStream(ASSET + path);
    }
}
