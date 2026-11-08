package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only Pumpkin composition; carved-item light/construction behavior is unsupported locally. */
class PumpkinProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void pumpkinResolvesInheritedFoodProduceExtractionAndButcheringCompositionWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE);
        manager.register(ModItemProcessingProfiles.TYPE);
        var before = manager.snapshot();
        var food = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "pumpkin"));
        var produceResources = BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "pumpkin");
        var ingestionResources = BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "pumpkin");
        var extractResources = BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "pumpkin");
        var looseResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        looseResources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "pumpkin"));
        var equipmentResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_item"));
        equipmentResources.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "carved_pumpkin"));
        var processingResources = BotanyCatalogCandidateTest.resources("item_processing_profile", "pumpkin");
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extract = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        manager.stage(Map.of(ModProduceProfiles.TYPE, produceResources, ModIngestionProfiles.TYPE, ingestionResources,
                ModExtractableProfiles.TYPE, extractResources, ModItemFoodSolutionProfiles.TYPE,
                BotanyCatalogCandidateTest.resources("item_food_solution_profile", "pumpkin"),
                ModLooseObjectProfiles.PROFILE_TYPE, looseResources,
                com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, equipmentResources), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v) -> produce.put(k, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((k,v) -> ingestion.put(k, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((k,v) -> extract.put(k, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(id("pumpkin"), produce.get(id("pumpkin")).seed());
        assertEquals(1f, produce.get(id("pumpkin")).potencyVisuals().minimumScale());
        assertEquals(2f, produce.get(id("pumpkin")).potencyVisuals().maximumScale());
        assertEquals(2500, food.get(id("pumpkin")).capacityCents());
        assertEquals(Map.of(id("pumpkinflesh"), 2000L, id("vitamin"), 500L), food.get(id("pumpkin")).contentsCents());
        assertEquals(IngestionProfile.Kind.FOOD, ingestion.get(id("pumpkin")).edible().kind());
        assertEquals(500, ingestion.get(id("pumpkin")).edible().doseCents());
        assertEquals("food", extract.get(id("pumpkin")).grindableSolutionName());
        assertNull(extract.get(id("pumpkin")).juiceSolution(), "FoodPumpkin has no source juiceSolution");
        var processing = ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                PrototypeResolver.resolve(processingResources, null).asMap().get(id("pumpkin"))).getOrThrow();
        assertEquals(ItemProcessingProfile.Butcherable.Type.KNIFE, processing.butcherable().type());
        assertEquals(8f, processing.butcherable().delaySeconds());
        assertEquals(java.util.List.of(new ItemProcessingProfile.ItemOutput(id("carved_pumpkin"), 1),
                new ItemProcessingProfile.ItemOutput(id("pumpkin_seeds"), 1)), processing.butcherable().outputs());
        assertNull(processing.sliceableFood(), "Pumpkin has no slicing action");
        var activatedProcessing = new LinkedHashMap<ResourceLocation, ItemProcessingProfile>();
        manager.stage(Map.of(ModItemProcessingProfiles.TYPE, processingResources), catalogs ->
                catalogs.get(ModItemProcessingProfiles.TYPE.typeId()).forEach((key, raw) -> activatedProcessing.put(key,
                        ItemProcessingProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertEquals(id("carved_pumpkin"), activatedProcessing.get(id("pumpkin")).butcherable().outputs().get(0).output());
        var invalidProcessing = new LinkedHashMap<>(processingResources);
        invalidProcessing.get(id("pumpkin")).getAsJsonObject("butcherable").getAsJsonArray("outputs").get(0)
                .getAsJsonObject().addProperty("output", "moonstation14:synthetic_missing_output");
        assertThrows(com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException.class,
                () -> manager.stage(Map.of(ModItemProcessingProfiles.TYPE, invalidProcessing), ignored -> { }));
        assertSame(before, manager.snapshot(), "candidate staging does not publish");
        var carved = equipmentResources.get(id("carved_pumpkin"));
        assertNotNull(carved, "carved pumpkin is item metadata, not clothing or armor");
        for (var family : new String[]{"produce_profile", "ingestion_profile", "item_food_solution_profile", "extractable_profile", "loose_object_profile", "equipment_item"})
            BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, "pumpkin", "carved_pumpkin");
        assertNull(BotanyCatalogCandidateTest.class.getClassLoader().getResource("botany-catalog-candidate/item_food_solution_profile/pumpkin_seeds.json"),
                "reuse the existing pumpkin seed profile; do not add a duplicate");
    }

}
