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

/** Source mapping: SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91, FoodCabbage -> FoodProduceBase -> FoodInjectableBase -> FoodBase -> EdibleBase -> BaseItem. */
class CabbageProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void cabbageFoodAndProduceResolveIndependentContentsAndSeedLinkWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE); manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var extraction = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "cabbage"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "cabbage"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "cabbage"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources("seed_profile", "defaults", "cabbage"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "cabbage")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extraction.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());

        var profile = produce.get(id("cabbage"));
        assertEquals(id("cabbage"), profile.seed());
        assertEquals(1, extraction.size());
        assertEquals("food", extraction.get(id("cabbage")).grindableSolutionName());
        assertNull(extraction.get(id("cabbage")).juiceSolution());
        assertEquals(1f, profile.potencyVisuals().minimumScale()); assertEquals(2f, profile.potencyVisuals().maximumScale());

        var edible = food.get(id("cabbage")).edible();
        assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
        assertEquals(Map.of(id("nutriment"), 1000L, id("vitamin"), 400L), sources.get(id("cabbage")).contentsCents());
        assertEquals(1400, sources.get(id("cabbage")).capacityCents());
        assertTrue(sources.get(id("cabbage")).capacityCents() >= sources.get(id("cabbage")).contentsCents().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks());
        assertEquals(60, edible.forceFeedDelayTicks()); assertFalse(edible.requiresSpecialDigestion());
        assertFalse(edible.consumeSource()); assertTrue(edible.destroyOnEmpty()); assertTrue(edible.trash().isEmpty());

        var seed = seeds.get(id("cabbage"));
        assertEquals(id("cabbage"), seed.product());
        assertEquals(new SeedProfile.Chemical(1, 10, 10, true), seed.chemicals().get(id("nutriment")));
        assertEquals(new SeedProfile.Chemical(1, 4, 25, true), seed.chemicals().get(id("vitamin")));
        try (var stream = CabbageProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/item_food_solution_profile/cabbage.json")) {
            assertNotNull(stream, "activated production profile");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
