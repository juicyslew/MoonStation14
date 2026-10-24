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

class EggplantProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void eggplantProductResolvesAgainstSeedAndInheritedFoodDefaultsWithoutPublication() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extract = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "eggplant"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "eggplant"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "eggplant"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "eggplant"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extract.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(1, produce.size()); assertEquals(1, food.size()); assertEquals(1, extract.size());
        assertEquals(72, seeds.size()); assertEquals(71, seeds.keySet().stream().filter(key -> !key.equals(id("defaults"))).count());
        var seed = seeds.get(id("eggplant"));
        assertEquals(id("eggplant"), seed.product()); assertEquals(java.util.List.of(id("eggy")), seed.mutationTargets());
        var item = produce.get(id("eggplant"));
        assertNotNull(item); assertEquals(id("eggplant"), item.seed()); assertTrue(sources.containsKey(id("eggplant")));
        assertEquals(1f, item.potencyVisuals().minimumScale()); assertEquals(2f, item.potencyVisuals().maximumScale());
        var edible = food.get(id("eggplant")).edible();
        assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
        assertEquals(Map.of(id("nutriment"), 1000L, id("vitamin"), 400L), sources.get(id("eggplant")).contentsCents());
        assertEquals(1400, sources.get(id("eggplant")).capacityCents());
        assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
        assertFalse(edible.requiresSpecialDigestion()); assertTrue(edible.destroyOnEmpty()); assertTrue(edible.trash().isEmpty());
        assertEquals("food", extract.get(id("eggplant")).grindableSolutionName());
        assertNull(extract.get(id("eggplant")).juiceSolution());
        for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile", "item_food_solution_profile"})
            assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/" + family + "/eggplant.json"));
    }
}
