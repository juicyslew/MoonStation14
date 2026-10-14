package com.juicyslew.moonstation14.ms14.radiation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RadiationCollectorAtlasAdapterTest {
    private static final String SOURCE = "/assets/moonstation14/textures/block/power_collection/generation_singularity_collector/";
    private static final String DERIVED = "/assets/moonstation14/textures/block/reactor_bindings/collector/";
    private static final int TANK_X = 28, TANK_Y = 45, TANK_W = 14, TANK_H = 8;
    private static final int LED_X = 43, LED_Y = 46, LED_W = 2, LED_H = 7;

    private static BufferedImage image(String root, String file) throws Exception {
        try (InputStream in = RadiationCollectorAtlasAdapterTest.class.getResourceAsStream(root + file)) {
            assertNotNull(in, root + file);
            return ImageIO.read(in);
        }
    }

    @Test void eightAtlasCombinationsCopyOnlyDisjointAcceptedTiles() throws Exception {
        BufferedImage baseline = image(SOURCE, "ca_off.png");
        BufferedImage tankSource = image(SOURCE, "ca_tank.png");
        for (int tank = 0; tank <= 1; tank++) for (int band = 0; band <= 3; band++) {
            String stem = "tank" + tank + "_pressure" + band;
            BufferedImage actual = image(DERIVED, stem + ".png");
            BufferedImage pressure = image(SOURCE, "ca_o" + band + ".png");
            int frames = band == 0 ? 2 : 1;
            assertEquals(128, actual.getWidth());
            assertEquals(128 * frames, actual.getHeight());
            for (int frame = 0; frame < frames; frame++) for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int expected = baseline.getRGB(x, y);
                if (tank == 1 && inRect(x, y, TANK_X, TANK_Y, TANK_W, TANK_H)) expected = tankSource.getRGB(x, y);
                if (inRect(x, y, LED_X, LED_Y, LED_W, LED_H)) expected = pressure.getRGB(x, y + (band == 0 ? frame * 128 : 0));
                int observed = actual.getRGB(x, y + frame * 128);
                if (expected != observed) fail(stem + " pixel " + x + "," + y + " frame " + frame + " source-pixel mismatch");
            }
        }
        assertFalse(overlap(TANK_X, TANK_Y, TANK_W, TANK_H, LED_X, LED_Y, LED_W, LED_H));
    }

    @Test void pressureZeroCarriesAcceptedBlinkMetadata() throws Exception {
        try (var source = RadiationCollectorAtlasAdapterTest.class.getResourceAsStream(SOURCE + "ca_o0.png.mcmeta");
             var derived = RadiationCollectorAtlasAdapterTest.class.getResourceAsStream(DERIVED + "tank0_pressure0.png.mcmeta")) {
            assertNotNull(source); assertNotNull(derived);
            assertEquals(new String(source.readAllBytes(), StandardCharsets.UTF_8),
                    new String(derived.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test void adapterPreservesAcceptedElementGeometryAndUv() throws Exception {
        JsonObject shellSource = json("/assets/moonstation14/models/block/power_collection/generation_singularity_collector/static.json");
        JsonObject rawPanelSource = json("/assets/moonstation14/models/block/power_collection/generation_singularity_collector/default.json");
        JsonObject shell = json("/assets/moonstation14/models/block/reactor_bindings/collector/shell_tank0_pressure0.json");
        JsonObject panel = json("/assets/moonstation14/models/block/reactor_bindings/collector/panel_inactive.json");
        assertFilteredElements(shellSource, shell.getAsJsonArray("elements"), "collector_base", "lower_controls");
        assertFilteredElements(rawPanelSource, panel.getAsJsonArray("elements"), "collector_lid", "collector_glass", "panel_rear_hinge");
        assertEquals("moonstation14:block/power_collection/generation_singularity_collector/ca_off",
                panel.getAsJsonObject("textures").get("atlas").getAsString());
        JsonObject activePanel = json("/assets/moonstation14/models/block/reactor_bindings/collector/panel_active.json");
        assertFilteredElements(rawPanelSource, activePanel.getAsJsonArray("elements"), "collector_lid", "collector_glass", "panel_rear_hinge");
        assertEquals("moonstation14:block/power_collection/generation_singularity_collector/ca_on",
                activePanel.getAsJsonObject("textures").get("atlas").getAsString());
        JsonObject led = json("/assets/moonstation14/models/block/reactor_bindings/collector/pressure_overlay_p0.json");
        JsonObject lamp = led.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertFalse(lamp.get("shade").getAsBoolean());
        assertArrayEquals(new double[]{9.26, 1.0, 2.83}, numbers(lamp.getAsJsonArray("from")), 1e-6);
        assertArrayEquals(new double[]{10.268, 4.5, 2.84}, numbers(lamp.getAsJsonArray("to")), 1e-6);
        assertArrayEquals(new double[]{5.375, 5.75, 5.625, 6.625}, numbers(lamp.getAsJsonObject("faces")
                .getAsJsonObject("north").getAsJsonArray("uv")));
    }

    @Test void panelAtlasesShareRawDefaultUvWithOnlyAcceptedActiveGlassPatchDifferent() throws Exception {
        JsonObject raw = json("/assets/moonstation14/models/block/power_collection/generation_singularity_collector/default.json");
        BufferedImage defaults = image(SOURCE, "default.png");
        BufferedImage inactive = image(SOURCE, "ca_off.png");
        BufferedImage active = image(SOURCE, "ca_on.png");
        int activePatchDifferences = 0;
        for (String name : new String[]{"collector_lid", "collector_glass", "panel_rear_hinge"}) {
            JsonObject element = findElement(raw, name);
            for (var faceEntry : element.getAsJsonObject("faces").entrySet()) {
                JsonArray uv = faceEntry.getValue().getAsJsonObject().getAsJsonArray("uv");
                int minX = (int)Math.round(uv.get(0).getAsDouble() * 8);
                int minY = (int)Math.round(uv.get(1).getAsDouble() * 8);
                int maxX = (int)Math.round(uv.get(2).getAsDouble() * 8);
                int maxY = (int)Math.round(uv.get(3).getAsDouble() * 8);
                boolean activeScreen = name.equals("collector_glass") && faceEntry.getKey().equals("up");
                for (int y = minY; y < maxY; y++) for (int x = minX; x < maxX; x++) {
                    assertEquals(defaults.getRGB(x, y), inactive.getRGB(x, y), name + " inactive UV pixel " + x + "," + y);
                    if (active.getRGB(x, y) != defaults.getRGB(x, y)) {
                        assertTrue(activeScreen, "active palette changes only accepted glass-up pixel region");
                        activePatchDifferences++;
                    }
                }
            }
        }
        assertTrue(activePatchDifferences > 0, "active accepted ca_on glass patch is retained");
    }

    @Test void provenanceRecordsRawDefaultPanelSourceAndAdapterHashes() throws Exception {
        JsonObject manifest = json("/assets/moonstation14/models/block/reactor_bindings/collector/provenance.json");
        String sourcePath = manifest.get("panelGeometrySource").getAsString();
        assertTrue(sourcePath.endsWith("generation_singularity_collector/default.json")
                || sourcePath.endsWith("generation_singularity_collector\\default.json"));
        assertEquals(sha256(bytes("/assets/moonstation14/models/block/power_collection/generation_singularity_collector/default.json")),
                manifest.get("panelGeometrySourceSha256").getAsString());
        JsonObject hashes = manifest.getAsJsonObject("derivedModelSha256");
        for (String name : new String[]{"panel_active.json", "panel_inactive.json"})
            assertEquals(sha256(bytes("/assets/moonstation14/models/block/reactor_bindings/collector/" + name)),
                    hashes.get(name).getAsString(), name + " provenance hash");
    }

    private static JsonObject json(String path) throws Exception {
        try (var reader = new InputStreamReader(RadiationCollectorAtlasAdapterTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void assertFilteredElements(JsonObject source, JsonArray actual, String... names) {
        assertEquals(names.length, actual.size());
        for (int i = 0; i < names.length; i++) {
            JsonObject expected = null;
            for (var element : source.getAsJsonArray("elements"))
                if (element.getAsJsonObject().get("name").getAsString().equals(names[i])) expected = element.getAsJsonObject();
            assertNotNull(expected, "accepted element " + names[i]);
            assertEquals(expected, actual.get(i).getAsJsonObject(), names[i] + " geometry/UV remains verbatim");
        }
    }

    private static JsonObject findElement(JsonObject source, String name) {
        for (var element : source.getAsJsonArray("elements"))
            if (element.getAsJsonObject().get("name").getAsString().equals(name)) return element.getAsJsonObject();
        fail("missing accepted element " + name);
        return null;
    }

    private static byte[] bytes(String path) throws Exception {
        try (var in = RadiationCollectorAtlasAdapterTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return in.readAllBytes();
        }
    }
    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static double[] numbers(JsonArray a) {
        double[] values = new double[a.size()];
        for (int i = 0; i < values.length; i++) values[i] = a.get(i).getAsDouble();
        return values;
    }
    private static boolean inRect(int x, int y, int rx, int ry, int width, int height) {
        return x >= rx && x < rx + width && y >= ry && y < ry + height;
    }
    private static boolean overlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
}
