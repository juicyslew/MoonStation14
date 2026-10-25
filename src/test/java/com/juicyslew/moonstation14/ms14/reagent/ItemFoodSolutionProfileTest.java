package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemFoodSolutionProfileTest {
    @Test void codecKeepsCentsSchemaAndValues() {
        var input = JsonParser.parseString("{\"capacity_cents\":2000,\"contents_cents\":{\"moonstation14:cornmeal\":1000}}");
        var result = ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, input).getOrThrow();
        assertEquals(2000, result.capacityCents());
        assertEquals(1000L, result.contentsCents().get(ResourceLocation.parse("moonstation14:cornmeal")));
        assertTrue(result.canReact());
        assertFalse(result.refillable());
        var inert = ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"capacity_cents\":2000,\"contents_cents\":{},\"can_react\":false}")).getOrThrow();
        assertFalse(inert.canReact());
        assertTrue(ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"capacity_cents\":2000,\"contents_cents\":{},\"can_react\":\"false\"}")).error().isPresent());
    }

    @Test void solutionNameDefaultsToFoodAndSupportsOpaqueNamedSolutions() {
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        var defaulted = ItemFoodSolutionProfile.CODEC.parse(ops, JsonParser.parseString("{\"capacity_cents\":200,\"contents_cents\":{}}" )).getOrThrow();
        assertEquals("food", defaulted.solutionName());
        var named = ItemFoodSolutionProfile.CODEC.parse(ops, JsonParser.parseString("{\"capacity_cents\":200,\"contents_cents\":{},\"solution_name\":\"shardglass\"}" )).getOrThrow();
        assertEquals("shardglass", named.solutionName());
        assertFalse(defaulted.refillable());
        assertTrue(ItemFoodSolutionProfile.CODEC.parse(ops, JsonParser.parseString("{\"capacity_cents\":200,\"contents_cents\":{},\"refillable\":true}")).getOrThrow().refillable());
        assertThrows(IllegalArgumentException.class, () -> ItemFoodSolutionProfile.CODEC.parse(ops,
                JsonParser.parseString("{\"capacity_cents\":200,\"contents_cents\":{},\"solution_name\":\" \"}" )).getOrThrow());
    }

    @Test void contentsAreImmutableAndValidatedAgainstCapacity() {
        var key = ResourceLocation.parse("moonstation14:cornmeal");
        var mutable = new java.util.HashMap<ResourceLocation, Long>();
        mutable.put(key, 100L);
        var profile = new ItemFoodSolutionProfile(200, mutable);
        mutable.clear();
        assertEquals(Map.of(key, 100L), profile.contentsCents());
        assertThrows(UnsupportedOperationException.class, () -> profile.contentsCents().put(key, 1L));
        assertThrows(IllegalArgumentException.class, () -> new ItemFoodSolutionProfile(99, Map.of(key, 100L)));
    }

    @Test void inheritanceResolvesIndependentSourceDefaults() {
        var manager = new PrototypeManager();
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        var base = JsonParser.parseString("{\"abstract\":true,\"capacity_cents\":500,\"contents_cents\":{\"moonstation14:nutriment\":200}}").getAsJsonObject();
        var child = JsonParser.parseString("{\"parent\":\"test:food_base\"}").getAsJsonObject();
        base.addProperty("can_react", false);
        child.addProperty("can_react", true);
        base.addProperty("refillable", true);
        manager.reload(ModItemFoodSolutionProfiles.TYPE, Map.of(ResourceLocation.parse("test:food_base"), base,
                ResourceLocation.parse("test:food_child"), child));
        assertEquals(new ItemFoodSolutionProfile(500, Map.of(ResourceLocation.parse("moonstation14:nutriment"), 200L), true, "food", true),
                manager.snapshot(ModItemFoodSolutionProfiles.TYPE).get(ResourceLocation.parse("test:food_child")));
        assertTrue(manager.snapshot(ModItemFoodSolutionProfiles.TYPE).get(ResourceLocation.parse("test:food_child")).refillable());
        assertThrows(IllegalArgumentException.class, () -> ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"capacity_cents\":99,\"contents_cents\":{\"moonstation14:nutriment\":100}}")));
    }

    @Test void aggregateCatalogRoundTripPreservesFalseAndOtherProfileDefault() {
        var type = ModItemFoodSolutionProfiles.TYPE;
        var source = new PrototypeManager();
        var target = new PrototypeManager();
        source.register(type);
        target.register(type);
        var inertShardglass = new ItemFoodSolutionProfile(200, Map.of(), false, "shardglass", true);
        var defaultReactive = new ItemFoodSolutionProfile(300, Map.of());
        source.publishDecoded(type, Map.of(ResourceLocation.parse("test:inert"), inertShardglass,
                ResourceLocation.parse("test:default"), defaultReactive));
        var encoded = source.encodePublishedCatalogs();
        target.publishEncodedCatalogs(encoded);
        assertFalse(target.snapshot(type).get(ResourceLocation.parse("test:inert")).canReact());
        assertEquals("shardglass", target.snapshot(type).get(ResourceLocation.parse("test:inert")).solutionName());
        assertTrue(target.snapshot(type).get(ResourceLocation.parse("test:inert")).refillable());
        assertTrue(target.snapshot(type).get(ResourceLocation.parse("test:default")).canReact());
        assertTrue(source.snapshot(type).get(ResourceLocation.parse("test:default")).canReact());
        assertEquals("food", target.snapshot(type).get(ResourceLocation.parse("test:default")).solutionName());
        assertFalse(target.snapshot(type).get(ResourceLocation.parse("test:default")).refillable());
    }

    @Test void materializationIsOneTimePreservesEmptyAndGeneratedStateAndAbsentProfile() {
        var food = new ItemFoodSolutionProfile(200, Map.of(ResourceLocation.parse("moonstation14:nutriment"), 100L));
        var stack = new ItemStack(Items.APPLE);
        ItemFoodSolutions.initialize(stack, food);
        var initialized = stack.get(ModDataComponents.REAGENT.get());
        assertNotNull(initialized);
        assertEquals(100L, initialized.centContents().values().iterator().next());
        ItemFoodSolutions.initialize(stack, new ItemFoodSolutionProfile(200, Map.of()));
        assertSame(initialized, stack.get(ModDataComponents.REAGENT.get()));

        var empty = new ItemStack(Items.APPLE);
        empty.set(ModDataComponents.REAGENT.get(), new ReagentComponent());
        ItemFoodSolutions.initialize(empty, food);
        assertTrue(empty.get(ModDataComponents.REAGENT.get()).centContents().isEmpty());

        var generated = new ItemStack(Items.APPLE);
        generated.set(ModDataComponents.PRODUCE_INSTANCE.get(), new com.juicyslew.moonstation14.ms14.botany.ProduceInstanceData(
                ResourceLocation.parse("moonstation14:seed"), 1, 200));
        ItemFoodSolutions.initialize(generated, food);
        assertTrue(generated.get(ModDataComponents.REAGENT.get()).centContents().isEmpty());

        var absent = new ItemStack(Items.APPLE);
        ItemFoodSolutions.initialize(absent, null);
        assertNull(absent.get(ModDataComponents.REAGENT.get()));
    }

    @Test void genericFoodSolutionCapabilityDoesNotGrantInteractionCapabilities() {
        var adapterType = com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.class;
        assertTrue(IReagentTrait.class.isAssignableFrom(adapterType));
        assertFalse(com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem.class.isAssignableFrom(adapterType));
        IReagentTrait capabilities = () -> 2f;
        assertNull(capabilities.drainableSolution(null, null));
        assertEquals(RefillableSolutionPolicy.DISABLED, capabilities.refillableSolution(null, null));
        assertEquals(SolutionTransferPolicy.DISABLED, capabilities.solutionTransfer(null, null));
        assertEquals(DrinkSolutionPolicy.DISABLED, capabilities.drinkSolution(null, null));
    }

    @Test void refillableProfileEnablesReceiveOnlyPolicyWithoutDrainCapability() {
        var disabled = new ItemFoodSolutionProfile(200, Map.of());
        var refillable = new ItemFoodSolutionProfile(200, Map.of(), true, "shardglass", true);
        var adapter = com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.class;
        assertNull(((IReagentTrait) () -> 2f).drainableSolution(null, null));
        assertEquals(RefillableSolutionPolicy.DISABLED,
                com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.refillableSolutionFromResolved(disabled));
        var refillPolicy = com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.refillableSolutionFromResolved(refillable);
        assertTrue(refillPolicy.enabled());
        assertEquals("shardglass", refillPolicy.solutionName());
        var transferPolicy = com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.solutionTransferFromResolved(refillable);
        assertTrue(transferPolicy.enabled());
        assertFalse(transferPolicy.canSend());
        assertTrue(transferPolicy.canReceive());
    }

    @Test void shippedProduceProfilesBindFoodRefillReceiveOnly() throws Exception {
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        var loader = ItemFoodSolutionProfileTest.class;
        for (var id : java.util.List.of("tomato", "potato", "aloe", "poppy")) {
            try (var stream = loader.getResourceAsStream("/data/moonstation14/moonstation14/item_food_solution_profile/" + id + ".json")) {
                assertNotNull(stream, id);
                var profile = ItemFoodSolutionProfile.CODEC.parse(ops, JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
                assertTrue(profile.refillable(), id);
                assertEquals("food", profile.solutionName(), id);
                var refill = com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.refillableSolutionFromResolved(profile);
                var transfer = com.juicyslew.moonstation14.item.custom.ItemFoodSolutionItem.solutionTransferFromResolved(profile);
                assertTrue(refill.enabled(), id);
                assertTrue(transfer.canReceive(), id);
                assertFalse(transfer.canSend(), id);
                assertNull(((IReagentTrait) () -> 2f).drainableSolution(null, null));
            }
        }
        try (var stream = loader.getResourceAsStream("/data/moonstation14/moonstation14/item_food_solution_profile/wheat_bushel.json")) {
            assertNotNull(stream);
            var profile = ItemFoodSolutionProfile.CODEC.parse(ops, JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))).getOrThrow();
            assertFalse(profile.refillable());
        }
    }
}
