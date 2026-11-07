package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereSampleFormatter;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GasAnalyzerProfileTest {
    @Test void registeredItemModelUsesTheVerifiedStaticAnalyzerIconAndAttribution() throws Exception {
        var model=resource("/assets/moonstation14/models/item/atmosphere_analyzer.json");
        assertEquals("moonstation14:item/atmosphere_analyzer",model.getAsJsonObject("textures").get("layer0").getAsString());
        try(var icon=getClass().getResourceAsStream("/assets/moonstation14/textures/item/atmosphere_analyzer.png")) {
            assertNotNull(icon);
            byte[] bytes=icon.readAllBytes();
            assertEquals(487,bytes.length);
            assertEquals("bec3fdff9b0def074b2edf003d1018c0bc730854c84248b718f17d0c18b05e0b",sha256(bytes));
            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertNotNull(image);assertEquals(32,image.getWidth());assertEquals(32,image.getHeight());
        }
        try(var notice=getClass().getResourceAsStream("/assets/moonstation14/textures/item/atmosphere_analyzer.png.attribution.txt")) {
            assertNotNull(notice);String text=new String(notice.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(text.contains("CC-BY-SA-3.0"));
            assertTrue(text.contains("ded74aff42136a7953c551a2a94cebc81f62f9fb"));
            assertTrue(text.contains("c9df5ef5d675b0d1d226828bddf6b78c28502d91"));
            assertTrue(text.contains("Inhand sprites by onesch"));
            assertTrue(text.contains("Edited or converted: no"));
            assertTrue(text.contains("Resources/Textures/Objects/Specific/Atmos/gasanalyzer.rsi/icon.png"));
            assertTrue(text.contains("src/main/resources/assets/moonstation14/textures/item/atmosphere_analyzer.png"));
            assertTrue(text.contains("Upstream bytes: 487")&&text.contains("Destination bytes: 487"));
            assertTrue(text.contains("Upstream SHA-256: bec3fdff9b0def074b2edf003d1018c0bc730854c84248b718f17d0c18b05e0b"));
            assertTrue(text.contains("Destination SHA-256: bec3fdff9b0def074b2edf003d1018c0bc730854c84248b718f17d0c18b05e0b"));
        }
    }
    @Test void registeredItemInheritsResolvedDefaultsAndSamplingConsumesThatProfile() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModGasAnalyzers.TYPE);
        var raw = new LinkedHashMap<net.minecraft.resources.ResourceLocation, JsonObject>();
        for (String name : java.util.List.of("gas_analyzer_base", "atmosphere_analyzer")) {
            String resource = "/data/moonstation14/moonstation14/gas_analyzer/" + name + ".json";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                raw.put(ModGasAnalyzers.id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        manager.reload(ModGasAnalyzers.TYPE, raw);
        var profile = ModGasAnalyzers.resolve(manager.snapshot(ModGasAnalyzers.TYPE));
        assertEquals(new GasAnalyzerProfile(6, 1, 20, .01), profile);

        var reading = new AtmosphereReading(new GasMixture(Map.of(GasType.OXYGEN, .02,
                GasType.NITROGEN, .03, GasType.PLASMA, .01), 293.15), AtmosphereReading.Status.FINITE);
        GasAnalyzerSample sample = GasAnalyzerSample.from(reading, profile);
        assertEquals(java.util.List.of(GasType.NITROGEN, GasType.OXYGEN), sample.gases().stream().map(GasAnalyzerSample.GasAmount::gas).toList());
        assertEquals(50, sample.gases().get(0).percentage(), 1e-4);
        assertEquals(1000, sample.volumeLitres(), 1e-4);
        assertTrue(AtmosphereSampleFormatter.format(sample).contains("V: 1000.00 L"));
        var empty = GasAnalyzerSample.from(new AtmosphereReading(GasMixture.vacuum(), AtmosphereReading.Status.FINITE), profile);
        assertTrue(empty.gases().isEmpty());
        assertTrue(AtmosphereSampleFormatter.format(empty).contains("gases: none"));

        JsonObject override = new JsonObject();
        override.addProperty("id", ModGasAnalyzers.HANDHELD_ID.toString());
        override.addProperty("parent", ModGasAnalyzers.BASE_ID.toString());
        override.addProperty("maximumRange", 4);
        override.addProperty("minimumGasMoles", .02);
        raw.put(ModGasAnalyzers.HANDHELD_ID, override);
        manager.reload(ModGasAnalyzers.TYPE, raw);
        assertEquals(new GasAnalyzerProfile(4, 1, 20, .02), ModGasAnalyzers.resolve(manager.snapshot(ModGasAnalyzers.TYPE)));
    }

    @Test void validatesSamplingDefaultsAndRejectsInvalidRanges() {
        assertEquals(0,new GasAnalyzerProfile(6,0,20,.01).defaultSampleDistance());
        assertThrows(IllegalArgumentException.class, () -> new GasAnalyzerProfile(4, 5, 20, .01));
        assertThrows(IllegalArgumentException.class, () -> new GasAnalyzerProfile(6, 1, 0, .01));
        assertThrows(IllegalArgumentException.class, () -> new GasAnalyzerProfile(6, 1, 20, Double.NaN));
    }

    @Test void rejectsNonFinitePressureInsteadOfPublishingAnInvalidGraphValue() {
        var tinyCell = new GasMixture(Map.of(GasType.OXYGEN, 1d), 293.15, 1e-45);
        assertFalse(Double.isFinite(tinyCell.pressureKpa()));
        assertThrows(IllegalArgumentException.class, () -> GasAnalyzerSample.from(
                new AtmosphereReading(tinyCell, AtmosphereReading.Status.FINITE),
                new GasAnalyzerProfile(6, 1, 20, .01)));
        assertThrows(IllegalArgumentException.class, () -> new GasAnalyzerSample(1, Double.POSITIVE_INFINITY, 293, java.util.List.of()));
    }

    @Test void namedNodeProjectionUsesEachAuthoritativeVolumeAndExistingSamplePolicy() {
        var profile = new GasAnalyzerProfile(6, 1, 20, .01);
        var mix = new GasMixture(Map.of(GasType.OXYGEN, 2d, GasType.NITROGEN, 1d,
                GasType.PLASMA, .01d, GasType.TRITIUM, .02d), 293.15);
        var pipe = GasAnalyzerSample.fromMixture(mix, .2, profile);
        var mixerPort = GasAnalyzerSample.fromMixture(mix, .1, profile);
        var manifoldPort = GasAnalyzerSample.fromMixture(mix, .05, profile);
        assertEquals(200, pipe.volumeLitres(), 1e-5);
        assertEquals(100, mixerPort.volumeLitres(), 1e-5);
        assertEquals(50, manifoldPort.volumeLitres(), 1e-5);
        assertEquals(pipe.pressureKpa() * 2, mixerPort.pressureKpa(), 1e-4);
        assertEquals(pipe.pressureKpa() * 4, manifoldPort.pressureKpa(), 1e-4);
        assertEquals(java.util.List.of(GasType.OXYGEN, GasType.NITROGEN, GasType.TRITIUM),
                pipe.gases().stream().map(GasAnalyzerSample.GasAmount::gas).toList());
        // Percentages retain the complete mixture total: filtered sub-threshold plasma still
        // contributes .01 mol to the denominator even though it has no displayed gas row.
        double fullMixtureMoles=2+1+.01+.02;
        assertEquals(100d*2/fullMixtureMoles,pipe.gases().get(0).percentage(),1e-4);
        assertNotEquals(100d*2/3,pipe.gases().get(0).percentage(),1e-4,
                "displayed species must not be renormalized after threshold filtering");
        assertTrue(pipe.gases().stream().noneMatch(g->g.gas()==GasType.PLASMA),"threshold plasma is not displayed");
        assertTrue(GasAnalyzerSample.fromMixture(com.juicyslew.moonstation14.ms14.atmos.core.GasMixture.vacuum(), .2, profile)
                .gases().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> GasAnalyzerSample.fromMixture(mix, 0, profile));
        assertThrows(IllegalArgumentException.class, () -> GasAnalyzerSample.fromMixture(mix, Double.NaN, profile));
    }

    private static JsonObject resource(String path)throws java.io.IOException {
        try(var stream=GasAnalyzerProfileTest.class.getResourceAsStream(path)) {
            assertNotNull(stream,path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static String sha256(byte[] bytes)throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
