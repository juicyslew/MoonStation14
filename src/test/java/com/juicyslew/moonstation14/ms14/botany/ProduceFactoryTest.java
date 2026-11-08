package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.component.ModDataComponents;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class ProduceFactoryTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void harvestChemistryUsesSeedFormulaAndExactCentTotal() {
        var seed = resolvedWheat();
        var contents = ProduceFactory.generatedChemistry(seed, 5);
        assertEquals(525L, contents.get(net.minecraft.resources.ResourceKey.create(
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("flour"))));
        assertEquals(125L, contents.get(net.minecraft.resources.ResourceKey.create(
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("nutriment"))));
        assertEquals(650L, com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(contents.values()));
    }

    @Test void harvestUsesCapturedProduceMutationPayloadWithoutChangingGeneratedChemistry() {
        var profile = resolvedProfiles("wheat").get(id("wheat"));
        var captured = new com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition(
                new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(true, .73d, 8d, true),
                new com.juicyslew.moonstation14.ms14.slip.SlipResponseData(2d, 4d, 3d, .25d, .7d, 8d, true, false, true));
        var record = new PlantMutationRecord("captured", false, true, true, captured);
        var genetics = new GeneticValue(id("wheat"), profile, java.util.List.of(record));
        var stack = ProduceFactory.createHarvest(genetics, profile.lifecycle().potency());
        assertEquals(captured, stack.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get()));
        assertEquals(ProduceFactory.generatedChemistry(profile, profile.lifecycle().potency()),
                stack.get(ModDataComponents.REAGENT.get()).centContents());
        assertEquals(genetics, stack.get(ModDataComponents.GENETIC_VALUE.get()));
        assertSame(captured, genetics.mutations().getFirst().slippery());

        var excluded = ProduceFactory.createHarvest(new GeneticValue(id("wheat"), profile,
                java.util.List.of(new PlantMutationRecord("not-produce", false, false, true, captured))),
                profile.lifecycle().potency());
        assertNull(excluded.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get()));
    }

    @Test void successfulMutationCycleFlowsCapturedPayloadThroughReferenceHarvestAndLooseResolver() {
        var runtime = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var looseType = com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles.PROFILE_TYPE;
        var previousLoose = runtime.snapshot(looseType).asMap();
        try {
        var looseProfiles = new java.util.LinkedHashMap<>(previousLoose);
        var genericRaw = mainResourceProfiles("loose_object_profile", "generic");
        var genericManager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        genericManager.register(looseType);
        genericManager.reload(looseType, genericRaw);
        looseProfiles.putAll(genericManager.snapshot(looseType).asMap());
        runtime.publishDecoded(looseType, looseProfiles);
        runtime.reload(ModProduceProfiles.TYPE, resourceProfiles("produce_profile", "produce_base", "wheat_bushel"));
        runtime.reload(com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE,
                resourceProfiles("ingestion_profile", "food_base", "edible_defaults"));
        runtime.reload(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE,
                resourceProfiles("extractable_profile", "extractable_base", "wheat_bushel"));
        runtime.reload(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE,
                mainResourceProfiles("item_food_solution_profile", "wheat_bushel"));
        var profile = resolvedProfiles("wheat").get(id("wheat"));
        var captured = new com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition(
                new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(true, .71d, 7d, true),
                new com.juicyslew.moonstation14.ms14.slip.SlipResponseData(2d, 4d, 3d, .25d, .7d, 8d, true, false, true));
        var entry = new PlantMutationTable.Entry("captured-payload", 1, false, true, true,
                PlantMutationTable.EffectKind.SLIPIFY, java.util.Optional.empty(), java.util.Optional.empty(), "",
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(captured));
        var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), profile, 0); plant.adjustMutationLevel(1);
        var rng = new java.util.random.RandomGenerator() {
            int doubles;
            @Override public long nextLong() { throw new AssertionError("unexpected random draw"); }
            @Override public double nextDouble() { return doubles++ == 0 ? 0d : 1d; }
        };
        PlantMutationSystem.cycle(plant, new PlantMutationTable(java.util.List.of(entry)), rng, () -> .5f, null, null);
        var reference = plant.seedReference();
        assertEquals(java.util.List.of(new PlantMutationRecord("captured-payload", false, true, true, captured)), reference.current().mutations());
        var baseline = ProduceFactory.generatedChemistry(profile, profile.lifecycle().potency());
        var stack = ProduceFactory.createHarvest(reference, profile.lifecycle().potency());
        assertSame(reference, stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
        assertEquals(reference.current(), stack.get(ModDataComponents.GENETIC_VALUE.get()));
        assertEquals(captured, com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveSlipperyForStack(stack, false));
        assertEquals(baseline, stack.get(ModDataComponents.REAGENT.get()).centContents());
        assertEquals(1650, stack.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents());

        var changed = new com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition(
                new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(false, .1d, 1d, false),
                com.juicyslew.moonstation14.ms14.slip.SlipResponseData.DEFAULT);
        var changedTableEntry = new PlantMutationTable.Entry("captured-payload", 1, false, true, true,
                PlantMutationTable.EffectKind.SLIPIFY, java.util.Optional.empty(), java.util.Optional.empty(), "",
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(changed));
        assertNotEquals(changedTableEntry.slippery().orElseThrow(),
                com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveSlipperyForStack(stack, false));
        } finally {
            runtime.publishDecoded(looseType, previousLoose);
        }
    }

    @Test void harvestInstanceMetadataRoundTripsAndRejectsInvalidState() {
        var data = new ProduceInstanceData(id("wheat"), 30, 650);
        var encoded = ProduceInstanceData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(data, ProduceInstanceData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertThrows(IllegalArgumentException.class, () -> new ProduceInstanceData(id("wheat"), 30, -1));
    }
    @Test void mutationContributionTruncatesBeforeAddingFixedPointMinimum() {
        var chemical = new SeedProfile.Chemical(.2f, .71f, 100f / .71f, false);
        assertEquals(23L, chemical.amountCents(5f));
        assertEquals(23L, com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(chemical.amount(5f)));
    }
    @Test void wheatAndTomatoGenerationAndCapacityBaselinesRemainExact() {
        var runtime = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        runtime.reload(ModProduceProfiles.TYPE, resourceProfiles("produce_profile", "produce_base", "wheat_bushel", "tomato"));
        runtime.reload(com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE,
                resourceProfiles("ingestion_profile", "food_base", "edible_defaults", "tomato"));
        runtime.reload(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE,
                resourceProfiles("extractable_profile", "extractable_base", "wheat_bushel", "tomato"));
        runtime.reload(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE,
                mainResourceProfiles("item_food_solution_profile", "wheat_bushel", "tomato"));
        resolvedInitializeUsesTheSharedFoodSolutionOwner();
        var profiles = resolvedProfiles("wheat", "tomato");
        var wheat = profiles.get(id("wheat"));
        var wheatChemistry = ProduceFactory.generatedChemistry(wheat, wheat.lifecycle().potency());
        assertEquals(650L, com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(wheatChemistry.values()));
        var wheatHarvest = ProduceFactory.createHarvest(new GeneticValue(id("wheat"), wheat), wheat.lifecycle().potency());
        assertEquals(1650, wheatHarvest.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents());
        assertEquals(new GeneticValue(id("wheat"), wheat), wheatHarvest.get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()));
        assertEquals(ProduceFactory.generatedChemistry(wheat, wheat.lifecycle().potency()),
                wheatHarvest.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()).centContents());
        var tomato = profiles.get(id("tomato"));
        var tomatoChemistry = ProduceFactory.generatedChemistry(tomato, tomato.lifecycle().potency());
        assertEquals(441L, com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(tomatoChemistry.values()));
        var tomatoHarvest = ProduceFactory.createHarvest(new GeneticValue(id("tomato"), tomato), tomato.lifecycle().potency());
        assertEquals(1841, tomatoHarvest.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents());
        assertEquals(ProduceFactory.generatedChemistry(tomato, tomato.lifecycle().potency()),
                tomatoHarvest.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()).centContents());
    }

    @Test void generatedProduceWithoutFreshSourceGetsZeroInitialCapacityButKeepsGeneratedChemistry() {
        var runtime = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var previousProduce = runtime.snapshot(ModProduceProfiles.TYPE);
        var ingestionType = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE;
        var previousIngestion = runtime.snapshot(ingestionType);
        var sourceType = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE;
        var previousSources = runtime.snapshot(sourceType);
        try {
            runtime.reload(ModProduceProfiles.TYPE, mainResourceProfiles("produce_profile", "produce_base", "wheat_bushel"));
            runtime.reload(sourceType, new java.util.LinkedHashMap<>());
            // Replace ingestion with a scoped catalog that deliberately omits wheat_bushel.
            runtime.reload(com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE,
                    mainResourceProfiles("ingestion_profile", "edible_defaults", "food_base"));
            var item = com.juicyslew.moonstation14.item.ModItems.WHEAT_BUSHEL.get();
            assertNull(ProduceSourceComposition.resolve(item, null), "fixture must really have no fresh source");
            var profile = resolvedProfiles("wheat").get(id("wheat"));
            var genetics = new GeneticValue(id("wheat"), profile);
            var stack = ProduceFactory.createHarvest(genetics, profile.lifecycle().potency());
            var expected = ProduceFactory.generatedChemistry(profile, profile.lifecycle().potency());
            int generated = Math.toIntExact(com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.total(expected.values()));
            assertEquals(expected, stack.get(ModDataComponents.REAGENT.get()).centContents());
            assertEquals(generated, stack.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents());
            assertEquals(generated / 100f, item.getCapacity(stack, null));
            assertEquals(genetics, stack.get(ModDataComponents.GENETIC_VALUE.get()));

            var emptyChemistry = new SeedProfile(profile.packet(), profile.product(), profile.visualKey(), profile.biology(),
                    profile.lifecycle(), java.util.Map.of(), profile.consumeGasses(), profile.exudeGasses(),
                    profile.mutationTargets(), profile.scream(), profile.sourceName());
            var emptyStack = ProduceFactory.createHarvest(new GeneticValue(id("wheat"), emptyChemistry),
                    emptyChemistry.lifecycle().potency());
            assertNotNull(emptyStack.get(ModDataComponents.REAGENT.get()), "EnsureSolution semantics retain an existing empty reagent component");
            assertTrue(emptyStack.get(ModDataComponents.REAGENT.get()).centContents().isEmpty());
            assertEquals(0, emptyStack.get(ModDataComponents.PRODUCE_INSTANCE.get()).generatedCapacityCents());
            assertEquals(0f, item.getCapacity(emptyStack, null));
        } finally {
            runtime.reload(ModProduceProfiles.TYPE, encodeCatalog(previousProduce, ProduceProfile.CODEC));
            runtime.reload(ingestionType, encodeCatalog(previousIngestion,
                    com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile.CODEC));
            runtime.reload(sourceType, encodeCatalog(previousSources,
                    com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile.CODEC));
        }
    }

    @Test void fixtureYieldAndPotencyPresetsTravelThroughRealProduceAndSeedPacketCarriers() {
        var runtime = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        runtime.reload(ModProduceProfiles.TYPE, resourceProfiles("produce_profile", "produce_base", "wheat_bushel", "tomato"));
        runtime.reload(com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE,
                resourceProfiles("ingestion_profile", "food_base", "edible_defaults", "tomato"));
        runtime.reload(com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.TYPE,
                resourceProfiles("extractable_profile", "extractable_base", "wheat_bushel", "tomato"));
        runtime.reload(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE,
                mainResourceProfiles("item_food_solution_profile", "wheat_bushel", "tomato"));
        resolvedInitializeUsesTheSharedFoodSolutionOwner();
        var wheat = resolvedProfiles("wheat").get(id("wheat"));
        assertEquals(3, wheat.lifecycle().yield()); assertEquals(5, wheat.lifecycle().potency());
        var base = new GeneticValue(id("wheat"), wheat);
        var presets = BotanyFixtureScenarios.wheatTraitPresets(base);
        var w = presets.yield();
        var x = presets.potency();
        assertEquals(7, w.profile().lifecycle().yield()); assertEquals(5, w.profile().lifecycle().potency());
        assertEquals(3, x.profile().lifecycle().yield()); assertEquals(50, x.profile().lifecycle().potency());
        assertEquals(SeedProfile.Harvest.NO_REPEAT, w.profile().lifecycle().harvest());
        assertEquals(SeedProfile.Harvest.NO_REPEAT, x.profile().lifecycle().harvest());
        assertEquals(wheat, base.profile());
        assertNotSame(w.profile(), x.profile());
        // Source fields untouched by either single-stat fixture edit.
        for (var changed : java.util.List.of(w.profile(), x.profile())) {
            assertEquals(wheat.biology(), changed.biology()); assertEquals(wheat.chemicals(), changed.chemicals());
            assertEquals(wheat.consumeGasses(), changed.consumeGasses()); assertEquals(wheat.exudeGasses(), changed.exudeGasses());
            assertEquals(wheat.packet(), changed.packet()); assertEquals(wheat.product(), changed.product());
            assertEquals(wheat.scream(), changed.scream());
            assertEquals(wheat.mutationTargets(), changed.mutationTargets());
            assertEquals(wheat.lifecycle().growthStages(), changed.lifecycle().growthStages());
            assertEquals(wheat.lifecycle().lifespan(), changed.lifecycle().lifespan());
            assertEquals(wheat.lifecycle().maturation(), changed.lifecycle().maturation());
            assertEquals(wheat.lifecycle().production(), changed.lifecycle().production());
            assertEquals(wheat.lifecycle().viable(), changed.lifecycle().viable());
            assertEquals(wheat.lifecycle().seedless(), changed.lifecycle().seedless());
        }
        var manager = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var itemType = ModSeedItemProfiles.TYPE;
        var previousItemProfiles = manager.snapshot(itemType);
        try {
        // Encode the explicit fixture composition; vanilla wheat seeds are not globally admitted.
        var fixtureSeedItem = itemType.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                new SeedItemProfile(id("wheat"))).getOrThrow().getAsJsonObject();
        manager.reload(itemType, java.util.Map.of(wheat.packet(), fixtureSeedItem));
        for (var genetics : java.util.List.of(w, x)) {
            var reference = new PlantSeedReference(genetics, true);
            var produce = ProduceFactory.createHarvest(reference, genetics.profile().lifecycle().potency());
            assertEquals(genetics, GeneticCarriers.resolve(produce, () -> null));
            var packet = GeneticCarriers.createSeedPacket(reference, null);
            assertEquals(wheat.packet(), net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(packet.getItem()));
            assertSame(reference, packet.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
            var examined = GeneticCarriers.resolveSeedPacket(packet);
            assertEquals(genetics.profile().lifecycle().yield(), examined.profile().lifecycle().yield());
            assertEquals(genetics.profile().lifecycle().potency(), examined.profile().lifecycle().potency());
        }
        } finally {
            var rawPrevious = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
            previousItemProfiles.asMap().forEach((key, value) -> rawPrevious.put(key,
                    itemType.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject()));
            manager.reload(itemType, rawPrevious);
        }
    }


    private static void resolvedInitializeUsesTheSharedFoodSolutionOwner() {
        var wheat = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WHEAT_BUSHEL.get());
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(wheat, null);
        var flour = ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("flour"));
        assertEquals(java.util.Map.of(flour, 1000L), wheat.get(ModDataComponents.REAGENT.get()).centContents());

        var tomato = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.TOMATO.get());
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(tomato, null);
        var nutriment = ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("nutriment"));
        var vitamin = ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("vitamin"));
        var water = ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id("water"));
        assertEquals(java.util.Map.of(nutriment, 700L, vitamin, 300L, water, 400L), tomato.get(ModDataComponents.REAGENT.get()).centContents());

        var depleted = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.TOMATO.get());
        depleted.set(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent());
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(depleted, null);
        assertTrue(depleted.get(ModDataComponents.REAGENT.get()).centContents().isEmpty());
        var partial = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.TOMATO.get());
        partial.set(ModDataComponents.PRODUCE_INSTANCE.get(), new ProduceInstanceData(id("tomato"), 2, 1841));
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(partial, null);
        assertTrue(partial.get(ModDataComponents.REAGENT.get()).centContents().isEmpty());
    }

    @Test void reagentCurrentStateCodecPreservesPartialAndEmptyValues() {
        var seed = resolvedWheat();
        var generated = ProduceFactory.generatedChemistry(seed, 5);
        generated.replaceAll((key, value) -> value / 2);
        var remaining = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(generated);
        var encoded = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, remaining).getOrThrow();
        var decoded = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(remaining, decoded);
        var empty = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of());
        var emptyJson = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow();
        assertEquals(empty, com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.CODEC.parse(JsonOps.INSTANCE, emptyJson).getOrThrow());
    }

    @Test void invalidHarvestInputsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> ProduceFactory.generatedChemistry(null, 1));
        assertThrows(IllegalArgumentException.class, () -> ProduceFactory.generatedChemistry(resolvedWheat(), Float.NaN));
    }
    private static SeedProfile resolvedWheat() {
        return BotanyTestSeedFixtures.resolved(id("wheat"));
    }
    private static java.util.Map<ResourceLocation, SeedProfile> resolvedProfiles(String... leaves) {
        var catalog = BotanyTestSeedFixtures.manager().snapshot(ModSeedProfiles.TYPE);
        var out = new java.util.LinkedHashMap<ResourceLocation, SeedProfile>();
        for (String leaf : leaves) out.put(id(leaf), catalog.get(id(leaf)));
        return out;
    }
    private static java.util.Map<ResourceLocation, com.google.gson.JsonObject> resourceProfiles(String family, String... names) {
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : names) try (var in = ProduceFactoryTest.class.getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/" + family + "/" + name + ".json")) {
            assertNotNull(in); raw.put(id(name), com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        return raw;
    }
    private static java.util.Map<ResourceLocation, com.google.gson.JsonObject> mainResourceProfiles(String family, String... names) {
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : names) try (var in = ProduceFactoryTest.class.getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/" + family + "/" + name + ".json")) {
            assertNotNull(in); raw.put(id(name), com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        return raw;
    }
    private static <T> java.util.Map<ResourceLocation, com.google.gson.JsonObject> encodeCatalog(
            com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<T> catalog, com.mojang.serialization.Codec<T> codec) {
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        catalog.asMap().forEach((key, value) -> raw.put(key,
                codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject()));
        return raw;
    }
}
