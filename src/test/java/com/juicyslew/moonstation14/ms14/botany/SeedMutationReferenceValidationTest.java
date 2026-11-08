package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SeedMutationReferenceValidationTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    private static Map<ResourceLocation, JsonObject> catalog() {
        return new LinkedHashMap<>(BotanyTestSeedFixtures.mainCatalog());
    }

    private static PrototypeManager manager() {
        var manager = new PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        manager.reload(ModSeedProfiles.TYPE, catalog());
        return manager;
    }

    @Test void candidateRejectsDanglingInheritedTargetWithoutChangingPublishedSnapshot() {
        var manager = manager();
        var before = manager.snapshot(ModSeedProfiles.TYPE);
        var candidate = catalog();
        candidate.get(id("defaults")).add("mutation_targets", new com.google.gson.JsonArray());
        candidate.get(id("wheat")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:missing\"]"));
        var exception = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModSeedProfiles.TYPE, candidate)));
        assertTrue(exception.getMessage().contains("moonstation14:wheat"));
        assertTrue(exception.getMessage().contains("moonstation14:missing"));
        assertSame(before, manager.snapshot(ModSeedProfiles.TYPE));
        assertFalse(manager.hasStagedReload());
    }

    @Test void reservedDefaultsTemplateMayContainTargetsOverriddenByEveryConcreteLeaf() {
        var manager = manager();
        var candidate = catalog();
        candidate.get(id("defaults")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:missing\"]"));
        for (var leaf : candidate.keySet()) if (!leaf.equals(id("defaults"))) candidate.get(leaf).add("mutation_targets", new com.google.gson.JsonArray());
        assertTrue(manager.commit(manager.stage(Map.of(ModSeedProfiles.TYPE, candidate))));
    }

    @Test void inheritedDefaultsTargetIsValidatedOnConcreteLeafButUnusedTrueAbstractIsNot() {
        var manager = manager();
        var inheritedCandidate = catalog();
        inheritedCandidate.get(id("defaults")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:missing\"]"));
        inheritedCandidate.get(id("wheat")).remove("mutation_targets");
        for (var leaf : inheritedCandidate.keySet()) if (!leaf.equals(id("defaults")) && !leaf.equals(id("wheat"))) inheritedCandidate.get(leaf).add("mutation_targets", new com.google.gson.JsonArray());
        var before = manager.snapshot(ModSeedProfiles.TYPE);
        var error = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModSeedProfiles.TYPE, inheritedCandidate)));
        assertTrue(error.getMessage().contains("moonstation14:wheat"), error::getMessage);
        assertSame(before, manager.snapshot(ModSeedProfiles.TYPE));

        var candidate = catalog();
        var abstractBase = candidate.get(id("defaults")).deepCopy();
        abstractBase.addProperty("id", "moonstation14:abstract_base");
        abstractBase.addProperty("abstract", true);
        abstractBase.add("mutation_targets", JsonParser.parseString("[\"moonstation14:missing\"]"));
        candidate.put(id("abstract_base"), abstractBase);
        candidate.get(id("wheat")).addProperty("parent", "moonstation14:abstract_base");
        candidate.get(id("wheat")).add("mutation_targets", new com.google.gson.JsonArray());
        assertTrue(manager.commit(manager.stage(Map.of(ModSeedProfiles.TYPE, candidate))));
        assertFalse(manager.snapshot(ModSeedProfiles.TYPE).contains(id("abstract_base")));
    }

    @Test void resolvedInheritedTargetWorksInMutationConsumerAndCyclesRemainLegal() {
        var manager = manager();
        var candidate = catalog();
        candidate.get(id("wheat")).remove("mutation_targets");
        for (var leaf : candidate.keySet()) if (!leaf.equals(id("defaults")) && !leaf.equals(id("wheat"))) candidate.get(leaf).add("mutation_targets", new com.google.gson.JsonArray());
        candidate.get(id("defaults")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:tomato\"]"));
        candidate.get(id("tomato")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:tomato\"]"));
        var staged = manager.stage(Map.of(ModSeedProfiles.TYPE, candidate));
        assertTrue(manager.commit(staged));
        var seeds = manager.snapshot(ModSeedProfiles.TYPE);
        var plant = new PlantInstance(50, 50, 0);
        plant.plant(id("wheat"), seeds.get(id("wheat")), 0);
        plant.adjustMutationLevel(1);
        var entry = new PlantMutationTable.Entry("species", 1, true, true, false,
                PlantMutationTable.EffectKind.SPECIES, java.util.Optional.empty(), java.util.Optional.empty(), "");
        var result = PlantMutationSystem.cycle(plant, new PlantMutationTable(List.of(entry)), new java.util.Random(1),
                () -> 0.5f, null, null, seeds);
        assertEquals(id("tomato"), plant.seed());
        assertEquals(seeds.get(id("tomato")).product(), plant.effectiveProfile().product());
        assertTrue(result.unsupported().isEmpty());
    }

    @Test void encodedSnapshotRejectsDanglingReferenceAtomically() {
        var manager = manager();
        var before = manager.snapshot();
        var encoded = new LinkedHashMap<ResourceLocation, Map<ResourceLocation, JsonObject>>(manager.encodePublishedCatalogs());
        var seeds = new LinkedHashMap<>(encoded.get(ModSeedProfiles.TYPE.typeId()));
        seeds.get(id("wheat")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:absent\"]"));
        encoded.put(ModSeedProfiles.TYPE.typeId(), seeds);
        assertThrows(RuntimeException.class, () -> manager.publishEncodedCatalogs(encoded));
        assertSame(before, manager.snapshot());
    }
}
