package com.juicyslew.moonstation14.ms14.ingestion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IngestionProfileTest {
    @Test void codecRequiresComposedEdibleAndAppliesEdibleDefaults() {
        var defaults = IngestionProfile.Edible.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        assertEquals(new IngestionProfile.Edible(500, 20, true), defaults);
        assertEquals(id("ss14/items/drink"), defaults.completionSound());
        assertEquals(-2f, defaults.volumeDb());
        assertEquals(.25f, defaults.pitchVariation());
        assertTrue(IngestionProfile.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).error().isPresent());
        JsonObject invalid = new JsonObject();
        JsonObject edible = new JsonObject(); edible.addProperty("dose_cents", 0); invalid.add("edible", edible);
        assertTrue(IngestionProfile.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
    }

    @Test void bundledInheritanceResolvesDistinctDrinkAndBottleParents() {
        PrototypeManager manager = new PrototypeManager(); manager.register(ModIngestionProfiles.TYPE);
        Map<ResourceLocation, JsonObject> resources = new LinkedHashMap<>();
        for (String name : new String[]{"edible_defaults", "drink_base", "jug", "bottle", "puddle", "pill"})
            resources.put(id(name), resource(name));
        manager.reload(ModIngestionProfiles.TYPE, resources);
        var catalog = manager.snapshot(ModIngestionProfiles.TYPE);
        assertEquals(new IngestionProfile.Edible(500, 20, false), catalog.get(id("jug")).edible());
        assertEquals(new IngestionProfile.Edible(500, 20, false), catalog.get(id("bottle")).edible());
        assertEquals(id("drink_base").toString(), resources.get(id("jug")).get("parent").getAsString());
        assertEquals(id("edible_defaults").toString(), resources.get(id("bottle")).get("parent").getAsString());
        assertEquals(new IngestionProfile.Edible(100, 60, true), catalog.get(id("puddle")).edible());
        assertEquals(500, catalog.get(id("jug")).doseCents());
        var pill = catalog.get(id("pill"));
        assertEquals(id("ss14/items/pill"), pill.completionSound());
        assertEquals(-1f, pill.completionVolumeDb());
        assertEquals(.2f, pill.completionPitchVariation());
        assertEquals(id("ss14/items/drink"), catalog.get(id("bottle")).completionSound());
    }

    @Test void lookupsFailClosedAndReadinessRequiresBundledEntries() {
        assertFalse(ModIngestionProfiles.serverReady());
        assertFalse(ModIngestionProfiles.clientReady());
        assertNull(ModIngestionProfiles.serverLookup(null));
        assertNull(ModIngestionProfiles.clientLookup(id("unknown")));
    }

    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/ingestion_profile/" + name + ".json";
        try (var stream = IngestionProfileTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
