package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProduceProfileTest {
    @Test void optionalSeedRoundTripsAndResolvedChildrenCanRemainUnbound() {
        var unbound = new ProduceProfile(null, new ProduceProfile.PotencyVisuals(1f, 2f));
        var encoded = ProduceProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, unbound).getOrThrow();
        assertFalse(encoded.getAsJsonObject().has("seed"));
        var decoded = ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
        assertNull(decoded.seed());
        assertEquals(1f, decoded.potencyVisuals().minimumScale());
        assertEquals(2f, decoded.potencyVisuals().maximumScale());

        var raw = read("produce_profile", "produce_base");
        raw.put(id("unbound_child"), JsonParser.parseString(
                "{\"id\":\"moonstation14:unbound_child\",\"parent\":\"moonstation14:produce_base\"}").getAsJsonObject());
        var manager = PrototypeRuntime.serverManager();
        manager.reload(ModProduceProfiles.TYPE, raw);
        var resolved = manager.snapshot(ModProduceProfiles.TYPE);
        assertNull(resolved.get(id("unbound_child")).seed());
        assertEquals(.5f, resolved.get(id("unbound_child")).potencyVisuals().minimumScale());
        assertEquals(4.5f, resolved.get(id("unbound_child")).potencyVisuals().maximumScale());
    }

    @Test void resolvedProduceAndIngestionSourcesAreUniqueAndComplete() {
        var manager = PrototypeRuntime.serverManager();
        manager.reload(ModProduceProfiles.TYPE, read("produce_profile", "produce_base", "wheat_bushel", "tomato", "potato", "poppy", "aloe"));
        manager.reload(ModIngestionProfiles.TYPE, read("ingestion_profile", "food_base", "edible_defaults", "tomato", "potato", "poppy", "aloe"));
        manager.reload(ModExtractableProfiles.TYPE, read("extractable_profile", "extractable_base", "wheat_bushel", "tomato", "potato", "poppy", "aloe"));
        manager.reload(ModItemFoodSolutionProfiles.TYPE, read("item_food_solution_profile", "wheat_bushel", "tomato", "potato", "poppy", "aloe"));
        var produce = manager.snapshot(ModProduceProfiles.TYPE);
        var foods = manager.snapshot(ModIngestionProfiles.TYPE);
        var extraction = manager.snapshot(ModExtractableProfiles.TYPE);
        var sources = manager.snapshot(ModItemFoodSolutionProfiles.TYPE);
        Map<String, Integer> sourceCaps = Map.of("tomato", 1400, "potato", 1400, "poppy", 2200, "aloe", 2000);
        for (String name : new String[]{"wheat_bushel", "tomato", "potato", "poppy", "aloe"}) {
            var profile = produce.get(id(name));
            assertNotNull(profile);
            assertEquals("food", extraction.get(id(name)).grindableSolutionName());
         assertEquals(.5f,profile.potencyVisuals().minimumScale());
         assertEquals(4.5f,profile.potencyVisuals().maximumScale());
            if (extraction.get(id(name)).juiceSolution() != null) {
                var juice = extraction.get(id(name)).juiceSolution().centContents();
                assertEquals(1, juice.size());
                assertEquals(1000L, juice.values().iterator().next());
                assertEquals(id("juicetomato"), juice.keySet().iterator().next().location());
            } else assertNull(extraction.get(id(name)).juiceSolution(), "juice authority is inherited only where configured");
            assertEquals(name.equals("wheat_bushel") ? "wheat" : name, profile.seed().getPath());
            var ingestion = foods.get(id(name));
            if (name.equals("wheat_bushel")) {
                assertNull(ingestion, "wheat must not receive ordinary edible policy");
                assertEquals(1000, sources.get(id(name)).capacityCents());
                assertEquals(Map.of(id("flour"), 1000L), sources.get(id(name)).contentsCents());
            } else {
                assertNotNull(ingestion);
                assertEquals(sourceCaps.get(name), sources.get(id(name)).capacityCents());
                assertEquals(500, ingestion.doseCents());
                assertEquals(20, ingestion.delayTicks());
                assertEquals(60, ingestion.forceFeedDelayTicks());
            }
        }
    }

    @Test void worldAppearanceUsesCapturedPotencyAndResolvedInheritedBounds() {
        var manager=PrototypeRuntime.clientManager();
        manager.reload(ModProduceProfiles.TYPE,read("produce_profile","produce_base","wheat_bushel","tomato","potato","poppy","aloe"));
        var resolved=manager.snapshot(ModProduceProfiles.TYPE);
        assertEquals(.5f,resolved.get(id("wheat_bushel")).potencyVisuals().minimumScale());
         assertEquals(.5f,resolved.get(id("aloe")).potencyVisuals().minimumScale());
         assertEquals(4.5f,resolved.get(id("aloe")).potencyVisuals().maximumScale());
        var wheat=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WHEAT_BUSHEL.get());
        assertEquals(1f,ProduceWorldAppearance.scale(wheat));
        wheat.set(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get(),
                new GeneticValue(id("wheat"),resolvedWheat()));
        assertEquals(1f,ProduceWorldAppearance.scale(wheat),"genetics alone do not author appearance");
        var reference=new PlantSeedReference(new GeneticValue(id("wheat"),resolvedWheat()),false);
        reference.update(reference.current().adjustPotency(45));
        GeneticCarriers.attach(wheat,reference);
        wheat.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),new ProduceInstanceData(id("wheat"),0,0));
        assertEquals(.5f,ProduceWorldAppearance.scale(wheat),"genetic carrier changes do not replace captured visual potency");
        wheat.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),new ProduceInstanceData(id("wheat"),5,0));
        assertEquals(.7f,ProduceWorldAppearance.scale(wheat),1e-6);
        wheat.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),new ProduceInstanceData(id("wheat"),10,0));
        assertEquals(.9f,ProduceWorldAppearance.scale(wheat),1e-6);
        assertEquals(50,GeneticCarriers.resolve(wheat,()->null).profile().lifecycle().potency());
        wheat.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),new ProduceInstanceData(id("wheat"),50,0));
        assertEquals(2.5f,ProduceWorldAppearance.scale(wheat));
        wheat.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),new ProduceInstanceData(id("wheat"),100,0));
        assertEquals(4.5f,ProduceWorldAppearance.scale(wheat));
        wheat.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),new ProduceInstanceData(id("wheat"),150,0));
        assertEquals(6.5f,ProduceWorldAppearance.scale(wheat));
    }

    @Test void explicitPotencyVisualOverrideReplacesInheritedBoundsWithoutMultiplication() {
        var raw=read("produce_profile","produce_base","tomato");
        raw.get(id("tomato")).add("potency_visuals",com.google.gson.JsonParser.parseString(
                "{\"minimum_scale\":0.5,\"maximum_scale\":1.0}"));
        var manager=PrototypeRuntime.clientManager(); manager.reload(ModProduceProfiles.TYPE,raw);
        var resolved=manager.snapshot(ModProduceProfiles.TYPE).get(id("tomato"));
        assertEquals(.5f,resolved.potencyVisuals().minimumScale());
        assertEquals(1f,resolved.potencyVisuals().maximumScale());
        var stack=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.TOMATO.get());
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),new ProduceInstanceData(id("tomato"),50,0));
        assertEquals(.75f,ProduceWorldAppearance.scale(stack));
    }

    @Test void independentFoodSourceDoesNotBelongToProduceOrIngestionPolicy() {
        var profile = new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(250, Map.of(id("nutriment"), 200L));
        assertEquals(250, profile.capacityCents());
        assertEquals(Map.of(id("nutriment"), 200L), profile.contentsCents());
        assertEquals(id("seed"), new ProduceProfile(id("seed"), null).seed());
        assertThrows(IllegalArgumentException.class,
                () -> new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(0, Map.of(id("nutriment"), 1L)));
    }

    @Test void generatedStackCapacityOverridesResolvedPrototypeCapacityWithoutMaterializingContents() {
        var source = new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(250, Map.of());
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE);
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),
                new ProduceInstanceData(id("seed"), 0, 725));
        assertEquals(7.25f, ProduceSourceComposition.capacity(stack, () -> { throw new AssertionError("must not resolve source"); }));
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(),
                new ProduceInstanceData(id("seed"), 0, 0));
        assertEquals(0f, ProduceSourceComposition.capacity(stack, () -> { throw new AssertionError("zero generated capacity still wins"); }));
        assertNull(stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()),
                "capacity lookup must not initialize contents");
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(), null);
        var resolutions = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(2.5f, ProduceSourceComposition.capacity(stack, () -> { resolutions.incrementAndGet(); return source; }));
        assertEquals(1, resolutions.get());
        assertEquals(0f, ProduceSourceComposition.capacity(stack, () -> null));
    }

    @Test void sourceInitializationMaterializesOnceAndNeverRefillsDepletedOrPartialProduce() {
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE);
        var source = new com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile(300,
                Map.of(id("nutriment"), 200L));
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initialize(stack, source);
        assertEquals(200L, stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()).centContents().values().iterator().next());
        var empty = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE);
        empty.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent());
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initialize(empty, source);
        assertTrue(empty.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()).centContents().isEmpty());
        var partial = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE);
        partial.set(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get(), new ProduceInstanceData(id("seed"), 1, 300));
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initialize(partial, source);
        assertTrue(partial.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()).centContents().isEmpty());
    }

    @Test void genericProduceAdapterTypeDeclaresReagentCapabilityWithoutEdibleInheritance() {
        assertTrue(com.juicyslew.moonstation14.ms14.reagent.IReagentTrait.class
                .isAssignableFrom(com.juicyslew.moonstation14.item.custom.ProduceItem.class));
        assertFalse(com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem.class
                .isAssignableFrom(com.juicyslew.moonstation14.item.custom.ProduceItem.class));
        assertTrue(com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.class
                .isAssignableFrom(com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem.class),
                "edible adapter reuses the generic food-solution owner");
    }

    private Map<ResourceLocation, JsonObject> read(String family, String... names) {
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (String name : names) {
            String path = "data/moonstation14/moonstation14/" + family + "/" + name + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                result.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        return result;
    }
    private SeedProfile resolvedWheat() {
        var manager=PrototypeRuntime.serverManager();
        manager.reload(ModSeedProfiles.TYPE,BotanyTestSeedFixtures.mainCatalog());
        return manager.snapshot(ModSeedProfiles.TYPE).get(id("wheat"));
    }
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("moonstation14", value); }
}
