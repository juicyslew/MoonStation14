package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageKernel;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageState;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only bounded food/damage candidate; hat outputs and slicing remain downstream composition. */
class MelonProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void melonCandidatesResolveFoodProduceExtractionAndDamageWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extracts = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var looseResolved = new LinkedHashMap<ResourceLocation, LooseObjectProfile>();
        var food = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources(
                "item_food_solution_profile", "watermelon", "holymelon", "holymelon_slice"));
        var loose = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        loose.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "watermelon", "holymelon"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "watermelon", "holymelon"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "produce_slice_base", "watermelon", "holymelon", "holymelon_slice"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "watermelon", "holymelon", "holymelon_slice"),
                ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_food_solution_profile", "produce_slice_base", "watermelon", "holymelon", "holymelon_slice"),
                ModLooseObjectProfiles.PROFILE_TYPE, loose
        ), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((k,v) -> produce.put(k, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((k,v) -> ingestion.put(k, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((k,v) -> extracts.put(k, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
            catalogs.get(ModLooseObjectProfiles.PROFILE_TYPE.typeId()).forEach((k,v) -> looseResolved.put(k, LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,v).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(id("watermelon"), produce.get(id("watermelon")).seed());
        assertEquals(id("watermelon"), produce.get(id("holymelon")).seed(), "source fallback; carried holymelon genetics are a separate runtime concern");
        assertEquals(1f, produce.get(id("holymelon")).potencyVisuals().minimumScale());
        assertEquals(2f, produce.get(id("holymelon")).potencyVisuals().maximumScale());
        assertEquals(Map.of(id("nutriment"),1000L,id("vitamin"),500L,id("water"),1000L), food.get(id("watermelon")).contentsCents());
        assertEquals(Map.of(id("nutriment"),1000L,id("vitamin"),500L,id("holywater"),1000L), food.get(id("holymelon")).contentsCents());
        assertEquals(2500, food.get(id("watermelon")).capacityCents());
        assertEquals(2500, food.get(id("holymelon")).capacityCents());
        assertEquals(Map.of(id("nutriment"),200L,id("vitamin"),100L,id("holywater"),200L), food.get(id("holymelon_slice")).contentsCents());
        assertEquals(500, food.get(id("holymelon_slice")).capacityCents());
        for (var name : new String[]{"watermelon","holymelon","holymelon_slice"}) {
            assertEquals(IngestionProfile.Kind.FOOD, ingestion.get(id(name)).edible().kind());
            assertEquals(500, ingestion.get(id(name)).edible().doseCents());
            assertEquals(20, ingestion.get(id(name)).edible().delayTicks());
            assertEquals(60, ingestion.get(id(name)).edible().forceFeedDelayTicks());
            assertEquals("food", extracts.get(id(name)).grindableSolutionName());
        }
        assertEquals(Map.of(ModReagents.createKey("juicewatermelon"),2000L), extracts.get(id("watermelon")).juiceSolution().centContents());
        assertEquals(Map.of(ModReagents.createKey("wine"),2000L), extracts.get(id("holymelon")).juiceSolution().centContents());
        assertEquals(Map.of(ModReagents.createKey("wine"),400L), extracts.get(id("holymelon_slice")).juiceSolution().centContents());
        for (var name : new String[]{"watermelon","holymelon"}) {
            var damage = looseResolved.get(id(name)).itemDamage();
            assertEquals(Set.of("brute","burn","toxin","airloss","genetic"), damage.allowedGroups());
            assertEquals(1f, damage.destructionThreshold());
            assertEquals(Map.of("blunt",1f), damage.highSpeedImpact());
            assertEquals(Map.of("blunt",1f), damage.throwLandDamage());
            assertEquals(.1f, damage.minimumImpactSpeed());
            assertEquals(.5f, damage.impactFactor()); assertEquals(40, damage.impactCooldownTicks());
            assertTrue(damage.spillCurrentReagents());
            var impact = ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, Map.of("blunt",1f), damage.allowedGroups(),
                    damage.minimumImpactSpeed(), damage.impactFactor(), damage.impactCooldownTicks(), 0, 1, true, damage.destructionThreshold());
            assertTrue(impact.destructionDue());
        }
        for (var family : new String[]{"produce_profile", "ingestion_profile", "item_food_solution_profile", "extractable_profile", "loose_object_profile"})
            BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, "watermelon", "holymelon", "holymelon_slice");
    }

}
