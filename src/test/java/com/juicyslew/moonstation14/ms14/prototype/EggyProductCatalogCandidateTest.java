package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfileMergeStrategy;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only eggy/eggshell composition; unsupported heat and random sprite behavior are omitted. */
class EggyProductCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void eggyCompositionStagesProduceFoodFootprintAndFragileDestructionWithoutPublishing() throws java.io.IOException {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE);
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.register(ModItemFootprints.TYPE);
        var before = manager.snapshot();

        var source = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "egg", "eggshells"));
        assertEquals(600, source.get(id("egg")).capacityCents());
        assertEquals(Map.of(id("egg"), 600L), source.get(id("egg")).contentsCents());
        assertEquals(200, source.get(id("eggshells")).capacityCents());
        assertEquals(Map.of(id("egg"), 100L), source.get(id("eggshells")).contentsCents());

        var ingestionRaw = new java.util.LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base"));
        ingestionRaw.putAll(BotanyCatalogCandidateTest.resources("ingestion_profile", "egg", "eggshells"));
        var ingestion = PrototypeResolver.resolve(ingestionRaw, new IngestionProfileMergeStrategy()).asMap();
        var egg = IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, ingestion.get(id("egg"))).getOrThrow();
        var shell = IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, ingestion.get(id("eggshells"))).getOrThrow();
        assertEquals(java.util.List.of(id("eggshells")), egg.edible().trash());
        assertEquals(IngestionProfile.Kind.FOOD, egg.edible().kind());
        assertTrue(shell.edible().trash().isEmpty());

        var looseRaw = new java.util.LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        looseRaw.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "egg"));
        var footprints = new java.util.LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("item_footprint", "tiny"));
        footprints.putAll(BotanyCatalogCandidateTest.resources("item_footprint", "egg", "eggshells"));
        var looseResolved = PrototypeResolver.resolve(looseRaw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow()).asMap();
        var loose = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, looseResolved.get(id("egg"))).getOrThrow();
        assertEquals(0.1f, loose.itemDamage().minimumImpactSpeed());
        assertEquals(Map.of("blunt", 1.0f), loose.itemDamage().highSpeedImpact());
        assertEquals(0.5f, loose.itemDamage().impactFactor());
        assertEquals(40, loose.itemDamage().impactCooldownTicks());
        assertEquals(1.0f, loose.itemDamage().destructionThreshold());
        assertTrue(loose.itemDamage().spillCurrentReagents());
        assertEquals(java.util.List.of(new ItemProcessingProfile.ItemOutput(id("eggshells"), 1)), loose.itemDamage().destructionOutputs());

        manager.stage(Map.of(ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.resources("produce_profile", "egg"),
                ModItemFootprints.TYPE, footprints), catalogs -> {
            var produced = ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    catalogs.get(ModProduceProfiles.TYPE.typeId()).get(id("egg"))).getOrThrow();
            assertEquals(id("eggy"), produced.seed());
            assertNull(produced.potencyVisuals(), "FoodEgg has no source PotencyVisuals component");
            for (String item : new String[]{"egg", "eggshells"}) {
                var footprint = ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                        catalogs.get(ModItemFootprints.TYPE.typeId()).get(id(item))).getOrThrow();
                assertEquals("Tiny", footprint.itemSize().orElseThrow().name());
            }
        });
        assertSame(before, manager.snapshot(), "candidate resolution must not publish profiles");
        var productionManager = new PrototypeManager();
        productionManager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var productionBefore = productionManager.snapshot();
        assertDoesNotThrow(() -> productionManager.stage(Map.of(ModLooseObjectProfiles.PROFILE_TYPE, looseRaw), ignored -> {}));
        assertSame(productionBefore, productionManager.snapshot(), "valid candidate resolution must not publish");
        var invalidOutput = looseRaw.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().deepCopy()));
        var eggProfile = invalidOutput.get(id("egg"));
        eggProfile.getAsJsonObject("item_damage").getAsJsonArray("destruction_outputs").get(0).getAsJsonObject()
                .addProperty("output", "moonstation14:synthetic_unregistered_eggy_output");
        var rejection = assertThrows(PrototypeLoadException.class, () -> productionManager.stage(
                Map.of(ModLooseObjectProfiles.PROFILE_TYPE, invalidOutput), ignored -> {}));
        assertTrue(rootMessages(rejection).contains("synthetic_unregistered_eggy_output"), rootMessages(rejection));
        assertSame(productionBefore, productionManager.snapshot(), "unregistered synthetic output must reject before publication");

        for (String family : new String[]{"produce_profile", "ingestion_profile", "item_food_solution_profile", "item_footprint", "loose_object_profile"})
            BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate(family, "egg", "eggshells");
    }

    @Test void canonicalEggProductMapsToEggySeedAndSharedGenerationChemistry() {
        var seedRows = BotanyCatalogCandidateTest.resources("seed_profile", "defaults", "eggy");
        var seedResolved = PrototypeResolver.resolve(seedRows, new com.juicyslew.moonstation14.ms14.botany.SeedProfileMergeStrategy()).asMap();
        var seed = SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, seedResolved.get(id("eggy"))).getOrThrow();
        var produceRows = BotanyCatalogCandidateTest.resources("produce_profile", "egg");
        var produceResolved = PrototypeResolver.resolve(produceRows, null).asMap();
        var produce = ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, produceResolved.get(id("egg"))).getOrThrow();
        assertEquals(id("egg"), seed.product());
        assertEquals(id("eggy"), produce.seed());
        var generated = com.juicyslew.moonstation14.ms14.botany.ProduceFactory.generatedChemistry(seed, seed.lifecycle().potency());
        assertEquals(Map.of(net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("egg")), 600L), generated);
    }
    private static String rootMessages(Throwable error) {
        var messages = new StringBuilder();
        for (var current = error; current != null; current = current.getCause()) messages.append(current.getMessage()).append('\n');
        return messages.toString();
    }
}
