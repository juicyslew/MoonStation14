package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegumeProductCatalogCandidateTest {
    private static final Map<String, String> PRODUCTS = Map.of("rice", "rice_bushel", "soybeans", "food_soybeans",
            "koibean", "food_koibean", "pea", "food_pea_pod", "laughin_pea", "food_laughin_pea_pod",
            "world_pea", "food_world_peas");
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void sixLegumeProductsResolveAgainstSeedsAndInheritedDefaultsWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extract = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", PRODUCTS.values().toArray(String[]::new)));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, resources("produce_profile", "produce_base"),
                ModIngestionProfiles.TYPE, resources("ingestion_profile", "food_base", "ruminant_edible_base"),
                ModExtractableProfiles.TYPE, resources("extractable_profile", "extractable_base"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extract.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> { if (!key.equals(id("defaults"))) seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()); });
        });
        assertSame(before, manager.snapshot());
        assertEquals(6, produce.size()); assertEquals(6, food.size()); assertEquals(6, extract.size());
        assertEquals(71, seeds.size()); assertFalse(seeds.containsKey(id("defaults")));
        assertEquals(id("pea_seeds"), seeds.get(id("world_pea")).packet());
        var expectedFood = Map.of("rice", Map.of(id("rice"), 1000L), "soybeans", Map.of(id("nutriment"), 400L),
                "koibean", Map.of(id("nutriment"), 500L, id("carpotoxin"), 200L), "pea", Map.of(id("nutriment"), 300L, id("vitamin"), 200L),
                "laughin_pea", Map.of(id("nutriment"), 300L, id("sugar"), 500L, id("laughter"), 500L),
                "world_pea", Map.of(id("happiness"), 300L, id("nutriment"), 300L, id("pax"), 200L));
        var capacities = Map.of("rice", 1000, "soybeans", 400, "koibean", 700, "pea", 500, "laughin_pea", 1300, "world_pea", 800);
        var juices = Map.of("soybeans", Map.of(id("milksoy"), 500L), "koibean", Map.of(id("carpotoxin"), 200L), "laughin_pea", Map.of(id("laughinsyrup"), 500L));
        for (var entry : PRODUCTS.entrySet()) {
            var seed = seeds.get(id(entry.getKey())); var productId = id(entry.getValue());
            assertNotNull(seed); assertEquals(productId, seed.product());
            var item = produce.get(productId); assertNotNull(item); assertEquals(id(entry.getKey()), item.seed());
            assertEquals(1f, item.potencyVisuals().minimumScale()); assertEquals(2f, item.potencyVisuals().maximumScale());
            var edible = food.get(productId).edible(); assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
            assertEquals(expectedFood.get(entry.getKey()), sources.get(productId).contentsCents());
            assertEquals(capacities.get(entry.getKey()), sources.get(productId).capacityCents());
            assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
            assertTrue(edible.destroyOnEmpty()); assertTrue(edible.trash().isEmpty());
            assertEquals(entry.getKey().equals("rice") || entry.getKey().equals("soybeans") || entry.getKey().equals("koibean"), edible.requiresSpecialDigestion());
            assertEquals(juiceKeys(juices.get(entry.getKey())), extract.get(productId).juiceSolution() == null ? null : extract.get(productId).juiceSolution().centContents());
            assertEquals("food", extract.get(productId).grindableSolutionName());
            assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/produce_profile/" + entry.getValue() + ".json"));
            assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/ingestion_profile/" + entry.getValue() + ".json"));
            assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/extractable_profile/" + entry.getValue() + ".json"));
        }
        assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/ingestion_profile/ruminant_edible_base.json"));
    }

    private static Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> juiceKeys(Map<ResourceLocation, Long> contents) {
        if (contents == null) return null;
        var result = new LinkedHashMap<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>();
        contents.forEach((key, value) -> result.put(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey(key.getPath()), value));
        return result;
    }

    private static Map<ResourceLocation, com.google.gson.JsonObject> resources(String family, String base, String... names) {
        var all = new String[names.length + 1]; all[0] = base; System.arraycopy(names, 0, all, 1, names.length);
        var result = new LinkedHashMap<>(BotanyCatalogCandidateTest.resources(family, all));
        for (var product : PRODUCTS.values()) result.putAll(BotanyCatalogCandidateTest.resources(family, product));
        return result;
    }
}
