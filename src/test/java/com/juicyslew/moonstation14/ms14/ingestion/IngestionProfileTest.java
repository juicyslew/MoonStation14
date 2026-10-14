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
        assertEquals(IngestionProfile.Kind.DRINK, defaults.kind());
        var programmaticFood = new IngestionProfile.Edible(500, 20, true, null, IngestionProfile.Kind.FOOD);
        assertEquals(id("ss14/items/eat"), programmaticFood.completionSound());
        assertEquals(-2f, programmaticFood.volumeDb());
        assertEquals(.25f, programmaticFood.pitchVariation());
        assertEquals(id("ss14/items/drink"), new IngestionProfile.Edible(500, 20, true).completionSound());
        assertTrue(IngestionProfile.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).error().isPresent());
        JsonObject invalid = new JsonObject();
        JsonObject edible = new JsonObject(); edible.addProperty("dose_cents", 0); invalid.add("edible", edible);
        assertTrue(IngestionProfile.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
    }

    @Test void bundledInheritanceResolvesDistinctDrinkAndBottleParents() {
        PrototypeManager manager = new PrototypeManager(); manager.register(ModIngestionProfiles.TYPE);
        Map<ResourceLocation, JsonObject> resources = new LinkedHashMap<>();
        for (String name : new String[]{"edible_defaults", "drink_base", "jug", "bottle", "puddle", "pill",
                "food_base", "food_baking_base", "cheese_slice", "pumpkin_pie_slice"})
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
        assertEquals(IngestionProfile.Kind.FOOD, pill.edible().kind());
        assertTrue(pill.consumeSource());
        assertEquals(2000, pill.doseCents());
        assertEquals(12, pill.delayTicks());
        assertEquals(40, pill.forceFeedDelayTicks());
        assertNull(pill.edible().sourceSolution(), "pill source contents are supplied by ChemMaster");
        assertEquals(id("ss14/items/pill"), pill.completionSound());
        assertEquals(-1f, pill.completionVolumeDb());
        assertEquals(.2f, pill.completionPitchVariation());
        assertEquals(id("ss14/items/drink"), catalog.get(id("bottle")).completionSound());
        var cheese = catalog.get(id("cheese_slice"));
        assertEquals(IngestionProfile.Kind.FOOD, cheese.edible().kind());
        assertEquals(id("ss14/items/eat"), cheese.completionSound());
        assertEquals(IngestionProfile.Kind.DRINK, catalog.get(id("puddle")).edible().kind());
        assertEquals(new IngestionProfile.Edible.SourceSolution(600, Map.of(id("nutriment"), 375L)), cheese.edible().sourceSolution());
        assertEquals(500, cheese.doseCents());
        assertEquals(20, cheese.delayTicks());
        assertTrue(cheese.edible().destroyOnEmpty(), "edible_defaults survives child composition");
        var pie = catalog.get(id("pumpkin_pie_slice"));
        assertEquals(IngestionProfile.Kind.FOOD, pie.edible().kind());
        assertEquals(500, pie.doseCents());
        assertEquals(20, pie.delayTicks());
        assertTrue(pie.edible().destroyOnEmpty());
        assertEquals(new IngestionProfile.Edible.SourceSolution(1225,
                Map.of(id("nutriment"), 750L, id("vitamin"), 350L, id("sugar"), 125L)),
                pie.edible().sourceSolution());
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
