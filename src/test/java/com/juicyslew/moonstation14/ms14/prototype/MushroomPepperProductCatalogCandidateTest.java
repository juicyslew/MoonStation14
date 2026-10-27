package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MushroomPepperProductCatalogCandidateTest {
    private static final String[] NAMES = {"mushroom", "fly_amanita", "chili", "chilly"};
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void fourProductsResolveFoodProduceAndExtractionWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extract = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", NAMES));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, resources("produce_profile", "produce_base"),
                ModIngestionProfiles.TYPE, resources("ingestion_profile", "food_base"),
                ModExtractableProfiles.TYPE, resources("extractable_profile", "extractable_base"),
                com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extract.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(4, produce.size()); assertEquals(4, food.size()); assertEquals(4, extract.size());
        Map<String, Map<ResourceLocation, Long>> expected = Map.of(
                "mushroom", Map.of(id("nutriment"), 500L),
                "fly_amanita", Map.of(id("amatoxin"), 1000L, id("nutriment"), 500L),
                "chili", Map.of(id("nutriment"), 400L, id("capsaicinoil"), 1000L, id("vitamin"), 400L),
                "chilly", Map.of(id("nutriment"), 400L, id("frostoil"), 1000L, id("vitamin"), 400L));
        Map<String, Integer> capacity = Map.of("mushroom", 500, "fly_amanita", 1500, "chili", 1800, "chilly", 1800);
        for (var name : NAMES) {
            var edible = food.get(id(name)).edible();
            assertEquals(expected.get(name), sources.get(id(name)).contentsCents());
            assertEquals(capacity.get(name), sources.get(id(name)).capacityCents());
            assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
            assertTrue(edible.destroyOnEmpty()); assertFalse(edible.requiresSpecialDigestion()); assertTrue(edible.trash().isEmpty());
            var expectedSeed = name.equals("mushroom") ? "chanterelle" : name;
            assertEquals(id(expectedSeed), produce.get(id(name)).seed());
            assertEquals(1f, produce.get(id(name)).potencyVisuals().minimumScale()); assertEquals(2f, produce.get(id(name)).potencyVisuals().maximumScale());
            assertEquals("food", extract.get(id(name)).grindableSolutionName()); assertNull(extract.get(id(name)).juiceSolution());
            try (var ignored = getClass().getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/produce_profile/" + name + ".json")) { assertNull(ignored); }
            catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        var seeds = new LinkedHashMap<ResourceLocation, com.juicyslew.moonstation14.ms14.botany.SeedProfile>();
        manager.stage(Map.of(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs ->
                catalogs.get(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key,
                        com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        for (var name : NAMES)
            assertEquals(id(name), seeds.get(produce.get(id(name)).seed()).product());
        for (var name : NAMES) {
            String path = "data/moonstation14/moonstation14/ingestion_profile/" + name + ".json";
            assertNull(getClass().getClassLoader().getResource(path), "candidate must remain off main classpath: " + path);
            path = "data/moonstation14/moonstation14/extractable_profile/" + name + ".json";
            assertNull(getClass().getClassLoader().getResource(path), "candidate must remain off main classpath: " + path);
        }
    }

    private static Map<ResourceLocation, com.google.gson.JsonObject> resources(String family, String base) {
        var names = new String[NAMES.length + 1]; names[0] = base; System.arraycopy(NAMES, 0, names, 1, NAMES.length);
        return BotanyCatalogCandidateTest.resources(family, names);
    }
}
