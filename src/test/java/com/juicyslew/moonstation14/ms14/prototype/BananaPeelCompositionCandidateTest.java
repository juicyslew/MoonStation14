package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only composition mapping to TrashBananaPeel; this is not gameplay activation. */
class BananaPeelCompositionCandidateTest {
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("moonstation14", value); }

    @Test void peelResolvesSupportedHeadwearFoodAndSlipMetadataWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModEquipmentItems.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var before = manager.snapshot();
        var equipment = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_item"));
        equipment.putAll(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_clothing"));
        equipment.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "clothing_head_base", "trash_banana_peel"));
        var loose = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        loose.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "trash_banana_peel"));
        manager.stage(Map.of(
                ModEquipmentItems.TYPE, equipment,
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "clothing_head_base", "trash_banana_peel"),
                ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_food_solution_profile", "trash_banana_peel"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "trash_banana_peel"),
                ModLooseObjectProfiles.PROFILE_TYPE, loose
        ), catalogs -> {
            var rawEquipment = catalogs.get(ModEquipmentItems.TYPE.typeId()).get(id("trash_banana_peel"));
            var item = EquipmentItemData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, rawEquipment).getOrThrow();
            assertEquals("Small", item.item().size());
            assertEquals(java.util.Set.of(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.HEAD),
                    java.util.Set.copyOf(item.clothing().orElseThrow().slotFlags()));
            assertFalse(item.clothing().orElseThrow().quickEquip());
            assertTrue(item.clothing().orElseThrow().wornModel().isEmpty());

            var ingestion = IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModIngestionProfiles.TYPE.typeId()).get(id("trash_banana_peel"))).getOrThrow();
            assertTrue(ingestion.edible().requiresSpecialDigestion());
            assertEquals(20, ingestion.edible().delayTicks());
            assertEquals(60, ingestion.edible().forceFeedDelayTicks());
            // TimedIngestionAccess intentionally denies this once the candidate is runtime-bound;
            // staging remains unpublished, so assert the typed restriction here without binding it.

            var solution = ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).get(id("trash_banana_peel"))).getOrThrow();
            assertEquals(400, solution.capacityCents());
            assertEquals(Map.of(id("pulped_banana_peel"), 400L), solution.contentsCents());
            var extract = ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModExtractableProfiles.TYPE.typeId()).get(id("trash_banana_peel"))).getOrThrow();
            assertEquals("food", extract.grindableSolutionName());
            assertNull(extract.juiceSolution());

            var rawLoose = catalogs.get(ModLooseObjectProfiles.PROFILE_TYPE.typeId()).get(id("trash_banana_peel"));
            var peel = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, rawLoose).getOrThrow();
            assertEquals(.4f, peel.physicalWidth());
            assertEquals(.2d, peel.stepTrigger().intersectionRatio());
            assertEquals(3.5d, peel.stepTrigger().requiredSpeed());
            assertEquals(.5d, peel.slippery().stunSeconds());
            assertEquals(25d, peel.slippery().staminaDamage());
            assertFalse(peel.slippery().affectsSliding());
            assertEquals("moonstation14:ss14/effects/slip", peel.slippery().soundEvent().toString());
        });
        assertSame(before, manager.snapshot());
        for (var family : new String[]{"equipment_item", "ingestion_profile", "item_food_solution_profile", "extractable_profile", "loose_object_profile", "produce_profile", "seed_profile"})
            BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, "trash_banana_peel");
    }

    @Test void mimanaPeelInheritsBananaCompositionAndOverridesOnlySlipVolumeWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModEquipmentItems.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var before = manager.snapshot();
        var equipment = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_item"));
        equipment.putAll(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_clothing"));
        equipment.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "clothing_head_base", "trash_banana_peel", "trash_mimana_peel"));
        var loose = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        loose.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "trash_banana_peel", "trash_mimana_peel"));
        manager.stage(Map.of(
                ModEquipmentItems.TYPE, equipment,
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "clothing_head_base", "trash_banana_peel", "trash_mimana_peel"),
                ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_food_solution_profile", "trash_banana_peel", "trash_mimana_peel"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "trash_banana_peel", "trash_mimana_peel"),
                ModLooseObjectProfiles.PROFILE_TYPE, loose
        ), catalogs -> {
            var banana = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModLooseObjectProfiles.PROFILE_TYPE.typeId()).get(id("trash_banana_peel"))).getOrThrow();
            var mimana = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModLooseObjectProfiles.PROFILE_TYPE.typeId()).get(id("trash_mimana_peel"))).getOrThrow();
            assertEquals(0d, banana.slippery().soundVolumeDb());
            assertEquals(-100d, mimana.slippery().soundVolumeDb());
            assertEquals(banana.physicalWidth(), mimana.physicalWidth());
            assertEquals(banana.stepTrigger(), mimana.stepTrigger());
            var baseResponse = banana.slippery();
            var childResponse = mimana.slippery();
            assertEquals(baseResponse.stunSeconds(), childResponse.stunSeconds());
            assertEquals(baseResponse.staminaDamage(), childResponse.staminaDamage());
            assertEquals(baseResponse.soundEvent(), childResponse.soundEvent());
            var bananaFood = ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).get(id("trash_banana_peel"))).getOrThrow();
            var mimanaFood = ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).get(id("trash_mimana_peel"))).getOrThrow();
            assertEquals(bananaFood, mimanaFood);
            var bananaExtract = ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModExtractableProfiles.TYPE.typeId()).get(id("trash_banana_peel"))).getOrThrow();
            var mimanaExtract = ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModExtractableProfiles.TYPE.typeId()).get(id("trash_mimana_peel"))).getOrThrow();
            assertEquals(bananaExtract, mimanaExtract);
        });
        assertSame(before, manager.snapshot());
        for (var family : new String[]{"equipment_item", "ingestion_profile", "item_food_solution_profile", "extractable_profile", "loose_object_profile", "produce_profile", "seed_profile"})
            BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, "trash_mimana_peel");
    }

    private static void assertMainAbsent(String family, String name) {
        var path = "data/moonstation14/moonstation14/" + family + "/" + name + ".json";
        try (var stream = BananaPeelCompositionCandidateTest.class.getClassLoader().getResourceAsStream(path)) { assertNull(stream, path); }
        catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
