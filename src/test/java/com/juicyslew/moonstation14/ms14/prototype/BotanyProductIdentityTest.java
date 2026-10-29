package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BotanyProductIdentityTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void candidateProduceProfilesBindCanonicalProductsToTheirReferencingSeeds() {
        var seedRaw = BotanyCatalogCandidateTest.resources();
        assertEquals(72, seedRaw.size());
        var manager = new PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, seedRaw), catalogs -> catalogs.get(ModSeedProfiles.TYPE.typeId())
                .forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(before, manager.snapshot());
        var products = seeds.entrySet().stream().filter(entry -> !entry.getKey().equals(id("defaults")))
                .map(entry -> entry.getValue().product().getPath()).distinct().toList();
        assertEquals(71, products.size());
        var loader = BotanyProductIdentityTest.class.getClassLoader();
        var existing = products.stream().filter(product -> loader.getResource("botany-catalog-candidate/produce_profile/" + product + ".json") != null).toList();
        assertEquals(60, existing.size());
        var namesToLoad = new java.util.ArrayList<>(existing);
        namesToLoad.add("produce_base");
        var produceRaw = BotanyCatalogCandidateTest.resources("produce_profile", namesToLoad.toArray(String[]::new));
        produceRaw.putAll(BotanyCatalogCandidateTest.mainResources("produce_profile", "meatwheat_bushel"));
        produceRaw.putAll(BotanyCatalogCandidateTest.mainResources("produce_profile", "tomato", "poppy"));
        var produceManager = new PrototypeManager();
        produceManager.register(ModProduceProfiles.TYPE);
        var produceBefore = produceManager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        produceManager.stage(Map.of(ModProduceProfiles.TYPE, produceRaw), catalogs -> catalogs.get(ModProduceProfiles.TYPE.typeId())
                .forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(produceBefore, produceManager.snapshot());
        assertEquals(62, produce.size());
        for (var key : existing) {
            var profile = produce.get(id(key));
            assertNotNull(profile, key);
            var references = seeds.entrySet().stream().filter(entry -> !entry.getKey().equals(id("defaults")) && entry.getValue().product().equals(id(key))).toList();
            assertFalse(references.isEmpty(), key);
            for (var seed : references) {
                var expectedSeed = key.equals("holymelon") ? id("watermelon") : seed.getKey();
                assertEquals(expectedSeed, profile.seed(), key + " seed binding");
                assertEquals(seed.getValue().product(), id(key), key + " canonical product");
            }
        }
        assertTrue(produce.containsKey(id("tomato")));
        assertTrue(produce.containsKey(id("poppy")));

        var mainRoot = "data/moonstation14/moonstation14/produce_profile/";
        BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate("produce_profile", existing.toArray(String[]::new));
        var aliases = new String[]{"food_lemon", "food_lemoon", "food_lime", "food_orange", "food_apple", "food_golden_apple", "chanterelle"};
        for (var family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile"}) {
            for (var alias : aliases) {
                assertNull(loader.getResource("botany-catalog-candidate/" + family + "/" + alias + ".json"), "candidate alias " + family + "/" + alias);
                assertNull(loader.getResource("data/moonstation14/moonstation14/" + family + "/" + alias + ".json"), "main alias " + family + "/" + alias);
            }
        }
    }
}
