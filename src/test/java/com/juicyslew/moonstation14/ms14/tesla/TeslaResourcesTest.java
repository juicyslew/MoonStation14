package com.juicyslew.moonstation14.ms14.tesla;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class TeslaResourcesTest {
    private static final String ROOT = "/assets/moonstation14/";

    private JsonObject json(String path) throws Exception {
        try (var in = getClass().getResourceAsStream(ROOT + path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private byte[] bytes(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(ROOT + path)) {
            assertNotNull(in, path);
            return in.readAllBytes();
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void sourceRsiSheetsRetainTwelveFrameLayoutsAndTimings() throws Exception {
        checkAnimated("textures/ss14/structures/power/generation/tesla/energy_ball.rsi", "energy_ball", 96, TeslaVisualResources.MAIN_SHEET.width(), TeslaVisualResources.MAIN_SHEET.height(),
                new double[]{.120000005, .04, .120000005, .08, .04, .120000005, .04, .120000005, .08, .04, .120000005, .04});
        checkAnimated("textures/ss14/structures/power/generation/tesla/energy_miniball.rsi", "tesla_projectile", 32, TeslaVisualResources.MINI_SHEET.width(), TeslaVisualResources.MINI_SHEET.height(),
                new double[]{.05, .08, .08, .08, .08, .08, .08, .08, .08, .08, .08, .08});
    }

    private void checkAnimated(String folder, String state, int cell, int width, int height, double[] timings) throws Exception {
        JsonObject meta = json(folder + "/meta.json");
        JsonObject selected = null;
        for (var s : meta.getAsJsonArray("states")) if (s.getAsJsonObject().get("name").getAsString().equals(state)) selected = s.getAsJsonObject();
        assertNotNull(selected, state);
        JsonArray delays = selected.getAsJsonArray("delays").get(0).getAsJsonArray();
        assertEquals(12, delays.size());
        assertEquals(12, timings.length);
        for (int i = 0; i < 12; i++) {
            assertTrue(delays.get(i).getAsDouble() > 0);
            assertEquals(timings[i], delays.get(i).getAsDouble(), 1e-7);
        }
        assertEquals(cell, meta.getAsJsonObject("size").get("x").getAsInt());
        assertEquals(cell, meta.getAsJsonObject("size").get("y").getAsInt());
        try (var in = getClass().getResourceAsStream(ROOT + folder + "/" + state + ".png")) {
            assertNotNull(in);
            var image = ImageIO.read(in);
            assertNotNull(image);
            assertEquals(width, image.getWidth());
            assertEquals(height, image.getHeight());
            assertEquals(12, (width / cell) * (height / cell));
        }
    }

    @Test void lightningTextureAndAttributionArePresent() throws Exception {
        JsonObject meta = json("textures/ss14/effects/lightning.rsi/meta.json");
        assertTrue(meta.get("license").getAsString().length() > 0);
        assertTrue(meta.get("copyright").getAsString().length() > 0);
        assertTrue(meta.getAsJsonArray("states").asList().stream().anyMatch(s -> s.getAsJsonObject().get("name").getAsString().equals("lightning_1")));
        var image = ImageIO.read(getClass().getResourceAsStream(ROOT + "textures/ss14/effects/lightning.rsi/lightning_1.png"));
        assertNotNull(image);
        assertEquals(32, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue((image.getRGB(0, 0) >>> 24) >= 0);
    }

    @Test void empPulseKeepsUpstreamAttributionAndEightFrameLayout() throws Exception {
        JsonObject meta=json("textures/ss14/effects/emp.rsi/meta.json");
        assertEquals("CC-BY-SA-3.0",meta.get("license").getAsString());
        assertTrue(meta.get("copyright").getAsString().contains("e52683d3872347af447bb0ff74c420d4a4e91ea8"));
        var state=meta.getAsJsonArray("states").asList().stream().map(e->e.getAsJsonObject())
                .filter(e->e.get("name").getAsString().equals("emp_pulse")).findFirst().orElseThrow();
        assertEquals(8,state.getAsJsonArray("delays").get(0).getAsJsonArray().size());
        assertTrue(state.getAsJsonArray("delays").get(0).getAsJsonArray().asList().stream().allMatch(d->d.getAsDouble()==.1));
        var image=ImageIO.read(getClass().getResourceAsStream(ROOT+"textures/ss14/effects/emp.rsi/emp_pulse.png"));
        assertNotNull(image); assertEquals(96,image.getWidth()); assertEquals(96,image.getHeight());
        assertEquals(8,TeslaVisualResources.EMP_PULSE_SHEET.frameCount(0));
        assertTrue(TeslaVisualResources.EMP_PULSE.getPath().equals("textures/ss14/effects/emp.rsi/emp_pulse.png"));
    }

    @Test void allTeslaRendererTextureLocationsAreValidAndPresent() throws Exception {
        for (ResourceLocation id : new ResourceLocation[]{TeslaVisualResources.MAIN, TeslaVisualResources.MINI, TeslaVisualResources.LIGHTNING, TeslaVisualResources.EMP_PULSE}) {
            try (var in = getClass().getResourceAsStream(ROOT + id.getPath())) {
                assertNotNull(in, id.toString());
            }
        }
        assertEquals(288, TeslaVisualResources.MAIN_SHEET.width());
        assertEquals(384, TeslaVisualResources.MAIN_SHEET.height());
        assertEquals(96, TeslaVisualResources.MINI_SHEET.width());
        assertEquals(128, TeslaVisualResources.MINI_SHEET.height());
        for (var sheet : new com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet[]{TeslaVisualResources.MAIN_SHEET, TeslaVisualResources.MINI_SHEET}) {
            assertEquals(12, sheet.frameCount(0));
            assertEquals(2 * sheet.frameWidth(), sheet.u(0, 11));
            assertEquals(3 * sheet.frameHeight(), sheet.v(0, 11));
            assertEquals(0, sheet.frameAt(0, sheet.cycleMillis(0)));
        }
    }

    @Test void teslaRuntimeRsiPathsHaveExactLowercaseFilesystemCase() throws Exception {
        Path project = Path.of("").toAbsolutePath();
        while (project != null && !Files.isDirectory(project.resolve("src/main/resources/assets/moonstation14"))) project = project.getParent();
        assertNotNull(project, "Could not locate project resource source directory");
        Path root = project.resolve("src/main/resources/assets/moonstation14");
        for (String resource : new String[]{
                "textures/ss14/effects/emp.rsi/meta.json",
                "textures/ss14/effects/emp.rsi/emp_pulse.png",
                "textures/ss14/effects/lightning.rsi/meta.json",
                "textures/ss14/effects/lightning.rsi/lightning_1.png",
                "textures/ss14/structures/power/generation/tesla/energy_ball.rsi/meta.json",
                "textures/ss14/structures/power/generation/tesla/energy_ball.rsi/energy_ball.png",
                "textures/ss14/structures/power/generation/tesla/energy_miniball.rsi/meta.json",
                "textures/ss14/structures/power/generation/tesla/energy_miniball.rsi/tesla_projectile.png"}) {
            Path current = root;
            for (String component : resource.split("/")) {
                boolean exactNameFound = false;
                try (DirectoryStream<Path> children = Files.newDirectoryStream(current)) {
                    for (Path child : children) if (child.getFileName().toString().equals(component)) exactNameFound = true;
                }
                assertTrue(exactNameFound, "Exact-case resource component missing: " + current + "/" + component);
                current = current.resolve(component);
            }
            assertTrue(Files.isRegularFile(current), resource);
        }
    }

    @Test void soundEventsResolveAudioAndMatchingSourceHashLicenseSidecars() throws Exception {
        JsonObject sounds = json("sounds.json");
        checkSound(sounds, "tesla/ambient", "tesla/tesla", "CC-BY-NC-4.0");
        checkSound(sounds, "tesla/spawn", "tesla/tesla_collapse", "CC-BY-NC-SA-3.0");
        checkSound(sounds, "tesla/consume", "tesla/tesla_consume", "CC0-1.0");
        checkSound(sounds, "tesla/lightning_shock", "lightning/lightningshock", "CC-BY-SA-3.0");
        assertEquals(1, sounds.getAsJsonObject("tesla/spawn").getAsJsonArray("sounds").size());
    }

    private void checkSound(JsonObject sounds, String event, String sample, String license) throws Exception {
        assertTrue(sounds.has(event));
        var sound = sounds.getAsJsonObject(event).getAsJsonArray("sounds").get(0);
        String value = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
        assertEquals("moonstation14:" + sample, value);
        String path = "sounds/" + sample + ".ogg";
        byte[] audio = bytes(path);
        JsonObject provenance = json(path + ".provenance.json");
        assertEquals(license, provenance.get("license").getAsString());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", provenance.get("upstream_revision").getAsString());
        assertEquals(provenance.get("source_file_sha256").getAsString(), sha256(audio));
        assertFalse(provenance.get("copyright").getAsString().isBlank());
    }

    @Test void importedModelManifestHashesAllSixteenFiles() throws Exception {
        JsonObject manifest = json("power-reactor-provenance/tesla/model-import-manifest.json");
        int total = 0;
        for (var family : manifest.getAsJsonArray("families")) {
            for (var resource : family.getAsJsonObject().getAsJsonArray("imported_resources")) {
                JsonObject entry = resource.getAsJsonObject();
                String runtimePath = entry.get("runtime_resource").getAsString().replace("src/main/resources/assets/moonstation14/", "");
                byte[] actual = bytes(runtimePath);
                assertEquals(entry.get("runtime_sha256").getAsString(), sha256(actual));
                assertEquals(entry.get("accepted_sha256").getAsString(), entry.get("runtime_sha256").getAsString());
                assertTrue(entry.get("byte_identical_to_accepted_export").getAsBoolean());
                total++;
            }
        }
        assertEquals(16, total);
    }
}
