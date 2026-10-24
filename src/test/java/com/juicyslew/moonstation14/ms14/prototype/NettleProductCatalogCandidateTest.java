package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfileMergeStrategy;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NettleProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void nettlesResolveFullProductCompositionsWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModIngestionProfiles.TYPE); manager.register(ModItemFootprints.TYPE);
        var before = manager.snapshot();

        var produce = PrototypeResolver.resolve(BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "nettle", "death_nettle"), null).asMap();
        assertEquals(id("nettle"), ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, produce.get(id("nettle"))).getOrThrow().seed());
        assertEquals(id("death_nettle"), ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, produce.get(id("death_nettle"))).getOrThrow().seed());

        var food = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "nettle", "death_nettle"));
        assertEquals(300, food.get(id("nettle")).capacityCents()); assertFalse(food.get(id("nettle")).refillable());
        assertEquals(3000, food.get(id("death_nettle")).capacityCents()); assertFalse(food.get(id("death_nettle")).refillable());
        assertEquals(Map.of(id("sulfuricacid"), 1500L, id("fluorosulfuricacid"), 1500L), food.get(id("death_nettle")).contentsCents());

        var extract = PrototypeResolver.resolve(BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "nettle", "death_nettle"), null).asMap();
        assertEquals("food", ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, extract.get(id("nettle"))).getOrThrow().grindableSolutionName());
        assertEquals("food", ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, extract.get(id("death_nettle"))).getOrThrow().grindableSolutionName());
        var ingestionRaw = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base"));
        ingestionRaw.putAll(BotanyCatalogCandidateTest.resources("ingestion_profile", "ruminant_edible_base"));
        ingestionRaw.putAll(BotanyCatalogCandidateTest.resources("ingestion_profile", "nettle"));
        var ingestion = PrototypeResolver.resolve(ingestionRaw, new IngestionProfileMergeStrategy()).asMap();
        assertEquals(IngestionProfile.Kind.FOOD, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, ingestion.get(id("nettle"))).getOrThrow().edible().kind());
        assertFalse(ingestion.containsKey(id("death_nettle")));

        var looseRaw = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        looseRaw.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "nettle", "death_nettle"));
        var loose = PrototypeResolver.resolve(looseRaw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow()).asMap();
        var nettle = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, loose.get(id("nettle"))).getOrThrow();
        var death = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, loose.get(id("death_nettle"))).getOrThrow();
        assertNull(nettle.itemDamage()); assertNull(nettle.damageOnInteract());
        assertEquals(5f, death.itemDamage().highSpeedImpact().get("blunt"));
        assertEquals(20f, death.itemDamage().minimumImpactSpeed()); assertEquals(.5f, death.itemDamage().impactFactor());
        assertEquals(40, death.itemDamage().impactCooldownTicks()); assertEquals(25f, death.itemDamage().destructionThreshold());
        assertEquals(java.util.Set.of("brute"), death.itemDamage().allowedGroups());
        assertEquals(java.util.Set.of("heat", "shock"), death.itemDamage().allowedTypes());
        assertEquals(id("ss14/voice/diona/salute"), death.itemDamage().destructionSound());
        assertEquals(-5f, death.itemDamage().destructionSoundVolumeDb());
        assertFalse(death.itemDamage().spillCurrentReagents()); assertTrue(death.itemDamage().destructionOutputs().isEmpty());

        var footprintRaw = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("item_footprint", "small"));
        footprintRaw.putAll(BotanyCatalogCandidateTest.resources("item_footprint", "nettle", "death_nettle"));
        var footprint = PrototypeResolver.resolve(footprintRaw, null).asMap();
        var nettleFootprint = ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, footprint.get(id("nettle"))).getOrThrow();
        var deathFootprint = ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, footprint.get(id("death_nettle"))).getOrThrow();
        assertEquals(2, nettleFootprint.cells().size()); assertEquals("Small", nettleFootprint.itemSize().orElseThrow().name());
        assertEquals(2, deathFootprint.cells().size()); assertEquals("Small", deathFootprint.itemSize().orElseThrow().name());
        manager.stage(Map.of(ModProduceProfiles.TYPE, produce), ignored -> {});
        assertSame(before, manager.snapshot(), "candidate staging must not publish profiles");
    }
}
