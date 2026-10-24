package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.*;
import com.juicyslew.moonstation14.ms14.ingestion.*;
import com.juicyslew.moonstation14.ms14.item_processing.*;
import com.juicyslew.moonstation14.ms14.reagent.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BananaProductCatalogCandidateTest {
    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath("moonstation14", s); }
    @Test void bananaCompositionResolvesWithoutPublishingAndKeepsFixedPeelIdentity() {
        var produceRaw = BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "banana");
        var produceManager = new PrototypeManager(); produceManager.register(ModProduceProfiles.TYPE);
        var produceBefore = produceManager.snapshot();
        var produce = new HashMap<ResourceLocation, ProduceProfile>();
        produceManager.stage(Map.of(ModProduceProfiles.TYPE, produceRaw), catalogs -> catalogs.get(ModProduceProfiles.TYPE.typeId())
                .forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(produceBefore, produceManager.snapshot());
        var bananaProduce = produce.get(id("banana"));
        assertEquals(id("banana"), bananaProduce.seed());
        assertEquals(1.0f, bananaProduce.potencyVisuals().minimumScale());
        assertEquals(2.0f, bananaProduce.potencyVisuals().maximumScale());

        var foodSource = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "banana"));
        var solution = foodSource.get(id("banana"));
        assertNotNull(solution);
        assertEquals(600, solution.capacityCents());
        assertEquals(Map.of(id("nutriment"), 400L, id("vitamin"), 200L), solution.contentsCents());

        var ingestionRaw = BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "banana");
        var rejected = new PrototypeManager(); rejected.register(ModIngestionProfiles.TYPE);
        var before = rejected.snapshot();
        var failure = assertThrows(PrototypeLoadException.class, () -> rejected.stage(Map.of(ModIngestionProfiles.TYPE, ingestionRaw), ignored -> {}));
        assertTrue(messages(failure).contains("unknown edible trash item: moonstation14:trash_banana_peel"), messages(failure));
        assertSame(before, rejected.snapshot());
        var ingestionRawResolved = PrototypeResolver.resolve(ingestionRaw, new IngestionProfileMergeStrategy()).asMap();
        var ingestion = IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, ingestionRawResolved.get(id("banana"))).getOrThrow();
        assertEquals(60, ingestion.edible().forceFeedDelayTicks());
        assertEquals(List.of(id("trash_banana_peel")), ingestion.edible().trash());

        var extractRaw = BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "banana");
        var extractManager = new PrototypeManager(); extractManager.register(ModExtractableProfiles.TYPE);
        var extractBefore = extractManager.snapshot();
        var extractionMap = new HashMap<ResourceLocation, ExtractableProfile>();
        extractManager.stage(Map.of(ModExtractableProfiles.TYPE, extractRaw), catalogs -> catalogs.get(ModExtractableProfiles.TYPE.typeId())
                .forEach((key, raw) -> extractionMap.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(extractBefore, extractManager.snapshot());
        var extraction = extractionMap.get(id("banana"));
        assertEquals("food", extraction.grindableSolutionName());
        assertEquals(Map.of(reagent("juicebanana"), 1000L), extraction.juiceSolution().centContents());

        var processing = ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                BotanyCatalogCandidateTest.resources("item_processing_profile", "banana").get(id("banana"))).getOrThrow();
        assertEquals(ItemProcessingProfile.Butcherable.Type.KNIFE, processing.butcherable().type());
        assertEquals(8f, processing.butcherable().delaySeconds());
        assertEquals(List.of(new ItemProcessingProfile.ItemOutput(id("trash_banana_peel"), 1)), processing.butcherable().outputs());
        assertNull(processing.sliceableFood());

        for (var family : List.of("produce_profile", "ingestion_profile", "item_food_solution_profile", "extractable_profile", "item_processing_profile"))
            assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/" + family + "/banana.json"), family);
    }
    @Test void mimanaCompositionResolvesSourceDistinctFoodAndPeelWithoutPublishing() {
        var produceRaw = BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "mimana");
        var produceManager = new PrototypeManager(); produceManager.register(ModProduceProfiles.TYPE);
        var produceBefore = produceManager.snapshot();
        var produce = new HashMap<ResourceLocation, ProduceProfile>();
        produceManager.stage(Map.of(ModProduceProfiles.TYPE, produceRaw), catalogs -> catalogs.get(ModProduceProfiles.TYPE.typeId())
                .forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(produceBefore, produceManager.snapshot());
        var mimana = produce.get(id("mimana"));
        assertEquals(id("mimana"), mimana.seed());
        assertEquals(1.0f, mimana.potencyVisuals().minimumScale());
        assertEquals(2.0f, mimana.potencyVisuals().maximumScale());

        var food = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "mimana")).get(id("mimana"));
        assertEquals(700, food.capacityCents());
        assertEquals(Map.of(id("mutetoxin"), 500L, id("vitamin"), 200L), food.contentsCents());

        var ingestionRaw = BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "mimana");
        var ingestionResolved = PrototypeResolver.resolve(ingestionRaw, new IngestionProfileMergeStrategy()).asMap();
        var ingestion = IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, ingestionResolved.get(id("mimana"))).getOrThrow();
        assertEquals(60, ingestion.edible().forceFeedDelayTicks());
        assertEquals(List.of(id("trash_mimana_peel")), ingestion.edible().trash());

        var extract = BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "mimana");
        var extractManager = new PrototypeManager(); extractManager.register(ModExtractableProfiles.TYPE);
        var extractBefore = extractManager.snapshot();
        var resolvedExtract = new HashMap<ResourceLocation, ExtractableProfile>();
        extractManager.stage(Map.of(ModExtractableProfiles.TYPE, extract), catalogs -> catalogs.get(ModExtractableProfiles.TYPE.typeId())
                .forEach((key, raw) -> resolvedExtract.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(extractBefore, extractManager.snapshot());
        assertEquals(Map.of(reagent("juicebanana"), 1000L, reagent("nothing"), 500L), resolvedExtract.get(id("mimana")).juiceSolution().centContents());

        var processing = ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                BotanyCatalogCandidateTest.resources("item_processing_profile", "mimana").get(id("mimana"))).getOrThrow();
        assertEquals(ItemProcessingProfile.Butcherable.Type.KNIFE, processing.butcherable().type());
        assertEquals(8f, processing.butcherable().delaySeconds());
        assertEquals(List.of(new ItemProcessingProfile.ItemOutput(id("trash_mimana_peel"), 1)), processing.butcherable().outputs());
        assertNull(processing.sliceableFood());
        for (var family : List.of("produce_profile", "ingestion_profile", "item_food_solution_profile", "extractable_profile", "item_processing_profile"))
            assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/" + family + "/mimana.json"), family);
    }
    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String s) {
        return net.minecraft.resources.ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, id(s));
    }
    private static String messages(Throwable e) { var b=new StringBuilder(); for (;e!=null;e=e.getCause()) b.append(e.getMessage()).append('\n'); return b.toString(); }
}
