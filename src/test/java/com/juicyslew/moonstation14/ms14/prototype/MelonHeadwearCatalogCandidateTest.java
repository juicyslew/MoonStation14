package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.damage.ArmorModifiers;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only melon headwear composition. Source: SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91. */
class MelonHeadwearCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void hatsResolveTypedHeadwearFoodAndArmorCompositionWithoutPublication() throws Exception {
        var manager = new PrototypeManager();
        manager.register(ModEquipmentItems.TYPE);
        manager.register(ModInventoryTemplates.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        var before = manager.snapshot();
        var equipment = new LinkedHashMap<ResourceLocation, JsonObject>();
        equipment.put(id("base_item"), resource("data/moonstation14/moonstation14/equipment_item/base_item.json"));
        equipment.put(id("base_clothing"), resource("data/moonstation14/moonstation14/equipment_item/base_clothing.json"));
        equipment.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "clothing_head_base",
                "clothing_head_hat_watermelon", "clothing_head_hat_holy_watermelon"));
        var templates = new LinkedHashMap<ResourceLocation, JsonObject>();
        templates.put(ModInventoryTemplates.HUMAN_ID,
                resource("data/moonstation14/moonstation14/inventory_template/human.json"));

        manager.stage(Map.of(
                ModEquipmentItems.TYPE, equipment,
                ModInventoryTemplates.TYPE, templates,
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base",
                        "clothing_head_base", "clothing_head_hat_watermelon", "clothing_head_hat_holy_watermelon"),
                ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_food_solution_profile",
                        "clothing_head_base", "clothing_head_hat_watermelon", "clothing_head_hat_holy_watermelon")
        ), catalogs -> {
            var resolvedEquipment = catalogs.get(ModEquipmentItems.TYPE.typeId());
            var template = catalogs.get(ModInventoryTemplates.TYPE.typeId()).get(ModInventoryTemplates.HUMAN_ID);
            var decodedTemplate = InventoryTemplateData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, template).getOrThrow();
            var hatIds = Set.of("clothing_head_hat_watermelon", "clothing_head_hat_holy_watermelon");
            var decodedEquipment = new LinkedHashMap<ResourceLocation, EquipmentItemData>();
            resolvedEquipment.forEach((key, raw) -> decodedEquipment.put(key,
                    EquipmentItemData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> ingestion.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            var solutions = new LinkedHashMap<ResourceLocation, ItemFoodSolutionProfile>();
            catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).forEach((key, raw) -> solutions.put(key,
                    ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));

            // The shared base is abstract and therefore intentionally absent from the manager's concrete snapshot.
            var headBase = EquipmentItemData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    BotanyCatalogCandidateTest.resources("equipment_item", "clothing_head_base").get(id("clothing_head_base"))).getOrThrow();
            assertEquals("Small", headBase.item().size());
            assertEquals(Set.of(InventoryTemplateData.SlotFlag.HEAD), Set.copyOf(headBase.clothing().orElseThrow().slotFlags()));
            assertEquals(EquipmentItemData.AppearanceRegion.HEAD, headBase.clothing().orElseThrow().region());
            assertTrue(headBase.clothing().orElseThrow().quickEquip());
            assertTrue(headBase.clothing().orElseThrow().wornModel().isEmpty(), "no unreviewed placeholder worn model");
            assertTrue(EquipmentFitPolicy.fits(decodedTemplate, "head", headBase, Set.of(), false));

            var headSolution = com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile.CODEC.parse(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    BotanyCatalogCandidateTest.resources("item_food_solution_profile", "clothing_head_base").get(id("clothing_head_base"))).getOrThrow();
            assertEquals(Map.of(id("fiber"), 1000L), headSolution.contentsCents());
            assertEquals(1000, headSolution.capacityCents());

            var restricted = ingestion.get(id("clothing_head_hat_watermelon")).edible();
            assertEquals(IngestionProfile.Kind.FOOD, restricted.kind());
            assertEquals(500, restricted.doseCents());
            assertEquals(20, restricted.delayTicks());
            assertEquals(60, restricted.forceFeedDelayTicks());
            assertTrue(restricted.requiresSpecialDigestion());
            // food_base is only the local shared policy/default parent. Upstream headwear inherits Edible directly.

            var watermelon = decodedEquipment.get(id("clothing_head_hat_watermelon"));
            var holy = decodedEquipment.get(id("clothing_head_hat_holy_watermelon"));
            assertEquals(Map.of("blunt", .95f), watermelon.armor().orElseThrow().coefficients());
            assertEquals(Map.of("caustic", .95f), holy.armor().orElseThrow().coefficients());
            assertEquals(Map.of("blunt", 9.5f, "caustic", 10f), ArmorModifiers.apply(
                    Map.of("blunt", 10f, "caustic", 10f), java.util.List.of(watermelon.armor().orElseThrow())));
            assertEquals(Map.of("blunt", 10f, "caustic", 9.5f), ArmorModifiers.apply(
                    Map.of("blunt", 10f, "caustic", 10f), java.util.List.of(holy.armor().orElseThrow())));
            for (String name : hatIds) {
                var key = id(name);
                assertTrue(EquipmentFitPolicy.fits(decodedTemplate, "head", decodedEquipment.get(key), Set.of(), false), name);
                assertEquals(restricted, ingestion.get(key).edible());
                assertEquals(headSolution, solutions.get(key));
                assertTrue(decodedEquipment.get(key).clothing().orElseThrow().wornModel().isEmpty());
                for (String family : new String[]{"ingestion_profile", "item_food_solution_profile", "produce_profile", "seed_profile", "extractable_profile", "item_processing_profile", "equipment_item"})
                    BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, name);
            }
            for (String family : new String[]{"ingestion_profile", "item_food_solution_profile", "equipment_item"})
                BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, "clothing_head_base");
        });
        assertSame(before, manager.snapshot(), "staging candidate output must not publish");
    }

    private static JsonObject resource(String path) {
        try (var stream = MelonHeadwearCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

}
