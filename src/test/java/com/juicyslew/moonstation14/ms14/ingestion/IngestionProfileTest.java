package com.juicyslew.moonstation14.ms14.ingestion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
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
        assertEquals(java.util.List.of(), defaults.trash(), "ordinary edible profiles emit no trash by default");
        var programmaticFood = new IngestionProfile.Edible(500, 20, 40, true, false, id("ss14/items/eat"), -2f, .25f, IngestionProfile.Kind.FOOD);
        assertEquals(id("ss14/items/eat"), programmaticFood.completionSound());
        assertEquals(-2f, programmaticFood.volumeDb());
        assertEquals(.25f, programmaticFood.pitchVariation());
        assertEquals(id("ss14/items/drink"), new IngestionProfile.Edible(500, 20, true).completionSound());
        assertTrue(IngestionProfile.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).error().isPresent());
        JsonObject invalid = new JsonObject();
        JsonObject edible = new JsonObject(); edible.addProperty("dose_cents", 0); invalid.add("edible", edible);
        assertTrue(IngestionProfile.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
    }

    @Test void trashOutputsPreserveDuplicatesOrderAndResolvedInheritance() {
        PrototypeManager manager = new PrototypeManager(); manager.register(ModIngestionProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        JsonObject base = JsonParser.parseString("""
                {"abstract":true,"edible":{"kind":"FOOD","trash":["minecraft:stick","minecraft:stick","minecraft:oak_planks"]}}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"parent":"test:trash_base"}
                """).getAsJsonObject();
        JsonObject replacement = JsonParser.parseString("""
                {"parent":"test:trash_base","edible":{"trash":["minecraft:oak_planks"]}}
                """).getAsJsonObject();
        manager.reload(ModIngestionProfiles.TYPE, Map.of(id("test:trash_base"), base,
                id("test:trash_child"), child, id("test:trash_replacement"), replacement));
        assertEquals(java.util.List.of(id("minecraft:stick"), id("minecraft:stick"), id("minecraft:oak_planks")),
                manager.snapshot(ModIngestionProfiles.TYPE).get(id("test:trash_child")).edible().trash());
        assertEquals(java.util.List.of(id("minecraft:oak_planks")),
                manager.snapshot(ModIngestionProfiles.TYPE).get(id("test:trash_replacement")).edible().trash());
    }

    @Test void invalidTrashReferenceRejectsCandidateWithoutReplacingResolvedCatalog() {
        PrototypeManager manager = new PrototypeManager(); manager.register(ModIngestionProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        var valid = JsonParser.parseString("""
                {"edible":{"kind":"FOOD","trash":["minecraft:stick"]}}
                """).getAsJsonObject();
        manager.reload(ModIngestionProfiles.TYPE, Map.of(id("test:edible"), valid));
        var snapshot = manager.snapshot(ModIngestionProfiles.TYPE);
        var invalid = JsonParser.parseString("""
                {"edible":{"kind":"FOOD","trash":["missing:no_item"]}}
                """).getAsJsonObject();
        assertThrows(com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException.class,
                () -> manager.reload(ModIngestionProfiles.TYPE, Map.of(id("test:edible"), invalid)));
        assertSame(snapshot, manager.snapshot(ModIngestionProfiles.TYPE), "invalid output reference must retain published profile catalog");
    }

    @Test void bundledInheritanceResolvesDistinctDrinkAndBottleParents() {
        PrototypeManager manager = new PrototypeManager(); manager.register(ModIngestionProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        Map<ResourceLocation, JsonObject> resources = new LinkedHashMap<>();
        for (String name : new String[]{"edible_defaults", "drink_base", "jug", "bottle", "puddle", "pill", "wheat_bushel",
                "food_base", "food_baking_base", "cheese_slice", "pumpkin_pie_slice", "cotton_material", "pyrotton_material"})
            resources.put(id(name), resource("ingestion_profile", name));
        manager.reload(ModIngestionProfiles.TYPE, resources);
        Map<ResourceLocation, JsonObject> sourceResources = new LinkedHashMap<>();
        for (String name : new String[]{"cheese_slice", "pumpkin_pie_slice", "wheat_bushel", "cotton_material", "pyrotton_material"})
            sourceResources.put(id(name), resource("item_food_solution_profile", name));
        manager.reload(ModItemFoodSolutionProfiles.TYPE, sourceResources);
        var catalog = manager.snapshot(ModIngestionProfiles.TYPE);
        var sources = manager.snapshot(ModItemFoodSolutionProfiles.TYPE);
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
        assertNull(sources.get(id("pill")), "pill source contents are supplied by ChemMaster, not a static item profile");
        assertEquals(id("ss14/items/pill"), pill.completionSound());
        assertEquals(-1f, pill.completionVolumeDb());
        assertEquals(.2f, pill.completionPitchVariation());
        assertEquals(id("ss14/items/drink"), catalog.get(id("bottle")).completionSound());
        var cheese = catalog.get(id("cheese_slice"));
        assertEquals(IngestionProfile.Kind.FOOD, cheese.edible().kind());
        assertEquals(id("ss14/items/eat"), cheese.completionSound());
        assertEquals(IngestionProfile.Kind.DRINK, catalog.get(id("puddle")).edible().kind());
        assertEquals(new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(600, Map.of(id("nutriment"), 375L)), sources.get(id("cheese_slice")));
        assertEquals(500, cheese.doseCents());
        assertEquals(20, cheese.delayTicks());
        assertTrue(cheese.edible().destroyOnEmpty(), "edible_defaults survives child composition");
        var wheat = catalog.get(id("wheat_bushel"));
        assertEquals(IngestionProfile.Kind.FOOD, wheat.edible().kind());
        assertTrue(wheat.edible().requiresSpecialDigestion());
        assertEquals(60, wheat.edible().forceFeedDelayTicks());
        assertEquals(1000, sources.get(id("wheat_bushel")).capacityCents());
        assertEquals(Map.of(id("flour"), 1000L), sources.get(id("wheat_bushel")).contentsCents());
        var cotton = catalog.get(id("cotton_material"));
        assertTrue(cotton.edible().requiresSpecialDigestion());
        assertEquals(60, cotton.forceFeedDelayTicks());
        assertEquals(new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(500, Map.of(id("fiber"), 500L)), sources.get(id("cotton_material")));
        var pyrotton = catalog.get(id("pyrotton_material"));
        assertTrue(pyrotton.edible().requiresSpecialDigestion());
        assertEquals(new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(1000,
                Map.of(id("fiber"), 500L, id("phlogiston"), 500L)), sources.get(id("pyrotton_material")));
        var specialOverride = JsonParser.parseString("{\"id\":\"moonstation14:wheat_bushel\",\"edible\":{\"requires_special_digestion\":false}}").getAsJsonObject();
        assertFalse(IngestionProfile.CODEC.parse(JsonOps.INSTANCE, specialOverride).getOrThrow().edible().requiresSpecialDigestion());
        var pie = catalog.get(id("pumpkin_pie_slice"));
        assertEquals(IngestionProfile.Kind.FOOD, pie.edible().kind());
        assertEquals(500, pie.doseCents());
        assertEquals(20, pie.delayTicks());
        assertTrue(pie.edible().destroyOnEmpty());
        assertEquals(new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(1225,
                Map.of(id("nutriment"), 750L, id("vitamin"), 350L, id("sugar"), 125L)),
                sources.get(id("pumpkin_pie_slice")));
    }

    @Test void lookupsFailClosedAndReadinessRequiresBundledEntries() {
        assertFalse(ModIngestionProfiles.serverReady());
        assertFalse(ModIngestionProfiles.clientReady());
        assertNull(ModIngestionProfiles.serverLookup(null));
        assertNull(ModIngestionProfiles.clientLookup(id("unknown")));
    }

    private static JsonObject resource(String family, String name) {
        String path = "/data/moonstation14/moonstation14/" + family + "/" + name + ".json";
        try (var stream = IngestionProfileTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.parse(path.contains(":") ? path : "moonstation14:" + path); }
}
