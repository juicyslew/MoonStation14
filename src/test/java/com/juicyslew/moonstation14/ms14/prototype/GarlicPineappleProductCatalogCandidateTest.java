package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Source mapping: SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91, garlic/pineapple inherit FoodProduceBase -> FoodInjectableBase -> FoodBase -> EdibleBase -> BaseItem. */
class GarlicPineappleProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void garlicAndPineappleResolveFoodProduceAndSliceCompositionWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractables = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "garlic", "pineapple"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "garlic", "pineapple"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "garlic", "pineapple"),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources(),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "garlic", "pineapple")), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractables.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(2, food.size());
        assertEquals(2, extractables.size());
        assertEquals(Map.of(id("nutriment"),1000L,id("vitamin"),400L,id("allicin"),400L), sources.get(id("garlic")).contentsCents());
        assertEquals(1800, sources.get(id("garlic")).capacityCents());
        assertEquals(Map.of(id("nutriment"),1000L,id("vitamin"),200L,id("water"),400L), sources.get(id("pineapple")).contentsCents());
        assertEquals(1600, sources.get(id("pineapple")).capacityCents());
        for (String name : new String[]{"garlic", "pineapple"}) {
            var edible = food.get(id(name)).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind()); assertEquals(500, edible.doseCents());
            assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
            assertTrue(edible.destroyOnEmpty()); assertFalse(edible.consumeSource()); assertFalse(edible.requiresSpecialDigestion());
            var profile = produce.get(id(name)); assertEquals(id(name), profile.seed());
            assertEquals("food", extractables.get(id(name)).grindableSolutionName());
            assertEquals(1f, profile.potencyVisuals().minimumScale()); assertEquals(2f, profile.potencyVisuals().maximumScale());
        }
        assertNull(extractables.get(id("garlic")).juiceSolution());
        assertEquals(Map.of(net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,id("juicepineapple")),1000L), extractables.get(id("pineapple")).juiceSolution().centContents());
        var raw = BotanyCatalogCandidateTest.resources("item_processing_profile", "pineapple").get(id("pineapple"));
        var processing = ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow();
        assertEquals(id("pineapple_slice"), processing.sliceableFood().output());
        assertEquals(5, processing.sliceableFood().count()); assertEquals(1f, processing.sliceableFood().delaySeconds());
        // Deliberately codec-only: pineapple_slice is not registered yet; registry-backed validation/runtime binding awaits the output item.
    }
}
