package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class PlantSpeciesMutationCycleTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void mainCatalogSpeciesMutationChangesIdentityOnlyAndPublishesNoOutput() {
        var authored = com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.resources();
        var names = authored.keySet().stream().map(ResourceLocation::getPath).toArray(String[]::new);
        var manager = new PrototypeManager(); manager.register(ModSeedProfiles.TYPE);
        manager.reload(ModSeedProfiles.TYPE, com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResources("seed_profile", names));
        var seeds = manager.snapshot(ModSeedProfiles.TYPE);
        var source = seeds.get(id("tomato")); var targetId = id("blue_tomato"); var target = seeds.get(targetId);
        assertNotNull(source); assertNotNull(target); assertTrue(source.mutationTargets().contains(targetId));
        var plant = new PlantInstance(50, 50, 0); plant.plant(id("tomato"), source, 0); plant.adjustMutationLevel(1);
        var beforeAge = plant.age(); var beforeHealth = plant.health(); var beforeReady = plant.ready();
        var random = new ScriptedRandom(0, 0);
        PlantMutationSystem.cycle(plant, table(species(1)), random, () -> .5f, null, null, seeds);
        assertEquals(targetId, plant.seed()); assertEquals(targetId, plant.genetics().species());
        assertEquals(target.packet(), plant.genetics().profile().packet());
        assertEquals(target.product(), plant.genetics().profile().product());
        assertEquals(target.visualKey(), plant.genetics().profile().visualKey());
        assertEquals(beforeAge, plant.age()); assertEquals(beforeHealth, plant.health()); assertEquals(beforeReady, plant.ready());
    }

    private record Profiles(SeedProfile wheat, SeedProfile tomato) { }
    private static Profiles profiles() {
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        var names = com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.resources().keySet().stream()
                .map(ResourceLocation::getPath).toArray(String[]::new);
        raw.putAll(com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResources("seed_profile", names));
        var manager = new PrototypeManager(); manager.register(ModSeedProfiles.TYPE); manager.reload(ModSeedProfiles.TYPE, raw);
        var catalog = manager.snapshot(ModSeedProfiles.TYPE);
        return new Profiles(catalog.get(id("wheat")), catalog.get(id("tomato")));
    }

    private static PlantMutationTable.Entry species(float odds) {
        return new PlantMutationTable.Entry("species", odds, true, true, false,
                PlantMutationTable.EffectKind.SPECIES, Optional.empty(), Optional.empty(), "");
    }
    private static PlantMutationTable.Entry stat(float odds) {
        return new PlantMutationTable.Entry("stat", odds, true, true, false,
                PlantMutationTable.EffectKind.STAT,
                Optional.of(PlantStatMutation.numeric(PlantStatMutation.Target.IDEAL_HEAT, 263, 323, 5)), Optional.empty(), "");
    }
    private static PlantMutationTable table(PlantMutationTable.Entry... entries) { return new PlantMutationTable(List.of(entries)); }
    private static final class ScriptedRandom implements RandomGenerator {
        final double[] rolls; final int pick; int doubles, ints, floats;
        ScriptedRandom(int pick, double... rolls) { this.pick = pick; this.rolls = rolls; }
        @Override public long nextLong() { throw new AssertionError("unexpected nextLong"); }
        @Override public double nextDouble() { return rolls[doubles++]; }
        @Override public int nextInt(int bound) { ints++; assertTrue(pick >= 0 && pick < bound); return pick; }
        @Override public float nextFloat() { floats++; return .5f; }
    }

    @Test void orderedTargetsResolveOnlyAfterUniformPickAndMissingChosenTargetDoesNotRetarget() {
        var p = profiles();
        var targetIds = List.of(id("absent"), id("tomato"));
        var source = new SeedProfile(p.wheat.packet(), p.wheat.product(), p.wheat.visualKey(), p.wheat.biology(), p.wheat.lifecycle(),
                p.wheat.chemicals(), p.wheat.consumeGasses(), p.wheat.exudeGasses(), targetIds, p.wheat.scream(), p.wheat.sourceName());
        var catalog = new PrototypeCatalog<SeedProfile>(Map.of(id("wheat"), source, id("tomato"), p.tomato));

        var sourceWithoutTargets = new SeedProfile(p.wheat.packet(), p.wheat.product(), p.wheat.visualKey(), p.wheat.biology(), p.wheat.lifecycle(),
                p.wheat.chemicals(), p.wheat.consumeGasses(), p.wheat.exudeGasses(), List.of(), p.wheat.scream(), p.wheat.sourceName());
        var noTargets = new PlantInstance(50, 50, 0); noTargets.plant(id("wheat"), sourceWithoutTargets, 0); noTargets.adjustMutationLevel(1);
        var noPick = new ScriptedRandom(0, 0);
        PlantMutationSystem.cycle(noTargets, table(species(1)), noPick, () -> .5f, null, null, catalog);
        assertEquals(1, noPick.doubles); assertEquals(0, noPick.ints);

        var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), source, 0); plant.adjustMutationLevel(1);
        var random = new ScriptedRandom(0, 0, 0); // uniformly select missing target index 0; never filter toward tomato.
        var result = PlantMutationSystem.cycle(plant, table(species(1), stat(1)), random, () -> .5f, null, null, catalog);
        assertEquals(2, random.doubles); assertEquals(1, random.ints); assertEquals(1, random.floats);
        assertEquals(id("wheat"), plant.seed());
        assertTrue(result.unsupported().contains("species:species_target_missing:moonstation14:absent"));
        assertNotEquals(source.biology().idealHeat(), plant.genetics().profile().biology().idealHeat(),
                "the later source-ordered stat still executes after the selected missing species profile");

        var noLaterChange = new PlantInstance(50, 50, 0); noLaterChange.plant(id("wheat"), source, 0); noLaterChange.adjustMutationLevel(1);
        var chosenValid = new ScriptedRandom(1, 0);
        PlantMutationSystem.cycle(noLaterChange, table(species(1)), chosenValid, () -> .5f, null, null, catalog);
        assertEquals(id("tomato"), noLaterChange.seed());
        assertSame(p.tomato.product(), noLaterChange.effectiveProfile().product());
    }

    @Test void speciesThenStatCommitsTransformedResolvedProfileAtomicallyAndPreservesHolderState() {
        var p = profiles(); var targets = List.of(id("tomato"));
        var source = new SeedProfile(p.wheat.packet(), p.wheat.product(), p.wheat.visualKey(), p.wheat.biology(), p.wheat.lifecycle(),
                p.wheat.chemicals(), p.wheat.consumeGasses(), p.wheat.exudeGasses(), targets, p.wheat.scream(), p.wheat.sourceName());
        var catalog = new PrototypeCatalog<SeedProfile>(Map.of(id("wheat"), source, id("tomato"), p.tomato));
        var plant = new PlantInstance(37, 61, 4); plant.plant(id("wheat"), source, 4); plant.adjustMutationLevel(10);
        plant.soil(37, 61, 2.5f, 3.5f); plant.pestLevel(2); plant.state(7, 42, 5, 1, true, false);
        long beforeRevision = plant.revision();
        var random = new ScriptedRandom(0, 0, 0);
        PlantMutationSystem.cycle(plant, table(species(1), stat(1)), random, () -> .5f, null, null, catalog);
        assertEquals(2, random.doubles, "species effect retains the ordinary per-entry chance roll count");
        assertEquals(1, random.ints); assertEquals(1, random.floats);
        assertEquals(id("tomato"), plant.seed());
        assertEquals(id("tomato"), plant.genetics().species());
        assertSame(p.tomato.product(), plant.genetics().profile().product());
        assertNotEquals(p.tomato.biology().idealHeat(), plant.genetics().profile().biology().idealHeat(), "later stat applies to transformed genetics");
        assertEquals(7, plant.age()); assertEquals(42, plant.health()); assertTrue(plant.ready());
        assertEquals(5, plant.lastProduceAge()); assertEquals(1, plant.skipAging());
        assertEquals(37, plant.water()); assertEquals(61, plant.nutrition()); assertEquals(2.5f, plant.weeds());
        assertEquals(3.5f, plant.toxins()); assertEquals(2, plant.pests());
        assertEquals(0, plant.mutationLevel()); assertEquals(beforeRevision + 3, plant.revision());
    }

    @Test void unavailableSelectedSpeciesDoesNotRerollButLaterMutationAndEdgesRemain() {
        var p = profiles();
        var source = new SeedProfile(p.wheat.packet(), p.wheat.product(), p.wheat.visualKey(), p.wheat.biology(), p.wheat.lifecycle(),
                p.wheat.chemicals(), p.wheat.consumeGasses(), p.wheat.exudeGasses(), List.of(id("tomato"), id("potato")), p.wheat.scream(), p.wheat.sourceName());
        // Target profile exists; its product is deliberately rejected by the availability policy.
        var catalog = new PrototypeCatalog<SeedProfile>(Map.of(id("wheat"), source, id("tomato"), p.tomato,
                id("potato"), p.wheat));
        var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), source, 0); plant.adjustMutationLevel(1);
        var random = new ScriptedRandom(1, 0, 0, 0); // selected unavailable potato; no reroll to supported tomato
        var result = PlantMutationSystem.cycle(plant, table(species(1), stat(1)), random, () -> .5f, null, null,
                catalog, selected -> selected.equals(id("tomato")));
        assertEquals(List.of(id("tomato"), id("potato")), source.mutationTargets());
        assertEquals(id("wheat"), plant.seed());
        assertTrue(result.unsupported().contains("species:species_target_unsupported:moonstation14:potato"));
        assertEquals(2, random.doubles); assertEquals(1, random.ints);
        assertNotEquals(source.biology().idealHeat(), plant.genetics().profile().biology().idealHeat());
        assertTrue(source.mutationTargets().contains(id("potato")), "unavailable outgoing edge remains authored");
    }

    @Test void configuredSpeciesRollUsesStrictChanceEndpointAndDescriptorLoadsAsPayloadFreeEffect() {
        var source = profiles().wheat;
        var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), source, 0); plant.adjustMutationLevel(1);
        var random = new ScriptedRandom(0, 1); // strict nextDouble() < 1: endpoint fails.
        PlantMutationSystem.cycle(plant, table(species(1)), random, () -> .5f, null, null,
                new PrototypeCatalog<>(Map.of(id("wheat"), source)));
        assertEquals(1, random.doubles); assertEquals(0, random.ints); assertEquals(id("wheat"), plant.seed());

        String path = "data/moonstation14/moonstation14/plant_mutation_table/random_plant_mutations.json";
        try (var in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in);
            var json = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var manager = new PrototypeManager(); manager.register(ModPlantMutations.TYPE);
            manager.reload(ModPlantMutations.TYPE, Map.of(ModPlantMutations.tableId("random_plant_mutations"), json));
            var loaded = manager.snapshot(ModPlantMutations.TYPE).get(ModPlantMutations.tableId("random_plant_mutations"));
            var changeSpecies = loaded.mutations().stream().filter(e -> e.name().equals("ChangeSpecies")).findFirst().orElseThrow();
            assertEquals(PlantMutationTable.EffectKind.SPECIES, changeSpecies.effectKind());
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
