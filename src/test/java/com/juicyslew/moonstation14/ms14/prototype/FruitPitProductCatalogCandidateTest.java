package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedItemProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedItemProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Candidate composition from pinned SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91. */
class FruitPitProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }

    @Test void cornBungoAndCherryResolveFoodProduceExtractionAndFixedOutputsWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModSeedProfiles.TYPE);
        manager.register(ModIngestionProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModSeedItemProfiles.TYPE); manager.register(ModItemProcessingProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extract = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var ingestionRaw = BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "corn", "bungo", "cherry", "food_bungo_pit", "trash_cherry_pit");
        var sourceSolutions = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources(
                "item_food_solution_profile", "corn", "bungo", "cherry", "food_bungo_pit", "trash_cherry_pit", "food_corn_trash"));
        var unknownOutput = new LinkedHashMap<>(BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "corn"));
        unknownOutput.get(id("corn")).getAsJsonObject("edible").add("trash",
                new com.google.gson.JsonArray());
        unknownOutput.get(id("corn")).getAsJsonObject("edible").getAsJsonArray("trash")
                .add("moonstation14:synthetic_missing_output");
        var rejectedIngestion = new PrototypeManager();
        rejectedIngestion.register(ModIngestionProfiles.TYPE);
        var rejectedBefore = rejectedIngestion.snapshot();
        var loadFailure = assertThrows(PrototypeLoadException.class, () -> rejectedIngestion.stage(
                Map.of(ModIngestionProfiles.TYPE, unknownOutput), ignored -> { }));
        assertTrue(rootMessages(loadFailure).contains("unknown edible trash item: moonstation14:synthetic_missing_output"), rootMessages(loadFailure));
        assertSame(rejectedBefore, rejectedIngestion.snapshot(), "invalid registered-item references must not publish");

        // Test-only composition: resolve inheritance and decode policies, but do not activate an
        // ingestion catalog until every declared trash item is registered in the runtime.
        PrototypeResolver.resolve(ingestionRaw, new com.juicyslew.moonstation14.ms14.ingestion.IngestionProfileMergeStrategy()).asMap()
                .forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "corn", "bungo", "cherry"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources("seed_profile", "defaults", "corn", "bungo", "cherry"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "corn", "bungo", "cherry", "food_bungo_pit", "trash_cherry_pit")
        ), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extract.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(3, produce.size()); assertEquals(4, seeds.size());
        assertEquals(3, seeds.keySet().stream().filter(key -> !key.equals(id("defaults"))).count());
        for (String product : new String[]{"corn", "bungo", "cherry"}) {
            assertEquals(id(product), seeds.get(id(product)).product());
            assertEquals(id(product), produce.get(id(product)).seed());
            assertEquals("food", extract.get(id(product)).grindableSolutionName());
            assertNotNull(extract.get(id(product)).juiceSolution());
        }
        assertFood(food.get(id("corn")), sourceSolutions.get(id("corn")), 2000, Map.of(id("cornmeal"), 1500L, id("nutriment"), 500L), id("food_corn_trash"));
        assertFood(food.get(id("bungo")), sourceSolutions.get(id("bungo")), 2000, Map.of(id("nutriment"), 1000L, id("enzyme"), 1000L), id("food_bungo_pit"));
        assertFood(food.get(id("cherry")), sourceSolutions.get(id("cherry")), 800, Map.of(id("nutriment"), 300L, id("vitamin"), 300L), id("trash_cherry_pit"));
        assertEquals(Map.of(reagent("cornoil"), 300L, reagent("enzyme"), 200L), extract.get(id("corn")).juiceSolution().centContents());
        assertEquals(Map.of(reagent("juicebungo"), 1000L), extract.get(id("bungo")).juiceSolution().centContents());
        assertEquals(Map.of(reagent("juicecherry"), 500L), extract.get(id("cherry")).juiceSolution().centContents());

        assertFood(food.get(id("food_bungo_pit")), sourceSolutions.get(id("food_bungo_pit")), 700, Map.of(id("nutriment"), 200L, id("bungotoxin"), 500L), null);
        assertFood(food.get(id("trash_cherry_pit")), sourceSolutions.get(id("trash_cherry_pit")), 100, Map.of(id("toxin"), 100L), null);
        var cob = sourceSolutions.get(id("food_corn_trash"));
        assertNotNull(cob); assertEquals(2000, cob.capacityCents()); assertEquals(Map.of(id("cornmeal"), 1000L), cob.contentsCents());
        assertFalse(food.containsKey(id("food_corn_trash")));
        assertFalse(produce.containsKey(id("food_corn_trash"))); assertFalse(extract.containsKey(id("food_corn_trash")));
        assertFalse(seeds.containsKey(id("food_corn_trash")));
        for (String pit : new String[]{"food_bungo_pit", "trash_cherry_pit"}) {
            assertTrue(food.containsKey(id(pit)), "ingestion profile is keyed by the actual output item");
            assertTrue(extract.containsKey(id(pit)), "extractable profile is keyed by the actual output item");
            assertEquals("food", extract.get(id(pit)).grindableSolutionName());
            assertNull(extract.get(id(pit)).juiceSolution());
        }
        assertFalse(food.containsKey(id("bungo_pit"))); assertFalse(food.containsKey(id("cherry_pit")));
        assertFalse(extract.containsKey(id("bungo_pit"))); assertFalse(extract.containsKey(id("cherry_pit")));
        assertPitSeedFallback("food_bungo_pit", "bungo");
        assertPitSeedFallback("trash_cherry_pit", "cherry");

        assertButcherOutput("corn", "food_corn_trash");
        assertButcherOutput("bungo", "food_bungo_pit");
        assertButcherOutput("cherry", "trash_cherry_pit");
        for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile", "item_processing_profile", "seed_item_profile", "item_food_solution_profile"})
            BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, "corn", "bungo", "cherry", "food_corn_trash", "food_bungo_pit", "trash_cherry_pit");
    }

    private static String rootMessages(Throwable error) {
        var result = new StringBuilder();
        for (var current = error; current != null; current = current.getCause()) result.append(current.getMessage()).append('\n');
        return result.toString();
    }

    @Test void cornCobHasIndependentFoodSourceButNoEdibleExtractionProduceOrSeedComposition() {
        var source = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "food_corn_trash"));
        assertEquals(Map.of(id("cornmeal"), 1000L), source.get(id("food_corn_trash")).contentsCents());
        assertCandidateAbsent("ingestion_profile", "food_corn_trash");
        assertCandidateAbsent("extractable_profile", "food_corn_trash");
        assertCandidateAbsent("produce_profile", "food_corn_trash");
        assertCandidateAbsent("seed_item_profile", "food_corn_trash");
        assertCandidateAbsent("seed_item_profile", "corn_trash");
    }

    private static void assertFood(IngestionProfile profile, ItemFoodSolutionProfile source, int capacity, Map<ResourceLocation, Long> contents, ResourceLocation trash) {
        assertNotNull(profile);
        assertNotNull(source);
        assertEquals(IngestionProfile.Kind.FOOD, profile.edible().kind());
        assertEquals(contents, source.contentsCents());
        assertEquals(capacity, source.capacityCents());
        assertEquals(trash == null ? java.util.List.of() : java.util.List.of(trash), profile.edible().trash());
        assertEquals(500, profile.edible().doseCents()); assertEquals(20, profile.edible().delayTicks());
        assertEquals(60, profile.edible().forceFeedDelayTicks());
    }

    private static void assertPitSeedFallback(String prototype, String seed) {
        var raw = BotanyCatalogCandidateTest.resources("seed_item_profile", prototype).get(id(prototype));
        var resolved = PrototypeResolver.resolve(Map.of(id(prototype), raw), null);
        var decoded = SeedItemProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, resolved.get(id(prototype))).getOrThrow();
        assertEquals(id(seed), decoded.seedId());
        var candidateSeeds = BotanyCatalogCandidateTest.resources("seed_profile", "defaults", seed);
        var seedRows = PrototypeResolver.resolve(candidateSeeds, new com.juicyslew.moonstation14.ms14.botany.SeedProfileMergeStrategy());
        var seedProfile = SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, seedRows.get(id(seed))).getOrThrow();
        assertEquals(id(seed), seedProfile.product());
        assertNotEquals(seedProfile.packet(), id(prototype), "fixed fruit-pit output is distinct from packet identity");
    }

    private static void assertButcherOutput(String source, String output) {
        var raw = BotanyCatalogCandidateTest.resources("item_processing_profile", source).get(id(source));
        var profile = ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow();
        var butcher = profile.butcherable();
        assertNotNull(butcher); assertEquals(ItemProcessingProfile.Butcherable.Type.KNIFE, butcher.type());
        assertEquals(8f, butcher.delaySeconds());
        assertEquals(java.util.List.of(new ItemProcessingProfile.ItemOutput(id(output), 1)), butcher.outputs());
        assertNull(profile.sliceableFood(), "butchery is not knife slicing");
    }

    private static void assertCandidateAbsent(String family, String name) {
        String path = "botany-catalog-candidate/" + family + "/" + name + ".json";
        try (var stream = FruitPitProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNull(stream, "no unsupported candidate composition: " + path);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
