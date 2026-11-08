package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MeatwheatRuntimeCompositionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void resolvedMainSeedAndBushelCompositionReachRealRuntimeConsumers() {
        var manager = new PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        manager.register(ModSeedItemProfiles.TYPE);
        manager.register(ModProduceProfiles.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE);
        Map<com.juicyslew.moonstation14.ms14.prototype.PrototypeType<?>, Map<ResourceLocation, JsonObject>> sources = new LinkedHashMap<>();
        sources.put(ModSeedProfiles.TYPE, BotanyTestSeedFixtures.mainCatalog());
        sources.put(ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("produce_profile", "produce_base", "meatwheat_bushel"));
        sources.put(ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base", "meatwheat_bushel", "food_meat_wheat"));
        sources.put(ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("extractable_profile", "extractable_base", "meatwheat_bushel"));
        sources.put(ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("item_food_solution_profile", "meatwheat_bushel", "food_meat_wheat"));
        sources.put(ModSeedItemProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("seed_item_profile", "meatwheat_seeds"));
        sources.put(com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE,
                BotanyCatalogCandidateTest.mainResources("item_processing_profile", "meatwheat_bushel"));
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extraction = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var food = new LinkedHashMap<ResourceLocation, ItemFoodSolutionProfile>();
        var seedItems = new LinkedHashMap<ResourceLocation, SeedItemProfile>();
        var processing = new LinkedHashMap<ResourceLocation, com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile>();
        manager.stage(sources, catalogs -> {
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((k,v) -> seeds.put(k, SeedProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v) -> produce.put(k, ProduceProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((k,v) -> ingestion.put(k, IngestionProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((k,v) -> extraction.put(k, ExtractableProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).forEach((k,v) -> food.put(k, ItemFoodSolutionProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModSeedItemProfiles.TYPE.typeId()).forEach((k,v) -> seedItems.put(k, SeedItemProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE.typeId()).forEach((k,v) -> processing.put(k, com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow()));
        });

        var seed = seeds.get(id("meatwheat"));
        assertNotNull(seed);
        assertEquals(id("meatwheat_seeds"), seed.packet());
        assertEquals(id("meatwheat_bushel"), seed.product());
        assertEquals("seeds-meatwheat-name", seed.sourceName());
        assertEquals(3, seed.lifecycle().yield());
        assertEquals(5, seed.lifecycle().potency());
        assertEquals(1f, seed.chemicals().get(id("nutriment")).min());
        assertEquals(20f, seed.chemicals().get(id("nutriment")).max());
        assertEquals(5f, seed.chemicals().get(id("uncookedanimalproteins")).min());
        assertEquals(20f, seed.chemicals().get(id("uncookedanimalproteins")).max());
        assertEquals(id("meatwheat"), produce.get(id("meatwheat_bushel")).seed());
        assertEquals(.5f, produce.get(id("meatwheat_bushel")).potencyVisuals().minimumScale());
        assertEquals(4.5f, produce.get(id("meatwheat_bushel")).potencyVisuals().maximumScale());
        assertNotNull(extraction.get(id("meatwheat_bushel")));
        assertEquals(1000, food.get(id("meatwheat_bushel")).capacityCents());
        assertEquals(20, ingestion.get(id("meatwheat_bushel")).edible().delayTicks());
        assertEquals(60, ingestion.get(id("meatwheat_bushel")).edible().forceFeedDelayTicks());
        assertTrue(ingestion.get(id("meatwheat_bushel")).edible().requiresSpecialDigestion());
        assertEquals(id("meatwheat"), seedItems.get(id("meatwheat_seeds")).seedId());
        var conversion = processing.get(id("meatwheat_bushel")).spawnItemsOnUse();
        assertNotNull(conversion);
        assertEquals(1, conversion.uses());
        assertEquals(id("food_meat_wheat"), conversion.outputs().getFirst().output());
        assertEquals(1, conversion.outputs().getFirst().count());

        var runtime = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var previousSeeds = runtime.snapshot(ModSeedProfiles.TYPE);
        var previousSeedItems = runtime.snapshot(ModSeedItemProfiles.TYPE);
        var previousProduce = runtime.snapshot(ModProduceProfiles.TYPE);
        var previousIngestion = runtime.snapshot(ModIngestionProfiles.TYPE);
        var previousExtraction = runtime.snapshot(ModExtractableProfiles.TYPE);
        var previousFood = runtime.snapshot(ModItemFoodSolutionProfiles.TYPE);
        var previousProcessing = runtime.snapshot(com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE);
        try {
        runtime.publishDecoded(ModSeedProfiles.TYPE, seeds);
        runtime.publishDecoded(ModSeedItemProfiles.TYPE, seedItems);
        runtime.publishDecoded(ModProduceProfiles.TYPE, produce);
        runtime.publishDecoded(ModIngestionProfiles.TYPE, ingestion);
        runtime.publishDecoded(ModExtractableProfiles.TYPE, extraction);
        runtime.publishDecoded(ModItemFoodSolutionProfiles.TYPE, food);
        runtime.publishDecoded(com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE, processing);
        var genetics = new GeneticValue(id("meatwheat"), seed);
        var bushel = ProduceFactory.createHarvest(genetics, seed.lifecycle().potency());
        assertSame(ModItems.MEATWHEAT_BUSHEL.get(), bushel.getItem());
        assertEquals(genetics, bushel.get(ModDataComponents.GENETIC_VALUE.get()));
        assertEquals(ProduceFactory.generatedChemistry(seed, 5), bushel.get(ModDataComponents.REAGENT.get()).centContents());
        assertEquals(id("meatwheat"), bushel.get(ModDataComponents.PRODUCE_INSTANCE.get()).seed());

        var packet = GeneticCarriers.createSeedPacket(new PlantSeedReference(genetics, true), null);
        assertSame(ModItems.MEATWHEAT_SEEDS.get(), packet.getItem());
        assertEquals(genetics, GeneticCarriers.resolveSeedPacket(packet).genetics());

        var clump = new ItemStack(ModItems.FOOD_MEAT_WHEAT.get());
        assertNull(clump.get(ModDataComponents.PRODUCE_INSTANCE.get()));
        assertNull(clump.get(ModDataComponents.GENETIC_VALUE.get()));
        assertNull(clump.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
        var clumpFood = ProduceSourceComposition.resolve(ModItems.FOOD_MEAT_WHEAT.get(), null);
        assertNotNull(clumpFood);
        assertEquals(Map.of(id("uncookedanimalproteins"), 100L), clumpFood.contentsCents());
        } finally {
            runtime.publishDecoded(ModSeedProfiles.TYPE, previousSeeds.asMap());
            runtime.publishDecoded(ModSeedItemProfiles.TYPE, previousSeedItems.asMap());
            runtime.publishDecoded(ModProduceProfiles.TYPE, previousProduce.asMap());
            runtime.publishDecoded(ModIngestionProfiles.TYPE, previousIngestion.asMap());
            runtime.publishDecoded(ModExtractableProfiles.TYPE, previousExtraction.asMap());
            runtime.publishDecoded(ModItemFoodSolutionProfiles.TYPE, previousFood.asMap());
            runtime.publishDecoded(com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles.TYPE, previousProcessing.asMap());
        }
    }
}
