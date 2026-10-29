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

class GrainLeafProductCatalogCandidateTest {
    private static final Map<String, String> PRODUCTS = Map.of("oat", "oat_bushel", "sugarcane", "sugarcane",
            "tea_plant", "leaves_tea", "tobacco", "leaves_tobacco", "cannabis", "leaves_cannabis",
            "rainbow_cannabis", "leaves_cannabis_rainbow");
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void sixGrainLeafProductsResolveInheritanceFoodJuiceAndSeedCrosslinksWithoutPublishing() {
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

        var expectedFood = Map.of("oat", Map.of(id("oats"), 1000L), "sugarcane", Map.of(id("sugar"), 1000L),
                "tea_plant", Map.of(id("vitamin"), 200L), "tobacco", Map.of(id("nicotine"), 200L),
                "cannabis", Map.of(id("thc"), 1500L), "rainbow_cannabis", Map.of(id("spacedrugs"), 300L,
                        id("lipolicide"), 300L, id("mindbreakertoxin"), 200L, id("happiness"), 200L, id("psicodine"), 60L));
        assertNotNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/reagent/spacedrugs.json"));
        assertNotNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/reagent/mindbreakertoxin.json"));
        var capacities = Map.of("oat", 1000, "sugarcane", 1000, "tea_plant", 200, "tobacco", 200, "cannabis", 1500, "rainbow_cannabis", 1060);
        for (var entry : PRODUCTS.entrySet()) {
            var seed = seeds.get(id(entry.getKey())); var productId = id(entry.getValue());
            assertNotNull(seed); assertEquals(productId, seed.product());
            var item = produce.get(productId); assertNotNull(item); assertEquals(id(entry.getKey()), item.seed());
            assertEquals(1f, item.potencyVisuals().minimumScale()); assertEquals(2f, item.potencyVisuals().maximumScale());
            var edible = food.get(productId).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
            assertEquals(expectedFood.get(entry.getKey()), sources.get(productId).contentsCents());
            assertEquals(capacities.get(entry.getKey()), sources.get(productId).capacityCents());
            assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
            assertTrue(edible.destroyOnEmpty()); assertTrue(edible.trash().isEmpty()); assertTrue(edible.requiresSpecialDigestion());
            assertEquals("food", extract.get(productId).grindableSolutionName());
            if (entry.getKey().equals("oat")) {
                assertEquals(Map.of(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("milkoat"), 500L),
                        extract.get(productId).juiceSolution().centContents());
            } else assertNull(extract.get(productId).juiceSolution());
            for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile"})
                BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, entry.getValue());
        }
        BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate("ingestion_profile", "ruminant_edible_base");
    }

    private static Map<ResourceLocation, com.google.gson.JsonObject> resources(String family, String base, String... names) {
        var all = new String[names.length + 1]; all[0] = base; System.arraycopy(names, 0, all, 1, names.length);
        var result = new LinkedHashMap<>(BotanyCatalogCandidateTest.resources(family, all));
        for (var product : PRODUCTS.values()) result.putAll(BotanyCatalogCandidateTest.resources(family, product));
        return result;
    }
}
