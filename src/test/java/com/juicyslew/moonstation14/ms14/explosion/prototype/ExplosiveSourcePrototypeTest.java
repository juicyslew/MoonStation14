package com.juicyslew.moonstation14.ms14.explosion.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExplosiveSourcePrototypeTest {
    @Test
    void bundledSourceDecodesAndRoundTripsBetweenIndependentManagers() throws Exception {
        String path = "data/moonstation14/moonstation14/explosive_source/ex_grenade.json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            PrototypeManager server = manager();
            server.reload(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE, Map.of(ModExplosiveSources.EX_GRENADE_ID, json));
            ExplosiveSourceData data = server.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).get(ModExplosiveSources.EX_GRENADE_ID);
            assertEquals(380, data.totalIntensity());
            assertEquals(3, data.intensitySlope());
            assertEquals(10, data.maxIntensity());
            assertEquals(ResourceLocation.parse("moonstation14:default"), data.explosionType());
            assertEquals(data.proposal(), new com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner.Proposal(380, 3, 10));
            PrototypeManager client = manager();
            client.publishEncodedCatalogs(server.encodePublishedCatalogs());
            assertEquals(data, client.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).get(ModExplosiveSources.EX_GRENADE_ID));
            assertThrows(IllegalArgumentException.class, () -> ModExplosiveSources.require(client.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE), id("missing")));
        }
    }

    @Test
    void strictCodecRejectsInvalidFieldsAndMissingTypeCatalogPublishIsAtomic() {
        JsonObject valid = JsonParser.parseString("{\"total_intensity\":380,\"intensity_slope\":3,\"max_intensity\":10,\"explosion_type\":\"moonstation14:default\"}").getAsJsonObject();
        assertTrue(ExplosiveSourceData.CODEC.parse(JsonOps.INSTANCE, valid).result().isPresent());
        JsonObject fractional = JsonParser.parseString("{\"total_intensity\":380.5,\"intensity_slope\":3.25,\"max_intensity\":10.5,\"explosion_type\":\"moonstation14:default\"}").getAsJsonObject();
        ExplosiveSourceData fractionalData = ExplosiveSourceData.CODEC.parse(JsonOps.INSTANCE, fractional).result().orElseThrow();
        JsonObject fractionalRoundTrip = ExplosiveSourceData.CODEC.encodeStart(JsonOps.INSTANCE, fractionalData).result().orElseThrow().getAsJsonObject();
        assertEquals(380.5, fractionalRoundTrip.get("total_intensity").getAsDouble());
        assertEquals(3.25, fractionalRoundTrip.get("intensity_slope").getAsDouble());
        assertEquals(10.5, fractionalRoundTrip.get("max_intensity").getAsDouble());
        for (String invalid : new String[]{"null", "{}", "{\"total_intensity\":380,\"intensity_slope\":3,\"max_intensity\":10,\"explosion_type\":\"moonstation14:default\",\"extra\":1}"})
            assertTrue(ExplosiveSourceData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent());
        for (String invalid : new String[]{"0", "-1", "1e-13", "1e999", "null", "\"3\""}) {
            JsonObject bad = valid.deepCopy(); bad.add("intensity_slope", JsonParser.parseString(invalid));
            assertTrue(ExplosiveSourceData.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent(), invalid);
        }
        PrototypeManager server = manager(), client = manager();
        server.reload(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE, Map.of(ModExplosiveSources.EX_GRENADE_ID, valid));
        var before = client.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE);
        assertThrows(IllegalArgumentException.class, () -> client.publishEncodedCatalogs(Map.of()));
        assertEquals(before, client.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE));
        assertEquals(1, server.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE).size());
    }

    private static PrototypeManager manager() { PrototypeManager manager = new PrototypeManager(); manager.register(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE); return manager; }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
