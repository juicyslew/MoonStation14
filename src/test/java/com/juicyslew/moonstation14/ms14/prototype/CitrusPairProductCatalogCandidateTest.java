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

/** Source mapping: SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91, produce.yml FoodLime/FoodOrange inherit FoodLemon; FoodLemon inherits FoodProduceBase -> FoodInjectableBase -> FoodBase -> EdibleBase -> BaseItem. FoodLemon supplies 9u food contents Nutriment 5u/Vitamin 4u and PotencyVisuals defaults 1..2; leaves replace Extractable juice with JuiceLime/JuiceOrange 10u. */
class CitrusPairProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }

    @Test void limeAndOrangeResolveFoodProduceAndSeedLinksWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE); manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "lime", "orange"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "lime", "orange"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "lime", "orange"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources(),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "lime", "orange")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key,
                    ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key,
                    ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(2, food.size());
        for (String product : new String[]{"lime", "orange"}) {
            var edible = food.get(id(product)).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind());
            assertEquals(Map.of(id("nutriment"), 500L, id("vitamin"), 400L), sources.get(id(product)).contentsCents());
            assertEquals(900, sources.get(id(product)).capacityCents());
            assertTrue(sources.get(id(product)).capacityCents() >= sources.get(id(product)).contentsCents().values().stream().mapToLong(Long::longValue).sum());
            try (var stream = CitrusPairProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/item_food_solution_profile/" + product + ".json")) {
                assertNotNull(stream, "activated MAIN profile: " + product);
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            assertEquals(500, edible.doseCents()); assertEquals(20, edible.delayTicks());
            assertEquals(60, edible.forceFeedDelayTicks()); assertFalse(edible.requiresSpecialDigestion());
            assertTrue(edible.trash().isEmpty());
            var profile = produce.get(id(product));
            assertEquals(id(product), profile.seed());
            assertEquals("food", extractables.get(id(product)).grindableSolutionName());
            assertEquals(1f, profile.potencyVisuals().minimumScale()); assertEquals(2f, profile.potencyVisuals().maximumScale());
        }
        assertEquals(Map.of(reagent("juicelime"), 1000L), extractables.get(id("lime")).juiceSolution().centContents());
        assertEquals(Map.of(reagent("juiceorange"), 1000L), extractables.get(id("orange")).juiceSolution().centContents());
        var seeds = new LinkedHashMap<ResourceLocation, com.juicyslew.moonstation14.ms14.botany.SeedProfile>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs ->
                catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key,
                        com.juicyslew.moonstation14.ms14.botany.SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertEquals(id("lime"), seeds.get(produce.get(id("lime")).seed()).product());
        assertEquals(id("orange"), seeds.get(produce.get(id("orange")).seed()).product());
    }

}
