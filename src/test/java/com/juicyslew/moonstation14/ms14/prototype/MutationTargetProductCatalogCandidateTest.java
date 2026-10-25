package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Nonproduction candidate mapping against actual main tomato/poppy baseline profiles. */
class MutationTargetProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static Map<ResourceLocation, com.google.gson.JsonObject> combine(Map<ResourceLocation, com.google.gson.JsonObject>... maps) {
        var out = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (var map : maps) out.putAll(map);
        return out;
    }

    @Test void mutationProductsResolveAsAtomicFoodAndJuiceCandidateCatalogs() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModSeedProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extracts = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var sourceResources = combine(BotanyCatalogCandidateTest.mainResources("item_food_solution_profile", "tomato", "poppy", "food_meat_wheat", "meatwheat_bushel"),
                BotanyCatalogCandidateTest.resources("item_food_solution_profile", "blue_tomato", "blood_tomato", "lily"));
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(sourceResources);
        var produceNames = new String[]{"meatwheat_bushel", "blue_tomato", "blood_tomato", "lily"};
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, combine(BotanyCatalogCandidateTest.resources("produce_profile", "produce_base"), BotanyCatalogCandidateTest.mainResources("produce_profile", "tomato", "poppy", "meatwheat_bushel"), BotanyCatalogCandidateTest.resources("produce_profile", "blue_tomato", "blood_tomato", "lily")),
                ModIngestionProfiles.TYPE, combine(BotanyCatalogCandidateTest.resources("ingestion_profile", "ruminant_edible_base"), BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base", "tomato", "poppy", "meatwheat_bushel"), BotanyCatalogCandidateTest.resources("ingestion_profile", "blue_tomato", "blood_tomato", "lily")),
                ModSeedProfiles.TYPE, combine(BotanyCatalogCandidateTest.resources(), BotanyCatalogCandidateTest.mainResources("seed_profile", "tomato", "poppy")),
                ModExtractableProfiles.TYPE, combine(BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base"), BotanyCatalogCandidateTest.mainResources("extractable_profile", "tomato", "poppy", "meatwheat_bushel"), BotanyCatalogCandidateTest.resources("extractable_profile", "blue_tomato", "blood_tomato", "lily"))), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extracts.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            assertEquals(72, seeds.size());
            assertEquals(71, seeds.keySet().stream().filter(key -> !key.equals(id("defaults"))).count());
        });
        assertSame(before, manager.snapshot());
        assertEquals(6, produce.size()); assertEquals(6, food.size()); assertEquals(6, extracts.size());
        assertEquals(id("meatwheat_bushel"), seeds.get(id("meatwheat")).product());
        for (String key : new String[]{"blue_tomato", "blood_tomato", "lily"}) assertEquals(id(key), seeds.get(id(key)).product());
        assertEquals(Map.of(id("nutriment"),700L,id("vitamin"),300L,id("water"),400L), sources.get(id("tomato")).contentsCents());
        assertEquals(Map.of(id("nutriment"),200L,id("bicaridine"),2000L), sources.get(id("poppy")).contentsCents());
        assertEquals(Map.of(id("nutriment"),500L,id("spacelube"),1500L,id("vitamin"),400L), sources.get(id("blue_tomato")).contentsCents());
        assertEquals(Map.of(id("blood"),1000L,id("vitamin"),400L), sources.get(id("blood_tomato")).contentsCents());
        assertEquals(2400, sources.get(id("blue_tomato")).capacityCents());
        assertEquals(1400, sources.get(id("blood_tomato")).capacityCents());
        assertEquals(food.get(id("poppy")).edible(), food.get(id("lily")).edible());
        for (String name : new String[]{"meatwheat_bushel", "blue_tomato", "blood_tomato", "lily"}) {
            assertEquals(id(name.equals("meatwheat_bushel") ? "meatwheat" : name), produce.get(id(name)).seed());
            assertEquals(1f, produce.get(id(name)).potencyVisuals().minimumScale());
            assertEquals(2f, produce.get(id(name)).potencyVisuals().maximumScale());
            var edible = food.get(id(name)).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind()); assertEquals(500, edible.doseCents());
            assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
            assertTrue(edible.destroyOnEmpty()); assertTrue(edible.trash().isEmpty());
            assertEquals(name.equals("meatwheat_bushel"), edible.requiresSpecialDigestion());
        }
        assertEquals(1000, sources.get(id("meatwheat_bushel")).capacityCents());
        assertEquals(Map.of(id("uncookedanimalproteins"),1000L), sources.get(id("meatwheat_bushel")).contentsCents());
        assertEquals(2200, sources.get(id("lily")).capacityCents());
        assertEquals(Map.of(id("nutriment"),200L,id("bicaridine"),2000L), sources.get(id("lily")).contentsCents());
        assertNull(extracts.get(id("meatwheat_bushel")).juiceSolution()); assertNull(extracts.get(id("lily")).juiceSolution());
        assertEquals(Map.of(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,id("spacelube")),1000L), extracts.get(id("blue_tomato")).juiceSolution().centContents());
        assertEquals(Map.of(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,id("blood")),1000L), extracts.get(id("blood_tomato")).juiceSolution().centContents());
        assertFalse(extracts.get(id("blue_tomato")).juiceSolution().centContents().containsKey(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,id("juicetomato"))));
        assertFalse(extracts.get(id("blood_tomato")).juiceSolution().centContents().containsKey(ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,id("juicetomato"))));
        for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile", "item_food_solution_profile"})
            for (String name : new String[]{"meatwheat_bushel", "blue_tomato", "blood_tomato", "lily"})
                if (name.equals("meatwheat_bushel")) assertNotNull(BotanyCatalogCandidateTest.class.getClassLoader().getResource("data/moonstation14/moonstation14/" + family + "/" + name + ".json"));
                else assertNull(BotanyCatalogCandidateTest.class.getClassLoader().getResource("data/moonstation14/moonstation14/" + family + "/" + name + ".json"));
    }

    @Test void mainPoppyHeadwearAndTestLilyInheritanceResolveWithoutPublishing() {
        var manager = new PrototypeManager(); manager.register(ModEquipmentItems.TYPE);
        var before = manager.snapshot();
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        raw.putAll(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_item", "base_clothing", "grey_cap", "poppy"));
        raw.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "lily"));
        var decoded = new LinkedHashMap<ResourceLocation, EquipmentItemData>();
        manager.stage(Map.of(ModEquipmentItems.TYPE, raw), catalogs ->
                catalogs.get(ModEquipmentItems.TYPE.typeId()).forEach((key, value) -> decoded.put(key,
                        EquipmentItemData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow())));
        assertSame(before, manager.snapshot());
        var poppy = decoded.get(id("poppy")); var lily = decoded.get(id("lily"));
        assertNotNull(poppy); assertNotNull(lily);
        assertEquals("Small", poppy.item().size());
        assertEquals(java.util.List.of(InventoryTemplateData.SlotFlag.HEAD), poppy.clothing().orElseThrow().slotFlags());
        assertEquals(EquipmentItemData.AppearanceRegion.HEAD, poppy.clothing().orElseThrow().region());
        assertFalse(poppy.clothing().orElseThrow().quickEquip());
        assertTrue(poppy.clothing().orElseThrow().wornModel().isEmpty());
        assertEquals(poppy, lily, "Lily inherits MAIN Poppy equipment profile through resolver");
        var templateRaw = BotanyCatalogCandidateTest.mainResources("inventory_template", "human").get(ModInventoryTemplates.HUMAN_ID);
        var template = InventoryTemplateData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, templateRaw).getOrThrow();
        assertTrue(EquipmentFitPolicy.fits(template, "head", poppy, java.util.Set.of(), false));
        assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/equipment_item/lily.json"));
    }
}
