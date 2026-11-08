package com.juicyslew.moonstation14.ms14.item_processing;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemProcessingCandidateReferenceTest {
    private static ResourceLocation id(String name) { return ResourceLocation.parse(name); }
    private static final ResourceLocation CHEESE = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.CHEESE_SLICE.get());
    private static final ResourceLocation STEEL = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.STEEL.get());

    private static PrototypeManager manager() {
        var manager = new PrototypeManager();
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModItemProcessingProfiles.TYPE);
        return manager;
    }

    private static JsonObject ingestion(int capacity, String kind) {
        var root = new JsonObject(); var edible = new JsonObject(); edible.addProperty("kind", kind);
        root.add("edible", edible); return root;
    }

    private static JsonObject sourceSolution(int capacity) {
        var root = new JsonObject(); root.addProperty("capacity_cents", capacity);
        root.add("contents_cents", new JsonObject()); return root;
    }

    private static JsonObject slice(String output) {
        var root = new JsonObject(); var config = new JsonObject(); config.addProperty("output", output);
        root.add("sliceable_food", config); return root;
    }

    @Test void stagedCombinedCandidateUsesInheritedFoodOutputAndRejectsAgainstCandidateAtomically() {
        var manager = manager();
        var base = new JsonObject(); base.addProperty("abstract", true); base.add("sliceable_food", slice(CHEESE.toString()).get("sliceable_food"));
        var child = new JsonObject(); child.addProperty("parent", "test:slice_base");
        var prepared = manager.stage(Map.of(ModIngestionProfiles.TYPE, Map.of(CHEESE, ingestion(300, "FOOD")),
                ModItemFoodSolutionProfiles.TYPE, Map.of(CHEESE, sourceSolution(300)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:slice_base"), base, id("test:slice_child"), child)));
        assertTrue(manager.commit(prepared));
        assertNull(manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:slice_base")));
        assertEquals(CHEESE, manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:slice_child")).sliceableFood().output());

        var before = manager.snapshot();
        assertThrows(IllegalArgumentException.class, () -> manager.stage(Map.of(ModIngestionProfiles.TYPE, Map.of(),
                ModItemFoodSolutionProfiles.TYPE, Map.of())));
        assertSame(before, manager.snapshot(), "removing the candidate output profile must preserve published catalogs");
        assertRejected(manager, Map.of(ModIngestionProfiles.TYPE, Map.of(CHEESE, ingestion(0, "FOOD")),
                ModItemFoodSolutionProfiles.TYPE, Map.of(CHEESE, sourceSolution(0))));
        assertRejected(manager, Map.of(ModIngestionProfiles.TYPE, Map.of(CHEESE, ingestion(300, "DRINK")),
                ModItemFoodSolutionProfiles.TYPE, Map.of(CHEESE, sourceSolution(300))));
        assertRejected(manager, Map.of(ModIngestionProfiles.TYPE, Map.of(STEEL, ingestion(300, "FOOD")),
                ModItemFoodSolutionProfiles.TYPE, Map.of(STEEL, sourceSolution(300)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:steel_slice"), slice(STEEL.toString()))));
    }

    @Test void encodedCandidateChecksFoodAndTraitAndRejectsWithoutReplacingPreviousSnapshot() {
        var source = manager();
        source.reload(Map.of(ModIngestionProfiles.TYPE, Map.of(CHEESE, ingestion(300, "FOOD")),
                ModItemFoodSolutionProfiles.TYPE, Map.of(CHEESE, sourceSolution(300)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:slice"), slice(CHEESE.toString()))));
        var validSnapshot = source.encodePublishedCatalogs();
        var receiver = manager();
        receiver.publishEncodedCatalogs(validSnapshot);
        assertEquals(CHEESE, receiver.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:slice")).sliceableFood().output());
        assertEncodedRejected(receiver, validSnapshot, STEEL);
        var drinkSnapshot = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, JsonObject>>(validSnapshot);
        var ingestionProfiles = new LinkedHashMap<>(drinkSnapshot.get(ModIngestionProfiles.TYPE.typeId()));
        ingestionProfiles.get(CHEESE).getAsJsonObject("edible").addProperty("kind", "DRINK");
        drinkSnapshot.put(ModIngestionProfiles.TYPE.typeId(), ingestionProfiles);
        var beforeDrink = receiver.snapshot();
        assertThrows(IllegalArgumentException.class, () -> receiver.publishEncodedCatalogs(drinkSnapshot));
        assertSame(beforeDrink, receiver.snapshot(), "encoded non-food target must preserve previous snapshot");

        var missing = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, JsonObject>>(validSnapshot);
        missing.put(ModIngestionProfiles.TYPE.typeId(), Map.of());
        var before = receiver.snapshot();
        assertThrows(IllegalArgumentException.class, () -> receiver.publishEncodedCatalogs(missing));
        assertSame(before, receiver.snapshot(), "invalid encoded reference must preserve prior published catalogs");
    }

    private static void assertRejected(PrototypeManager manager,
            Map<com.juicyslew.moonstation14.ms14.prototype.PrototypeType<?>, ? extends Map<ResourceLocation, JsonObject>> candidate) {
        var before = manager.snapshot();
        assertThrows(IllegalArgumentException.class, () -> manager.stage(candidate));
        assertSame(before, manager.snapshot(), "invalid staged candidate must preserve published catalogs");
    }

    private static void assertEncodedRejected(PrototypeManager receiver,
            Map<ResourceLocation, Map<ResourceLocation, JsonObject>> validSnapshot, ResourceLocation output) {
        var invalid = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, JsonObject>>(validSnapshot);
        var profiles = new LinkedHashMap<>(invalid.get(ModItemProcessingProfiles.TYPE.typeId()));
        profiles.get(id("test:slice")).getAsJsonObject("sliceable_food").addProperty("output", output.toString());
        invalid.put(ModItemProcessingProfiles.TYPE.typeId(), profiles);
        var before = receiver.snapshot();
        assertThrows(IllegalArgumentException.class, () -> receiver.publishEncodedCatalogs(invalid));
        assertSame(before, receiver.snapshot(), "invalid encoded candidate must preserve published catalogs");
    }
}
