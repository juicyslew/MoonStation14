package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeldItemConversionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void spawnOnUseInheritsDefaultsAndRoundTripsThroughFullProfileCodec() {
        var manager = new PrototypeManager();
        manager.register(ModItemProcessingProfiles.TYPE);
        var base = new JsonObject(); base.addProperty("abstract", true);
        var output = new JsonObject(); output.addProperty("output", BuiltInRegistries.ITEM.getKey(ModItems.FOOD_MEAT_WHEAT.get()).toString()); output.addProperty("count", 1);
        var outputs = new com.google.gson.JsonArray(); outputs.add(output);
        var spawn = new JsonObject(); spawn.add("outputs", outputs);
        base.add("spawn_items_on_use", spawn);
        var child = new JsonObject(); child.addProperty("parent", "moonstation14:conversion_base");
        var staged = manager.stage(Map.of(ModItemProcessingProfiles.TYPE, Map.of(id("conversion_base"), base, id("conversion_child"), child)));
        assertTrue(manager.commit(staged));
        var snapshot = manager.snapshot();
        var catalog = manager.snapshot(ModItemProcessingProfiles.TYPE);
        assertEquals(1, catalog.size());
        var resolved = catalog.get(id("conversion_child"));
        assertNull(resolved.spawnItemsOnUse().sound());
        assertEquals(1, resolved.spawnItemsOnUse().uses());
        var roundTrip = ItemProcessingProfile.CODEC.parse(JsonOps.INSTANCE,
                ItemProcessingProfile.CODEC.encodeStart(JsonOps.INSTANCE, resolved).getOrThrow()).getOrThrow();
        assertEquals(resolved, roundTrip);
        assertSame(snapshot, manager.snapshot());

        var configured = new ItemProcessingProfile(null, null, null,
                new ItemProcessingProfile.SpawnItemsOnUse(List.of(new ItemProcessingProfile.ItemOutput(
                        BuiltInRegistries.ITEM.getKey(ModItems.FOOD_MEAT_WHEAT.get()), 1)), 1,
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "botany/produce/slime_squish")));
        assertEquals(configured, ItemProcessingProfile.CODEC.parse(JsonOps.INSTANCE,
                ItemProcessingProfile.CODEC.encodeStart(JsonOps.INSTANCE, configured).getOrThrow()).getOrThrow());
    }

    @Test void invalidSpawnUsesDoesNotPublishCandidate() {
        var manager = new PrototypeManager(); manager.register(ModItemProcessingProfiles.TYPE);
        var before = manager.snapshot();
        var invalid = new JsonObject(); var spawn = new JsonObject();
        var output = new JsonObject(); output.addProperty("output", BuiltInRegistries.ITEM.getKey(ModItems.FOOD_MEAT_WHEAT.get()).toString());
        var outputs = new com.google.gson.JsonArray(); outputs.add(output); spawn.add("outputs", outputs); spawn.addProperty("uses", 2);
        invalid.add("spawn_items_on_use", spawn);
        assertThrows(IllegalArgumentException.class, () -> manager.stage(Map.of(ModItemProcessingProfiles.TYPE, Map.of(id("bad_conversion"), invalid))));
        assertSame(before, manager.snapshot());
    }

    @Test void outputsAreExpandedIntoIndependentFreshCountOneStacks() {
        var id = BuiltInRegistries.ITEM.getKey(ModItems.FOOD_MEAT_WHEAT.get());
        var stacks = HeldItemConversionService.prepareOutputs(new ItemProcessingProfile.SpawnItemsOnUse(
                List.of(new ItemProcessingProfile.ItemOutput(id, 2)), 1, null));
        assertNotNull(stacks);
        assertEquals(2, stacks.size());
        assertNotSame(stacks.get(0), stacks.get(1));
        for (var stack : stacks) {
            assertEquals(1, stack.getCount());
            assertEquals(ModItems.FOOD_MEAT_WHEAT.get(), stack.getItem());
            assertNull(stack.get(ModDataComponents.GENETIC_VALUE.get()));
            assertNull(stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
            assertNull(stack.get(ModDataComponents.PRODUCE_INSTANCE.get()));
            var source = ItemFoodSolutions.resolve(id, null);
            if (source != null) {
                var reagent = stack.get(ModDataComponents.REAGENT.get());
                assertNotNull(reagent, "profiled conversion output receives its own source solution");
                var expected = new java.util.LinkedHashMap<String, Long>();
                source.contentsCents().forEach((reagentId, amount) -> expected.put(reagentId.getPath(), amount));
                var actual = new java.util.LinkedHashMap<String, Long>();
                reagent.centContents().forEach((key, amount) -> actual.put(key.location().getPath(), amount));
                assertEquals(expected, actual, "output chemistry comes from its own resolved profile");
            } else {
                assertNull(stack.get(ModDataComponents.REAGENT.get()), "unprofiled outputs remain unmodified");
            }
        }
    }

    @Test void invalidConversionFailsBeforeAnyOutputsCanBePrepared() {
        assertNull(HeldItemConversionService.prepareOutputs(new ItemProcessingProfile.SpawnItemsOnUse(List.of(), 1, null)));
        assertNull(HeldItemConversionService.prepareOutputs(new ItemProcessingProfile.SpawnItemsOnUse(
                List.of(new ItemProcessingProfile.ItemOutput(ResourceLocation.parse("missing:no_item"), 1)), 1, null)));
        assertNull(HeldItemConversionService.prepareOutputs(new ItemProcessingProfile.SpawnItemsOnUse(
                List.of(new ItemProcessingProfile.ItemOutput(BuiltInRegistries.ITEM.getKey(ModItems.FOOD_COCOA_BEANS.get()), 1)), 2, null)));
    }
}
