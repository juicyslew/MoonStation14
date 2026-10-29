package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BotanyCatalogSeedlingInitializationTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void everySupportedCatalogSeedStartsAsHealthyLowestStageSeedling() {
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        manager.register(ModProduceProfiles.TYPE);
        var resolved = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, JsonObject>>();
        manager.stage(Map.of(ModSeedProfiles.TYPE,
                BotanyCatalogCandidateTest.mainResourcesAll("seed_profile"), ModProduceProfiles.TYPE,
                BotanyCatalogCandidateTest.mainResourcesAll("produce_profile")), resolved::putAll);
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        resolved.get(ModSeedProfiles.TYPE.typeId()).forEach((key, json) ->
                seeds.put(key, SeedProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()));
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        resolved.get(ModProduceProfiles.TYPE.typeId()).forEach((key, json) ->
                produce.put(key, ProduceProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()));
        var seedCatalog = new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(seeds);
        var produceCatalog = new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(produce);
        var available = BotanySpeciesAvailability.policy(seedCatalog, produceCatalog, ignored -> true, ignored -> true);
        var entries = com.juicyslew.moonstation14.ms14.botany.fixture.BotanyCatalogGalleryPlan
                .create(net.minecraft.core.BlockPos.ZERO, seedCatalog, produceCatalog, available).entries();
        var excluded = java.util.Set.of(id("defaults"), id("killer_tomato"), id("anomaly_berry"),
                id("gatfruit"), id("fake_capfruit"), id("real_capfruit"), id("spacemans_trumpet"));
        var expected = seeds.keySet().stream().filter(key -> !excluded.contains(key)).collect(java.util.stream.Collectors.toSet());
        assertEquals(65, expected.size());
        assertEquals(expected, entries.stream().map(com.juicyslew.moonstation14.ms14.botany.fixture.BotanyCatalogGalleryPlan.Entry::species)
                .collect(java.util.stream.Collectors.toSet()));
        for (var entry : entries) {
            assertSame(seeds.get(entry.species()), entry.profile());
            var plant = new PlantInstance(0, 0, 0);
            BotanyFixtureScenarios.prepareFixtureSeedling(plant, entry.species(), entry.profile(), 0);
            assertEquals(1, plant.age(), entry.species().toString());
            assertEquals(1, PlantMetabolismEvaluator.appearance(plant, entry.profile()).stage(), entry.species().toString());
            assertEquals(entry.species(), plant.seed());
            assertEquals(entry.species(), plant.genetics().species());
            assertEquals(100, plant.water());
            assertEquals(100, plant.nutrition());
            assertEquals(0, plant.weeds());
            assertEquals(0, plant.soilToxins());
            assertEquals(entry.profile().biology().endurance(), plant.health());
            assertFalse(plant.dead());
            assertFalse(plant.ready());
        }
        var pea = entries.stream().filter(entry -> entry.species().equals(id("pea"))).findFirst().orElseThrow();
        var worldPea = entries.stream().filter(entry -> entry.species().equals(id("world_pea"))).findFirst().orElseThrow();
        assertEquals(pea.profile().packet(), worldPea.profile().packet());
        assertNotEquals(pea.species(), worldPea.species());
        var peaPlant = new PlantInstance(0, 0, 0);
        var worldPeaPlant = new PlantInstance(0, 0, 0);
        BotanyFixtureScenarios.prepareFixtureSeedling(peaPlant, pea.species(), pea.profile(), 0);
        BotanyFixtureScenarios.prepareFixtureSeedling(worldPeaPlant, worldPea.species(), worldPea.profile(), 0);
        assertNotEquals(peaPlant.genetics().species(), worldPeaPlant.genetics().species());
    }
}
