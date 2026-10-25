package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
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

/** Source mapping: SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91, produce.yml FoodApple/FoodGoldenApple inherit FoodProduceBase -> FoodInjectableBase -> FoodBase -> EdibleBase -> BaseItem. FoodProduceBase contributes Produce, Extractable(food), and PotencyVisuals (1..2 defaults); leaves set seed and fixed juice. */
class AppleProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }

    @Test void appleProductsResolveIndependentFoodAndProduceMappingsWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE); manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "apple", "golden_apple"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "apple", "golden_apple"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "apple", "golden_apple"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources(),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "apple", "golden_apple")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key,
                    ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key,
                    ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(2, food.size());
        assertEquals(Map.of(id("nutriment"), 1000L, id("vitamin"), 400L), sources.get(id("apple")).contentsCents());
        assertEquals(1400, sources.get(id("apple")).capacityCents());
        assertEquals(Map.of(id("nutriment"), 1000L, id("vitamin"), 400L, id("doctorsdelight"), 1300L), sources.get(id("golden_apple")).contentsCents());
        assertEquals(3000, sources.get(id("golden_apple")).capacityCents());
        for (String name : new String[]{"apple", "golden_apple"}) {
            var source = sources.get(id(name));
            assertTrue(source.capacityCents() >= source.contentsCents().values().stream().mapToLong(Long::longValue).sum(), name + " source contents fit");
            assertMainSourceAbsent(name);
        }
        for (String name : new String[]{"apple", "golden_apple"}) {
            var edible = food.get(id(name)).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
            assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks());
            assertEquals(60, edible.forceFeedDelayTicks()); assertFalse(edible.requiresSpecialDigestion());
            var profile = produce.get(id(name));
            assertEquals(id(name), profile.seed());
            assertEquals("food", extractables.get(id(name)).grindableSolutionName());
            assertEquals(1f, profile.potencyVisuals().minimumScale()); assertEquals(2f, profile.potencyVisuals().maximumScale());
        }
        assertEquals(Map.of(reagent("juiceapple"), 1000L), extractables.get(id("apple")).juiceSolution().centContents());
        assertEquals(Map.of(reagent("juiceapple"), 1000L, reagent("gold"), 1000L), extractables.get(id("golden_apple")).juiceSolution().centContents());
        var seeds = new LinkedHashMap<ResourceLocation, com.juicyslew.moonstation14.ms14.botany.SeedProfile>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs ->
                catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key,
                        com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertEquals(id("apple"), seeds.get(produce.get(id("apple")).seed()).product());
        assertEquals(id("golden_apple"), seeds.get(produce.get(id("golden_apple")).seed()).product());
    }

    private static void assertMainSourceAbsent(String name) {
        try (var stream = AppleProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/item_food_solution_profile/" + name + ".json")) {
            assertNull(stream, "candidate source remains TEST-only: " + name);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

}
