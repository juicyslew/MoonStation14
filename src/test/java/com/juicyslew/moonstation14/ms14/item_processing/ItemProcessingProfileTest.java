package com.juicyslew.moonstation14.ms14.item_processing;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.item.ModItems;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemProcessingProfileTest {
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static JsonObject log(String output, Integer count) {
        var json = new JsonObject(); var component = new JsonObject(); component.addProperty("output", output);
        if (count != null) component.addProperty("count", count); json.add("log", component); return json;
    }
    private static JsonObject defaultLog() { var json = new JsonObject(); json.add("log", new JsonObject()); return json; }
    private static JsonObject butcher(String output) {
        var root = new JsonObject(); var b = new JsonObject(); var entries = new com.google.gson.JsonArray();
        var entry = new JsonObject(); entry.addProperty("output", output); entries.add(entry); b.add("outputs", entries); root.add("butcherable", b); return root;
    }
    private static JsonObject slicing(String output) {
        var root = new JsonObject(); var slice = new JsonObject(); slice.addProperty("output", output);
        root.add("sliceable_food", slice); return root;
    }
    private static PrototypeManager manager() {
        var manager = new PrototypeManager(); manager.register(ModItemProcessingProfiles.TYPE); return manager;
    }

    @Test void resolvedLogProfileInheritsOneOutputAndDefaultTwoCount() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        var base = new JsonObject(); base.addProperty("abstract", true);
        base.add("log", defaultLog().getAsJsonObject("log"));
        raw.put(id("test:log_base"), base);
        var child = new JsonObject(); child.addProperty("parent", "test:log_base"); raw.put(id("test:log_source"), child);
        var outputOverride = new JsonObject(); outputOverride.addProperty("parent", "test:log_base");
        var outputOnly = new JsonObject(); outputOnly.addProperty("output",
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.STEEL.get()).toString());
        outputOverride.add("log", outputOnly); raw.put(id("test:log_output_override"), outputOverride);
        var countOverride = new JsonObject(); countOverride.addProperty("parent", "test:log_base");
        var countOnly = new JsonObject(); countOnly.addProperty("count", 1);
        countOverride.add("log", countOnly); raw.put(id("test:log_count_override"), countOverride);
        var manager = manager(); manager.reload(ModItemProcessingProfiles.TYPE, raw);
        assertNull(manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:log_base")));
        var resolved = manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:log_source"));
        assertNotNull(resolved.log());
        assertEquals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WOOD.get()), resolved.log().output());
        assertEquals(2, resolved.log().count());
        var outputChild = manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:log_output_override"));
        assertEquals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.STEEL.get()), outputChild.log().output());
        assertEquals(2, outputChild.log().count(), "output override retains parent default count");
        var countChild = manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:log_count_override"));
        assertEquals(resolved.log().output(), countChild.log().output(), "count override retains inherited wood output");
        assertEquals(1, countChild.log().count());
    }

    @Test void explicitCountAndInvalidReferenceOrCountAreValidated() {
        var good = Map.of(id("test:log"), log("minecraft:iron_ingot", 1));
        var manager = manager(); manager.reload(ModItemProcessingProfiles.TYPE, good);
        assertEquals(1, manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:log")).log().count());
        var original = manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:log"));
        assertThrows(PrototypeLoadException.class, () -> manager.reload(ModItemProcessingProfiles.TYPE,
                Map.of(id("test:bad"), log("missing:no_such_item", 2))));
        assertSame(original, manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:log")),
                "failed candidate publication retains the last valid resolved catalog");
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModItemProcessingProfiles.TYPE,
                Map.of(id("test:bad"), log("minecraft:iron_ingot", 0))));
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModItemProcessingProfiles.TYPE,
                Map.of(id("test:bad"), log("minecraft:iron_ingot", -1))));
    }

    @Test void authenticLocalWoodSteelAndPaperOutputsKeepSeparateEntityCounts() {
        var wood = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WOOD.get());
        var steel = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.STEEL.get());
        var paper = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.PAPER.get());
        var raw = Map.of(id("test:towercap_log"), defaultLog(),
                id("test:steelcap_log"), log(steel.toString(), 1), id("test:papercane_log"), log(paper.toString(), 2));
        var manager = manager(); manager.reload(ModItemProcessingProfiles.TYPE, raw);
        var catalog = manager.snapshot(ModItemProcessingProfiles.TYPE);
        assertEquals(2, catalog.get(id("test:towercap_log")).log().count());
        assertEquals(1, catalog.get(id("test:steelcap_log")).log().count());
        assertEquals(2, catalog.get(id("test:papercane_log")).log().count());
        assertEquals(wood, catalog.get(id("test:towercap_log")).log().output());
        assertEquals(steel, catalog.get(id("test:steelcap_log")).log().output());
        assertEquals(paper, catalog.get(id("test:papercane_log")).log().output());
    }

    @Test void butcherCompositionIsOrderedAndInheritsDefaultsAndOverrides() {
        var output = "minecraft:iron_ingot";
        var base = butcher(output).getAsJsonObject("butcherable");
        var root = new JsonObject(); root.addProperty("abstract", true); root.add("butcherable", base);
        var child = new JsonObject(); child.addProperty("parent", "test:butcher_base");
        var override = butcher(output); override.getAsJsonObject("butcherable").addProperty("delay_seconds", 4f);
        var manager = manager(); manager.reload(ModItemProcessingProfiles.TYPE, Map.of(
                id("test:butcher_base"), root, id("test:butcher_child"), child, id("test:butcher_override"), override));
        var resolved = manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:butcher_child")).butcherable();
        assertEquals(8f, resolved.delaySeconds()); assertEquals(ItemProcessingProfile.Butcherable.Type.KNIFE, resolved.type());
        assertEquals(1, resolved.outputs().get(0).count()); assertEquals(id(output), resolved.outputs().get(0).output());
        assertEquals(4f, manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:butcher_override")).butcherable().delaySeconds());
        var ordered = butcher(output); var entries = ordered.getAsJsonObject("butcherable").getAsJsonArray("outputs");
        var second = new JsonObject(); second.addProperty("output", "minecraft:apple"); entries.add(second);
        manager.reload(ModItemProcessingProfiles.TYPE, Map.of(id("test:ordered"), ordered));
        assertEquals(id("minecraft:apple"), manager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:ordered")).butcherable().outputs().get(1).output());
    }

    @Test void butcherRejectsInvalidReferencesCountsTypesAndDelays() {
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModItemProcessingProfiles.TYPE, Map.of(id("test:bad"), butcher("missing:no_item"))));
        var zero = butcher("minecraft:iron_ingot"); zero.getAsJsonObject("butcherable").getAsJsonArray("outputs").get(0).getAsJsonObject().addProperty("count", 0);
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModItemProcessingProfiles.TYPE, Map.of(id("test:bad"), zero)));
        var badType = butcher("minecraft:iron_ingot"); badType.getAsJsonObject("butcherable").addProperty("type", "spike");
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModItemProcessingProfiles.TYPE, Map.of(id("test:bad"), badType)));
        var badDelay = butcher("minecraft:iron_ingot"); badDelay.getAsJsonObject("butcherable").addProperty("delay_seconds", 0);
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModItemProcessingProfiles.TYPE, Map.of(id("test:bad"), badDelay)));
    }

    @Test void sliceCompositionDefaultsAndInvalidOutputsAreChecked() {
        var profileManager = managerWithSlicingCatalogs();
        var sliceBase = new JsonObject(); sliceBase.addProperty("abstract", true);
        var cheese = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.CHEESE_SLICE.get()).toString();
        sliceBase.add("sliceable_food", slicing(cheese).getAsJsonObject("sliceable_food"));
        var sliceChild = new JsonObject(); sliceChild.addProperty("parent", "test:slice_base");
        var staged = profileManager.stage(Map.of(
                com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE, Map.of(id(cheese), foodSolution(300)),
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, Map.of(id(cheese), sourceSolution(300)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:slice_base"), sliceBase, id("test:slice"), sliceChild)));
        assertTrue(profileManager.commit(staged));
        assertNull(profileManager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:slice_base")));
        var slice = profileManager.snapshot(ModItemProcessingProfiles.TYPE).get(id("test:slice")).sliceableFood();
        assertEquals(id(cheese), slice.output());
        assertEquals(5, slice.count());
        assertEquals(1f, slice.delaySeconds());
        var validSnapshot = profileManager.snapshot();
        assertThrows(IllegalArgumentException.class, () -> profileManager.stage(Map.of(
                com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE, Map.of(),
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, Map.of())));
        assertSame(validSnapshot, profileManager.snapshot(), "stale ingestion candidate cannot displace current catalogs");
        var noProfile = managerWithSlicingCatalogs();
        assertThrows(IllegalArgumentException.class, () -> noProfile.reload(Map.of(
                com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE, Map.of(),
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, Map.of(),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:bad_slice"), slicing(cheese)))));
        var nonTrait = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.STEEL.get()).toString();
        assertThrows(IllegalArgumentException.class, () -> managerWithSlicingCatalogs().reload(Map.of(
                com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE, Map.of(id(nonTrait), foodSolution(300)),
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, Map.of(id(nonTrait), sourceSolution(300)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:bad_slice"), slicing(nonTrait)))));
        var drink = foodSolution(300); drink.getAsJsonObject("edible").addProperty("kind", "DRINK");
        assertThrows(IllegalArgumentException.class, () -> managerWithSlicingCatalogs().reload(Map.of(
                com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE, Map.of(id(cheese), drink),
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, Map.of(id(cheese), sourceSolution(300)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:bad_slice"), slicing(cheese)))));
        var shardglass = sourceSolution(300); shardglass.addProperty("solution_name", "shardglass");
        var atomic = managerWithSlicingCatalogs();
        assertThrows(IllegalArgumentException.class, () -> atomic.reload(Map.of(
                com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE, Map.of(id(cheese), foodSolution(300)),
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, Map.of(id(cheese), shardglass),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:bad_named_slice"), slicing(cheese)))));
        assertTrue(atomic.snapshot(ModItemProcessingProfiles.TYPE).asMap().isEmpty(), "named non-food output candidate is rejected atomically");
        var noCapacity = foodSolution(0);
        assertThrows(IllegalArgumentException.class, () -> managerWithSlicingCatalogs().reload(Map.of(
                com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE, Map.of(id(cheese), noCapacity),
                com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE, Map.of(id(cheese), sourceSolution(0)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:bad_slice"), slicing(cheese)))));
    }

    @Test void encodedCatalogValidationUsesCandidateAndRejectsAtomically() {
        var ingestionType = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE;
        var foodSolutionType = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE;
        var source = managerWithSlicingCatalogs();
        var cheese = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.CHEESE_SLICE.get()).toString();
        source.reload(Map.of(ingestionType, Map.of(id(cheese), foodSolution(300)),
                foodSolutionType, Map.of(id(cheese), sourceSolution(300)),
                ModItemProcessingProfiles.TYPE, Map.of(id("test:slice"), slicing(cheese))));
        var snapshot = source.encodePublishedCatalogs();
        var destination = managerWithSlicingCatalogs();
        destination.publishEncodedCatalogs(snapshot);
        var before = destination.snapshot();
        var invalid = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, JsonObject>>(snapshot);
        var profiles = new LinkedHashMap<>(invalid.get(ModItemProcessingProfiles.TYPE.typeId()));
        var nonTrait = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.STEEL.get()).toString();
        profiles.get(id("test:slice")).getAsJsonObject("sliceable_food").addProperty("output", nonTrait);
        invalid.put(ModItemProcessingProfiles.TYPE.typeId(), profiles);
        assertThrows(IllegalArgumentException.class, () -> destination.publishEncodedCatalogs(invalid));
        assertSame(before, destination.snapshot(), "invalid encoded snapshot must preserve prior published catalogs");
    }

    private static PrototypeManager managerWithSlicingCatalogs() {
        var manager = new PrototypeManager();
        manager.register(com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModItemProcessingProfiles.TYPE);
        return manager;
    }

    private static JsonObject foodSolution(int capacity) {
        var root = new JsonObject(); var edible = new JsonObject(); edible.addProperty("kind", "FOOD");
        root.add("edible", edible); return root;
    }

    private static JsonObject sourceSolution(int capacity) {
        var root = new JsonObject(); root.addProperty("capacity_cents", capacity);
        root.add("contents_cents", new JsonObject()); return root;
    }
}
