package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Candidate-only cocoa conversion and produce composition; no prototype is installed or published. */
class CocoaConversionCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    private static Map<ResourceLocation, JsonObject> combine(Map<ResourceLocation, JsonObject>... maps) {
        var result = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (var map : maps) result.putAll(map);
        return result;
    }

    @Test void cocoaAndMeatwheatCandidateCatalogsResolveWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModItemProcessingProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extracts = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var processing = new LinkedHashMap<ResourceLocation, ItemProcessingProfile>();
        var sourceResources = combine(BotanyCatalogCandidateTest.mainResources("item_food_solution_profile", "food_cocoa_beans", "food_meat_wheat"),
                BotanyCatalogCandidateTest.resources("item_food_solution_profile", "cocoa_pod", "meatwheat_bushel"));
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(sourceResources);
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, combine(BotanyCatalogCandidateTest.resources("produce_profile", "produce_base"), BotanyCatalogCandidateTest.resources("produce_profile", "cocoa_pod"), BotanyCatalogCandidateTest.mainResources("produce_profile", "meatwheat_bushel")),
                ModIngestionProfiles.TYPE, combine(BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base"), BotanyCatalogCandidateTest.resources("ingestion_profile", "ruminant_edible_base", "cocoa_pod"), BotanyCatalogCandidateTest.mainResources("ingestion_profile", "meatwheat_bushel")),
                ModSeedProfiles.TYPE, combine(BotanyCatalogCandidateTest.resources(), BotanyCatalogCandidateTest.resources("seed_profile", "cocoa", "meatwheat")),
                ModExtractableProfiles.TYPE, combine(BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base"), BotanyCatalogCandidateTest.resources("extractable_profile", "cocoa_pod"), BotanyCatalogCandidateTest.mainResources("extractable_profile", "meatwheat_bushel")),
                ModItemProcessingProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_processing_profile", "cocoa_pod", "meatwheat_bushel")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extracts.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModItemProcessingProfiles.TYPE.typeId()).forEach((key, raw) -> processing.put(key, ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });

        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(2, food.size()); assertEquals(2, extracts.size());
        assertEquals(72, seeds.size());
        assertEquals(71, seeds.keySet().stream().filter(key -> !key.equals(id("defaults"))).count());
        assertEquals(id("cocoa_pod"), seeds.get(id("cocoa")).product());
        assertEquals(id("meatwheat_bushel"), seeds.get(id("meatwheat")).product());

        assertFood(food.get(id("cocoa_pod")), false);
        assertEquals(1400, sources.get(id("cocoa_pod")).capacityCents());
        assertEquals(Map.of(id("nutriment"), 200L, id("vitamin"), 100L, id("cocoapowder"), 100L), sources.get(id("cocoa_pod")).contentsCents());
        assertFood(food.get(id("meatwheat_bushel")), true);
        assertEquals(1000, sources.get(id("meatwheat_bushel")).capacityCents());
        assertEquals(Map.of(id("uncookedanimalproteins"), 1000L), sources.get(id("meatwheat_bushel")).contentsCents());

        for (String name : new String[]{"cocoa_pod", "meatwheat_bushel"}) {
            assertEquals(id(name.equals("cocoa_pod") ? "cocoa" : "meatwheat"), produce.get(id(name)).seed());
            assertEquals(1f, produce.get(id(name)).potencyVisuals().minimumScale());
            assertEquals(2f, produce.get(id(name)).potencyVisuals().maximumScale());
            assertNull(extracts.get(id(name)).juiceSolution());
            assertEquals(500, food.get(id(name)).edible().doseCents());
            assertEquals(20, food.get(id(name)).edible().delayTicks());
            assertEquals(60, food.get(id(name)).edible().forceFeedDelayTicks());
            assertTrue(food.get(id(name)).edible().destroyOnEmpty());
            assertTrue(food.get(id(name)).edible().trash().isEmpty());
        }
        assertFalse(food.get(id("cocoa_pod")).edible().requiresSpecialDigestion());
        assertTrue(food.get(id("meatwheat_bushel")).edible().requiresSpecialDigestion());

        assertProcessing(processing.get(id("cocoa_pod")), "food_cocoa_beans", "botany/produce/packetrip");
        assertProcessing(processing.get(id("meatwheat_bushel")), "food_meat_wheat", "slime_squish");
        assertRegisteredReagent("cocoapowder"); assertRegisteredReagent("uncookedanimalproteins");
        assertRegisteredItem("food_cocoa_beans"); assertRegisteredItem("food_meat_wheat");

        for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile", "item_processing_profile", "item_food_solution_profile"})
            for (String name : new String[]{"cocoa_pod", "meatwheat_bushel"}) {
                var resource = getClass().getClassLoader().getResource("data/moonstation14/moonstation14/" + family + "/" + name + ".json");
                BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, name);
            }
    }

    private static void assertFood(IngestionProfile profile, boolean special) {
        var edible = profile.edible();
        assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
        assertEquals(special, edible.requiresSpecialDigestion());
        assertTrue(edible.destroyOnEmpty()); assertTrue(edible.trash().isEmpty());
    }

    private static void assertProcessing(ItemProcessingProfile profile, String output, String cue) {
        assertNotNull(profile); assertNotNull(profile.spawnItemsOnUse());
        assertEquals(1, profile.spawnItemsOnUse().uses());
        assertEquals(1, profile.spawnItemsOnUse().outputs().size());
        var item = profile.spawnItemsOnUse().outputs().get(0);
        assertEquals(id(output), item.output()); assertEquals(1, item.count());
        assertEquals(id(cue), profile.spawnItemsOnUse().sound());
    }

    private static void assertRegisteredReagent(String name) {
        var path = "data/moonstation14/moonstation14/reagent/" + name + ".json";
        try (var stream = getResource(path)) {
            assertNotNull(stream, path);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(name, json.get("id").getAsString());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void assertRegisteredItem(String name) {
        assertNotNull(getClassLoader().getResource("data/moonstation14/moonstation14/ingestion_profile/" + name + ".json"), name);
    }
    private static java.io.InputStream getResource(String path) { return getClassLoader().getResourceAsStream(path); }
    private static ClassLoader getClassLoader() { return CocoaConversionCatalogCandidateTest.class.getClassLoader(); }
}
