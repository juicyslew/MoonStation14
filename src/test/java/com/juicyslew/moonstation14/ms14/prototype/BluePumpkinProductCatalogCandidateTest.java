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
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only supported composition, mapped from SS14 c9df5ef5d675b0d1d226828bddf6b78c28502d91. */
class BluePumpkinProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void bluePumpkinResolvesSupportedProfilesAndDamageDefaultsWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extractable = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var foodSources = BotanyCatalogCandidateTest.stageFoodSolutions(
                BotanyCatalogCandidateTest.resources("item_food_solution_profile", "blue_pumpkin"));
        var looseResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        looseResources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "blue_pumpkin"));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "produce_base", "blue_pumpkin"),
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.resources("ingestion_profile", "food_base", "blue_pumpkin"),
                ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "blue_pumpkin"),
                ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.resources("item_food_solution_profile", "blue_pumpkin"),
                ModLooseObjectProfiles.PROFILE_TYPE, looseResources
        ), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key,
                    ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> ingestion.put(key,
                    IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extractable.put(key,
                    ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(id("blue_pumpkin"), produce.get(id("blue_pumpkin")).seed());
        assertEquals(1f, produce.get(id("blue_pumpkin")).potencyVisuals().minimumScale());
        assertEquals(2f, produce.get(id("blue_pumpkin")).potencyVisuals().maximumScale());
        assertEquals(IngestionProfile.Kind.FOOD, ingestion.get(id("blue_pumpkin")).edible().kind());
        assertEquals(500, ingestion.get(id("blue_pumpkin")).edible().doseCents());
        assertEquals(20, ingestion.get(id("blue_pumpkin")).edible().delayTicks());
        assertEquals(60, ingestion.get(id("blue_pumpkin")).edible().forceFeedDelayTicks());
        assertFalse(ingestion.get(id("blue_pumpkin")).edible().requiresSpecialDigestion());
        assertEquals(2500, foodSources.get(id("blue_pumpkin")).capacityCents());
        assertEquals(Map.of(id("ammonia"), 1000L, id("chlorine"), 500L, id("vitamin"), 1000L),
                foodSources.get(id("blue_pumpkin")).contentsCents());
        assertEquals("food", extractable.get(id("blue_pumpkin")).grindableSolutionName());
        assertEquals(Map.of(reagent("juicebluepumpkin"), 1000L), extractable.get(id("blue_pumpkin")).juiceSolution().centContents());

        var loose = resolveLoose();
        var damage = loose.itemDamage();
        assertEquals(Set.of("brute", "burn", "toxin", "airloss", "genetic"), damage.allowedGroups());
        assertEquals(16f, damage.destructionThreshold());
        assertEquals(id("botany/produce/desecration"), damage.destructionSound());
        assertTrue(damage.spillCurrentReagents());
        assertTrue(damage.throwLandDamage().isEmpty(), "blue pumpkin has no tomato-style throw-land damage");
        assertEquals(Map.of("blunt", 5f), damage.highSpeedImpact(), "generic BaseItem impact damage is inherited");
        assertEquals(20f, damage.minimumImpactSpeed());
        assertEquals(.5f, damage.impactFactor());
        assertEquals(40, damage.impactCooldownTicks());
        for (String family : new String[]{"produce_profile", "ingestion_profile", "item_food_solution_profile", "extractable_profile", "loose_object_profile"})
            assertMainAbsent(family, "blue_pumpkin");
    }

    @Test void bluePumpkinDamageUsesExistingAllowedFilterAndAccumulatedThresholdKernel() {
        var composition = resolveLoose().itemDamage();
        assertEquals(Map.of("blunt", 2f, "heat", 4f), ItemDamageKernel.filterAllowed(
                Map.of("blunt", 2f, "heat", 4f, "holy", 8f), composition.allowedGroups()));
        assertEquals(Map.of("blunt", 2f), ItemDamageKernel.filterAllowed(Map.of("blunt", 2f, "heat", 4f), Set.of("brute")),
                "canonical heat is valid damage but excluded when only brute is configured");
        assertThrows(IllegalArgumentException.class, () -> ItemDamageKernel.filterAllowed(
                Map.of("unknown", 1f), composition.allowedGroups()), "unknown damage keys fail canonical validation");
        float minimumSpeed = composition.minimumImpactSpeed();
        float factor = composition.impactFactor();
        int cooldownTicks = composition.impactCooldownTicks();
        var belowSpeed = ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, Map.of("blunt", 1f),
                composition.allowedGroups(), minimumSpeed, factor, cooldownTicks, 0, .999, true,
                composition.destructionThreshold());
        assertFalse(belowSpeed.applied());
        assertFalse(belowSpeed.destructionDue());

        var first = ItemDamageKernel.highSpeedImpact(belowSpeed.state(), Map.of("blunt", 1f),
                composition.allowedGroups(), minimumSpeed, factor, cooldownTicks, 0, 16, true,
                composition.destructionThreshold());
        assertTrue(first.applied());
        assertEquals(Map.of("blunt", 8f), first.state().damage());
        assertFalse(first.destructionDue());
        var cooldownReplay = ItemDamageKernel.highSpeedImpact(first.state(), Map.of("blunt", 1f),
                composition.allowedGroups(), minimumSpeed, factor, cooldownTicks, 39, 16, true,
                composition.destructionThreshold());
        assertFalse(cooldownReplay.applied());
        assertFalse(cooldownReplay.destructionDue());
        var second = ItemDamageKernel.highSpeedImpact(cooldownReplay.state(), Map.of("blunt", 1f),
                composition.allowedGroups(), minimumSpeed, factor, cooldownTicks, 40, 16, true,
                composition.destructionThreshold());
        assertTrue(second.applied());
        assertEquals(Map.of("blunt", 16f), second.state().damage());
        assertTrue(second.destructionDue(), "existing kernel reports destruction at resolved threshold 16");

        var filteredAccumulation = ItemDamageKernel.apply(ItemDamageState.EMPTY,
                Map.of("blunt", 5f, "heat", 100f), Set.of("brute"));
        assertEquals(Map.of("blunt", 5f), filteredAccumulation.damage());
    }

    private static LooseObjectProfile resolveLoose() {
        var manager = new PrototypeManager(); manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var resources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        resources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "blue_pumpkin"));
        manager.reload(ModLooseObjectProfiles.PROFILE_TYPE, resources);
        return manager.snapshot(ModLooseObjectProfiles.PROFILE_TYPE).get(id("blue_pumpkin"));
    }
    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }
    private static void assertMainAbsent(String family, String name) {
        String path = "data/moonstation14/moonstation14/" + family + "/" + name + ".json";
        try (var stream = BluePumpkinProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "activated MAIN profile: " + path);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
