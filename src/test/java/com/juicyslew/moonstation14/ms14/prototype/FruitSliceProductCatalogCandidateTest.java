package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FruitSliceProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void fruitSlicesResolveIndependentFoodAndExtractionCompositionWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE);
        var before = manager.snapshot();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources(
                "item_food_solution_profile", "produce_slice_base", "pineapple_slice", "watermelon_slice"));
        manager.stage(Map.of(
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "produce_slice_base", "pineapple_slice", "watermelon_slice"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "pineapple_slice", "watermelon_slice")
        ), catalogs -> {
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key,
                    ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });

        assertSame(before, manager.snapshot(), "staging candidate data must not publish it");
        assertEquals(2, food.size());
        assertEquals(2, extractables.size());
        assertEquals(2, sources.size(), "abstract base is excluded from resolved profiles");
        assertFalse(sources.containsKey(id("produce_slice_base")));

        var pineapple = food.get(id("pineapple_slice")).edible();
        assertEquals(IngestionProfile.Kind.FOOD, pineapple.kind());
        var pineappleSource = sources.get(id("pineapple_slice"));
        assertEquals(Map.of(id("nutriment"), 200L), pineappleSource.contentsCents());
        assertEquals(300, pineappleSource.capacityCents());
        assertEquals(100, pineappleSource.capacityCents() - pineappleSource.contentsCents().values().stream().mapToInt(Long::intValue).sum());
        assertEdibleDefaults(pineapple);

        var watermelon = food.get(id("watermelon_slice")).edible();
        assertEquals(IngestionProfile.Kind.FOOD, watermelon.kind());
        assertEquals(Map.of(id("nutriment"), 200L, id("vitamin"), 100L, id("water"), 200L), sources.get(id("watermelon_slice")).contentsCents());
        assertEquals(500, sources.get(id("watermelon_slice")).capacityCents());
        assertEdibleDefaults(watermelon);

        for (String name : new String[]{"pineapple_slice", "watermelon_slice"})
            assertEquals("food", extractables.get(id(name)).grindableSolutionName(), name);
        assertEquals(Map.of(ModReagents.createKey("juicepineapple"), 200L), extractables.get(id("pineapple_slice")).juiceSolution().centContents());
        assertEquals(Map.of(ModReagents.createKey("juicewatermelon"), 400L), extractables.get(id("watermelon_slice")).juiceSolution().centContents());

        for (String family : new String[]{"ingestion_profile", "extractable_profile", "produce_profile", "item_food_solution_profile"}) {
            for (String name : new String[]{"pineapple_slice", "watermelon_slice"}) {
                String path = "data/moonstation14/moonstation14/" + family + "/" + name + ".json";
                try (var resource = getClass().getClassLoader().getResourceAsStream(path)) {
                    assertNull(resource, "candidate data must not be registered as production data: " + path);
                } catch (java.io.IOException e) {
                    throw new AssertionError("Could not close resource stream: " + path, e);
                }
            }
        }
    }

    private static void assertEdibleDefaults(IngestionProfile.Edible edible) {
        assertEquals(500, edible.doseCents());
        assertEquals(20, edible.delayTicks());
        assertEquals(60, edible.forceFeedDelayTicks());
        assertFalse(edible.requiresSpecialDigestion());
        assertTrue(edible.trash().isEmpty());
    }
}
