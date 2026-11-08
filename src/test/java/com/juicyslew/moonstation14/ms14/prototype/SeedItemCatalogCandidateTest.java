package com.juicyslew.moonstation14.ms14.prototype;

import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.SeedItemProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SeedItemCatalogCandidateTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    @Test
    void extendedPacketItemProfilesComposeAgainstSeedCatalogWithoutPublishing() {
        var mainNames = Set.of("wheat_seeds", "tomato_seeds", "potato_seeds", "poppy_seeds", "aloe_seeds", "meatwheat_seeds");
        var manager = new PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        manager.register(ModSeedItemProfiles.TYPE);
        var before = manager.snapshot();

        var resolvedSeeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs -> {
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, value) -> resolvedSeeds.put(key,
                    SeedProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
        });
        assertEquals(72, resolvedSeeds.size());
        assertSame(before, manager.snapshot());

        var boundSeeds = resolvedSeeds.entrySet().stream().filter(entry -> !entry.getKey().equals(id("defaults"))).toList();
        assertEquals(71, boundSeeds.size());
        var distinctPackets = boundSeeds.stream().map(entry -> entry.getValue().packet()).distinct().toList();
        assertEquals(70, distinctPackets.size());
        for (var entry : boundSeeds) assertNotNull(entry.getValue().packet(), "seed packet: " + entry.getKey());
        var candidateNames = distinctPackets.stream().map(ResourceLocation::getPath)
                .filter(name -> !mainNames.contains(name)).toList();
        assertEquals(64, candidateNames.size());

        var itemResources = BotanyCatalogCandidateTest.resources("seed_item_profile", candidateNames.toArray(String[]::new));
        var mainResources = BotanyCatalogCandidateTest.mainResources("seed_item_profile", mainNames.toArray(String[]::new));
        BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate("seed_item_profile", candidateNames.toArray(String[]::new));
        for (var name : mainNames) if (!name.equals("meatwheat_seeds"))
            assertNull(getClass().getClassLoader().getResource("botany-catalog-candidate/seed_item_profile/"
                    + name + ".json"), "main item must not have a candidate copy: " + name);
        assertNotNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/seed_item_profile/meatwheat_seeds.json"));
        itemResources.putAll(mainResources);

        manager.stage(Map.of(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources(),
                ModSeedItemProfiles.TYPE, itemResources), catalogs -> {
            var stageSeeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, value) -> stageSeeds.put(key,
                    SeedProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
            assertEquals(72, stageSeeds.size());
            var seedsByPacket = new LinkedHashMap<ResourceLocation, SeedProfile>();
            for (var entry : stageSeeds.entrySet()) {
                if (!entry.getKey().equals(id("defaults"))) {
                    assertNotNull(entry.getValue().packet(), "seed packet: " + entry.getKey());
                    seedsByPacket.put(entry.getValue().packet(), entry.getValue());
                }
            }
            assertEquals(71, stageSeeds.size() - 1);
            assertEquals(70, seedsByPacket.size());
            for (var entry : boundSeeds)
                assertTrue(catalogs.get(ModSeedItemProfiles.TYPE.typeId()).containsKey(entry.getValue().packet()),
                        "missing item for seed packet: " + entry.getValue().packet());

            var items = new LinkedHashMap<ResourceLocation, SeedItemProfile>();
            catalogs.get(ModSeedItemProfiles.TYPE.typeId()).forEach((key, value) -> items.put(key,
                    SeedItemProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
            assertEquals(70, items.size());
            for (var entry : items.entrySet()) {
                var seedId = entry.getValue().seedId();
                assertNotNull(seedId, "item seed binding: " + entry.getKey());
                assertTrue(stageSeeds.containsKey(seedId), "unknown seed: " + seedId);
                assertEquals(entry.getKey(), stageSeeds.get(seedId).packet());
            }
            assertEquals(id("pea_seeds"), stageSeeds.get(id("world_pea")).packet());
            assertEquals(id("pea_seeds"), stageSeeds.get(id("pea")).packet());
            assertEquals(stageSeeds.get(id("world_pea")).packet(), stageSeeds.get(id("pea")).packet());
            assertEquals(id("pea"), items.get(id("pea_seeds")).seedId());
        });

        assertSame(before, manager.snapshot());
    }
}
