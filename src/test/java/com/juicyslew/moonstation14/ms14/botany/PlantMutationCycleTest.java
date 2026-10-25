package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class PlantMutationCycleTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static SeedProfile wheat() {
        return BotanyTestSeedFixtures.resolved(id("wheat"));
    }
    static PlantMutationTable sourceTable() {
        String path = "data/moonstation14/moonstation14/plant_mutation_table/random_plant_mutations.json";
        try (var in = PlantMutationCycleTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in);
            var json = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
            manager.register(ModPlantMutations.TYPE); manager.reload(ModPlantMutations.TYPE, Map.of(ModPlantMutations.tableId("random_plant_mutations"), json));
            return manager.snapshot(ModPlantMutations.TYPE).get(ModPlantMutations.tableId("random_plant_mutations"));
        } catch (Exception e) { throw new AssertionError(e); }
    }
    record ChemicalCatalogs(com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<WeightedRandomFillSolution> tables,
                                    com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagents) { }
    static ChemicalCatalogs chemicalCatalogs() {
        String path = "data/moonstation14/moonstation14/weighted_random_fill_solution/randompickbotanyreagent.json";
        com.google.gson.JsonObject json;
        try (var in = PlantMutationCycleTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in); json = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
        var table = WeightedRandomFillSolution.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        var reagents = new java.util.LinkedHashMap<ResourceLocation, com.juicyslew.moonstation14.component.codec.json.ReagentData>();
        for (var reagentId : table.fills().stream().flatMap(g -> g.reagents().stream()).distinct().toList()) reagents.put(reagentId,
                new com.juicyslew.moonstation14.component.codec.json.ReagentData(reagentId.toString(), "", "", "", "", "", 0,
                Map.of(), 400, 200, com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum.NONE, List.of(), Map.of(),
                new com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData(null, null), 0, "fill-", false, 0));
        return new ChemicalCatalogs(new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(Map.of(id("randompickbotanyreagent"), table)),
                new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(reagents));
    }
    private static class Controlled implements RandomGenerator {
        int doubles, floats, ints; double roll = 1; float floatRoll = .5f; int intRoll = 1;
        @Override public long nextLong() { throw new AssertionError("unexpected nextLong"); }
        @Override public double nextDouble() { doubles++; return roll; }
        @Override public float nextFloat() { floats++; return floatRoll; }
        @Override public int nextInt(int origin, int bound) { ints++; return Math.min(bound - 1, origin + intRoll - 1); }
        @Override public int nextInt(int bound) { ints++; return Math.min(bound - 1, intRoll - 1); }
    }

    @Test void dueCyclePollsFirstThenMutatesUsesNewProfileAndConsumes27Rolls() {
        var profile = wheat(); var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), profile, 0);
        plant.adjustMutationLevel(40);
        var random = new Controlled() {
            @Override public double nextDouble() { doubles++; return doubles == 2 ? .89 : 1; }
        }; // Only Slippery succeeds; strict < makes capped chance 1 fail at exactly 1.
        var chemistry = chemicalCatalogs();
        var order = new java.util.ArrayList<String>();
        var tray = new TrayProfile(3, 15, 50, 50, false, 10000, 10000);
        var result = PlantMetabolismEvaluator.poll(plant, tray, plant::effectiveProfile, 15, p -> order.add("reagents"), random,
                Optional.of(new PlantMetabolismEvaluator.Environment(101.3f, 293.15f)), p -> {
                    order.add("mutation");
                    var mutationResult = PlantMutationSystem.cycle(p, sourceTable(), random, () -> .5f, chemistry.tables(), chemistry.reagents());
                    assertEquals(25f, mutationResult.severity());
                    assertTrue(p.genetics().mutations().stream().anyMatch(x -> x.name().equals("Slippery")
                            && !x.appliesToPlant() && x.persists() && x.slippery().equals(com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition.DEFAULT)));
                });
        assertTrue(result.isPresent()); assertEquals(List.of("reagents", "mutation"), order);
        assertEquals(27, random.doubles); assertEquals(0, plant.mutationLevel());
    }

    @Test void slipperyAcquisitionCapturesResolvedPayloadOnlyOnSuccessfulPersistentRoll() {
        var profile = wheat(); var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), profile, 0); plant.adjustMutationLevel(25);
        var table = sourceTable(); var slippery = table.mutations().stream().filter(e -> e.name().equals("Slippery")).findFirst().orElseThrow();
        assertEquals(PlantMutationTable.EffectKind.SLIPIFY, slippery.effectKind());
        assertEquals(com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition.DEFAULT, slippery.slippery().orElseThrow());
        var rng = new Controlled() { @Override public double nextDouble() { doubles++; return doubles == 2 ? 0 : 1; } };
        var before = plant.genetics();
        PlantMutationSystem.cycle(plant, table, rng, () -> .5f, chemicalCatalogs().tables(), chemicalCatalogs().reagents());
        assertEquals(27, rng.doubles);
        var recorded = plant.genetics().mutations().getFirst();
        assertEquals("Slippery", recorded.name()); assertFalse(recorded.appliesToPlant()); assertTrue(recorded.persists());
        assertTrue(before.mutations().isEmpty(), "capturing an acquisition must not mutate the prior shared genetics snapshot");
        assertTrue(plant.effectiveProfile().equals(profile), "SLIPIFY does not modify living plant profile");
    }

    @Test void slipifyRequiresSuccessfulPersistentRollAndFirstCapturedSnapshotWins() {
        var profile = wheat(); var chemistry = chemicalCatalogs();
        var base = new PlantMutationTable.Entry("Slippery", 1, false, true, true, PlantMutationTable.EffectKind.SLIPIFY,
                Optional.empty(), Optional.empty(), "", Optional.empty(), Optional.empty(), Optional.of(com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition.DEFAULT));
        var none = new PlantInstance(50, 50, 0); none.plant(id("wheat"), profile, 0); none.adjustMutationLevel(1);
        PlantMutationSystem.cycle(none, new PlantMutationTable(List.of(base)), new Controlled(), () -> .5f, null, null);
        assertTrue(none.genetics().mutations().isEmpty());
        var transientEntry = new PlantMutationTable.Entry("Slippery", 1, false, true, false, PlantMutationTable.EffectKind.SLIPIFY,
                Optional.empty(), Optional.empty(), "", Optional.empty(), Optional.empty(), Optional.of(base.slippery().orElseThrow()));
        var temporaryPlant = new PlantInstance(50, 50, 0); temporaryPlant.plant(id("wheat"), profile, 0); temporaryPlant.adjustMutationLevel(1);
        var hit = new Controlled(); hit.roll = 0;
        PlantMutationSystem.cycle(temporaryPlant, new PlantMutationTable(List.of(transientEntry)), hit, () -> .5f, null, null);
        assertTrue(temporaryPlant.genetics().mutations().isEmpty());

        var changed = new com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition(
                new com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData(false, .2d, 8d, true),
                com.juicyslew.moonstation14.ms14.slip.SlipResponseData.DEFAULT);
        var later = new PlantMutationTable.Entry("Slippery", 1, false, true, true, PlantMutationTable.EffectKind.SLIPIFY,
                Optional.empty(), Optional.empty(), "", Optional.empty(), Optional.empty(), Optional.of(changed));
        var duplicatePlant = new PlantInstance(50, 50, 0); duplicatePlant.plant(id("wheat"), profile, 0); duplicatePlant.adjustMutationLevel(1);
        var duplicateRng = new Controlled() { @Override public double nextDouble() { doubles++; return 0; } };
        PlantMutationSystem.cycle(duplicatePlant, new PlantMutationTable(List.of(base, later)), duplicateRng, () -> .5f, null, null);
        assertEquals(2, duplicateRng.doubles);
        assertEquals(List.of(base.slippery().orElseThrow()), duplicatePlant.genetics().mutations().stream().map(PlantMutationRecord::slippery).toList());
        assertNotEquals(changed, duplicatePlant.genetics().mutations().getFirst().slippery());
    }

    @Test void attachedGeneticCarrierSurvivesActualStackPersistentAndNetworkRoundTrips() {
        var descriptor = new PlantMutationRecord("Slippery", false, true, true,
                com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition.DEFAULT);
        var genetics = new GeneticValue(id("wheat"), wheat()).withRecordedMutation(descriptor);
        var reference = new PlantSeedReference(genetics, true);
        var original = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 2);
        GeneticCarriers.attach(original, reference);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var decoded = net.minecraft.world.item.ItemStack.CODEC.parse(ops,
                net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, original).getOrThrow()).getOrThrow();
        assertCarrierSnapshot(decoded, descriptor);
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        try {
            net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, original);
            var streamed = net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            assertCarrierSnapshot(streamed, descriptor);
        } finally { buf.release(); }
    }

    private static void assertCarrierSnapshot(net.minecraft.world.item.ItemStack stack, PlantMutationRecord expected) {
        var reference = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get());
        var genetics = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get());
        assertNotNull(reference); assertNotNull(genetics);
        for (var value : List.of(reference.current(), genetics)) {
            assertEquals(1, value.mutations().size());
            var record = value.mutations().getFirst();
            assertEquals(expected.name(), record.name()); assertEquals(expected.appliesToPlant(), record.appliesToPlant());
            assertEquals(expected.appliesToProduce(), record.appliesToProduce()); assertEquals(expected.persists(), record.persists());
            assertEquals(expected.slippery(), record.slippery());
        }
    }

    @Test void dueBoundaryAndAbsentDeadAndIndependentPlantGuards() {
        var profile = wheat(); var tray = new TrayProfile(3, 15, 50, 50, false, 10000, 10000);
        var empty = new PlantInstance(50, 50, 0); var rng = new Controlled(); AtomicInteger calls = new AtomicInteger();
        PlantMetabolismEvaluator.poll(empty, tray, () -> null, 15, p -> {}, rng, Optional.empty(), p -> calls.incrementAndGet());
        assertEquals(1, calls.get()); assertEquals(0, rng.doubles); // mutation owner is invoked but its empty-holder guard exits before rolls.
        var notDue = new PlantInstance(50, 50, 0); notDue.plant(id("wheat"), profile, 0);
        calls.set(0);
        PlantMetabolismEvaluator.poll(notDue, tray, notDue::effectiveProfile, 3, p -> {}, new Controlled(),
                Optional.of(new PlantMetabolismEvaluator.Environment(101.3f, 293.15f)), p -> calls.incrementAndGet());
        assertEquals(0, calls.get(), "a reagent poll before the 15-second due boundary must not mutate");
        var dead = new PlantInstance(50, 50, 0); dead.plant(id("wheat"), profile, 0); dead.adjustMutationLevel(2);
        dead.state(dead.age(), 0, dead.lastProduceAge(), dead.skipAging(), false, true);
        var before = dead.genetics();
        var chemistry = chemicalCatalogs();
        var noMutation = PlantMutationSystem.cycle(dead, sourceTable(), rng, () -> .5f, chemistry.tables(), chemistry.reagents());
        assertEquals(0, noMutation.severity()); assertSame(before, dead.genetics()); assertEquals(2, dead.mutationLevel());
        var a = new PlantInstance(50, 50, 0); var b = new PlantInstance(50, 50, 0); a.plant(id("wheat"), profile, 0); b.plant(id("wheat"), profile, 0);
        a.adjustMutationLevel(1); long revision = a.revision(); a.genetics(a.genetics());
        assertEquals(revision + 1, a.revision()); assertSame(profile, b.effectiveProfile());
    }

    @Test void cryoxadoneForceRunsWholeCycleImmediatelyAndIsConsumedOnce() {
        var profile = wheat(); var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), profile, 0);
        plant.state(8, 100, 0, 0, false, false);
        var tray = new TrayProfile(3, 15, 50, 50, false, 10000, 10000);
        var environment = Optional.of(new PlantMetabolismEvaluator.Environment(101.3f, 293.15f));
        var random = new Controlled(); var mutationCalls = new AtomicInteger();
        PlantMetabolismEvaluator.poll(plant, tray, plant::effectiveProfile, 3,
                p -> p.cryoxadone(profile, () -> 9), random, environment, p -> mutationCalls.incrementAndGet());
        assertEquals(3, plant.age(), "Cryoxadone rewinds age; the forced cycle must consume SkipAging instead of aging immediately");
        assertEquals(0, plant.skipAging());
        assertEquals(3, plant.lastCycle(), "forced cycle records actual current time, not a fabricated old clock value");
        assertEquals(1, mutationCalls.get());

        PlantMetabolismEvaluator.poll(plant, tray, plant::effectiveProfile, 6, p -> {}, random, environment,
                p -> mutationCalls.incrementAndGet());
        assertEquals(3, plant.age()); assertEquals(3, plant.lastCycle());
        assertEquals(1, mutationCalls.get(), "the one-shot force doesn't bypass the ordinary cycle interval on the next poll");
    }

    @Test void chemicalPreflightFailsBeforeRandomOrGeneticCommit() {
        var profile = wheat(); var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), profile, 0); plant.adjustMutationLevel(2);
        var table = new PlantMutationTable(List.of(
                new PlantMutationTable.Entry("stat", 1, true, true, false, PlantMutationTable.EffectKind.STAT,
                        Optional.of(PlantStatMutation.toggle(PlantStatMutation.Target.VIABLE)), Optional.empty(), ""),
                new PlantMutationTable.Entry("chem", 1, true, true, false, PlantMutationTable.EffectKind.CHEMICALS,
                        Optional.empty(), Optional.of(id("missing")), "")));
        long revision = plant.revision(); var random = new Controlled();
        assertThrows(IllegalArgumentException.class, () -> PlantMutationSystem.cycle(plant, table, random, () -> .5f, null, null));
        assertEquals(0, random.doubles); assertEquals(revision, plant.revision()); assertSame(profile, plant.effectiveProfile()); assertEquals(2, plant.mutationLevel());
    }

    @Test void successfulStatMutationReplacesOnlyThisInstancesImmutableGeneticsAndResetsLevel() {
        var profile = wheat(); var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), profile, 0); plant.adjustMutationLevel(1);
        var other = new PlantInstance(50, 50, 0); other.plant(id("wheat"), profile, 0); var otherGenetics = other.genetics();
        var previous = plant.genetics(); long revision = plant.revision();
        var table = new PlantMutationTable(List.of(new PlantMutationTable.Entry("heat", 1, true, true, false,
                PlantMutationTable.EffectKind.STAT,
                Optional.of(PlantStatMutation.numeric(PlantStatMutation.Target.IDEAL_HEAT, 263, 323, 5)), Optional.empty(), "")));
        var rng = new Controlled(); rng.roll = 0; rng.floatRoll = .5f;
        PlantMutationSystem.cycle(plant, table, rng, () -> .5f, null, null);
        assertEquals(1, rng.doubles); assertEquals(1, rng.floats); assertEquals(0, plant.mutationLevel());
        assertEquals(revision + 3, plant.revision()); // COW, immutable genetics replacement, and holder-level reset
        assertNotSame(previous, plant.genetics()); assertSame(otherGenetics, other.genetics());
        assertEquals(profile.biology().idealHeat(), previous.profile().biology().idealHeat());
        assertNotEquals(profile.biology().idealHeat(), plant.effectiveProfile().biology().idealHeat());
    }

    @Test void resolvedSourceGasMutationsApplyInSourceOrderAndAreNotDiagnosedUnsupported() {
        var profile = wheat(); var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), profile, 0);
        plant.adjustMutationLevel(25);
        var rng = new Controlled() {
            @Override public double nextDouble() { doubles++; return doubles == 25 || doubles == 26 ? 0 : 1; }
            @Override public float nextFloat(float origin, float bound) { floats++; assertEquals(.01f, origin); assertEquals(.5f, bound); return .25f; }
            @Override public int nextInt(int bound) { ints++; return ints == 1 ? 8 : 0; }
        };
        var result = PlantMutationSystem.cycle(plant, sourceTable(), rng, () -> .5f, chemicalCatalogs().tables(), chemicalCatalogs().reagents());
        assertEquals(27, rng.doubles); assertEquals(2, rng.floats); assertEquals(2, rng.ints);
        assertFalse(result.unsupported().stream().anyMatch(x -> x.startsWith("ChangeExudeGasses") || x.startsWith("ChangeConsumeGasses")));
        assertEquals(.25f, plant.effectiveProfile().exudeGasses().get(com.juicyslew.moonstation14.ms14.atmos.core.GasType.FREZON));
        assertEquals(.25f, plant.effectiveProfile().consumeGasses().get(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN));
        assertTrue(plant.effectiveProfile().chemicals().equals(profile.chemicals()));
    }

    @Test void resolvedChangeHarvestMutatesImmutableInstanceThroughThreeModesWithoutBodyRandomDraws() {
        var baseline = wheat(); var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), baseline, 0);
        var other = new PlantInstance(50, 50, 0); other.plant(id("wheat"), baseline, 0);
        var otherGenetics = other.genetics(); var table = sourceTable(); var chemistry = chemicalCatalogs();
        var previousSnapshots = new java.util.ArrayList<GeneticValue>();
        var previousModes = new java.util.ArrayList<SeedProfile.Harvest>();
        var transitions = List.of(
                new SeedProfile.Harvest[]{SeedProfile.Harvest.NO_REPEAT, SeedProfile.Harvest.REPEAT},
                new SeedProfile.Harvest[]{SeedProfile.Harvest.REPEAT, SeedProfile.Harvest.SELF_HARVEST},
                new SeedProfile.Harvest[]{SeedProfile.Harvest.SELF_HARVEST, SeedProfile.Harvest.SELF_HARVEST});
        for (var transition : transitions) {
            var expectedBefore = transition[0]; var expected = transition[1];
            plant.adjustMutationLevel(25);
            var rng = new Controlled() { @Override public double nextDouble() { doubles++; return doubles == 27 ? 0 : 1; } };
            var before = plant.genetics();
            assertEquals(expectedBefore, before.profile().lifecycle().harvest());
            var result = PlantMutationSystem.cycle(plant, table, rng, () -> { throw new AssertionError("no body random draws"); }, chemistry.tables(), chemistry.reagents());
            assertEquals(27, rng.doubles); assertEquals(0, rng.floats); assertEquals(0, rng.ints);
            assertFalse(result.unsupported().stream().anyMatch(s -> s.startsWith("ChangeHarvest:")));
            assertEquals(expected, plant.genetics().profile().lifecycle().harvest());
            assertEquals(SeedProfile.Harvest.NO_REPEAT, baseline.lifecycle().harvest());
            assertEquals(expectedBefore, before.profile().lifecycle().harvest(), "mutation must not alter its prior immutable snapshot");
            for (int i = 0; i < previousSnapshots.size(); i++)
                assertEquals(previousModes.get(i), previousSnapshots.get(i).profile().lifecycle().harvest(), "later mutations must not alter prior snapshots");
            previousSnapshots.add(before); previousModes.add(expectedBefore);
            assertSame(otherGenetics, other.genetics());
            assertEquals(baseline.packet(), plant.genetics().profile().packet());
            assertEquals(baseline.product(), plant.genetics().profile().product());
            assertEquals(baseline.chemicals(), plant.genetics().profile().chemicals());
            assertEquals(baseline.consumeGasses(), plant.genetics().profile().consumeGasses());
            assertEquals(baseline.exudeGasses(), plant.genetics().profile().exudeGasses());
            assertEquals(baseline.biology(), plant.genetics().profile().biology());
        }
    }

    @Test void gasMutationUniformlyAddressesEveryCanonicalSpeciesAndKeepsDirectionIndependent() {
        var profile = wheat(); var original = new GeneticValue(id("wheat"), profile);
        var effect = PlantGasMutation.source(PlantGasMutation.Target.CONSUME);
        for (int i = 0; i < com.juicyslew.moonstation14.ms14.atmos.core.GasType.values().length; i++) {
            final int selected = i; int[] amountDraws = {0}, picks = {0};
            var random = new java.util.Random(1) {
                @Override public float nextFloat(float origin, float bound) { amountDraws[0]++; return .125f; }
                @Override public int nextInt(int bound) { picks[0]++; assertEquals(9, bound); return selected; }
            };
            var changed = effect.apply(original, random);
            var gas = com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()[i];
            assertEquals(.125f, changed.profile().consumeGasses().get(gas));
            assertTrue(changed.profile().exudeGasses().isEmpty());
            assertEquals(1, amountDraws[0]); assertEquals(1, picks[0]);
        }
    }

    @Test void viabilityDamagesAndPestsUseResolvedToleranceAndHarvestYieldCanBeZero() {
        var base = wheat();
        var biology = base.biology(); var lifecycle = base.lifecycle();
        var nonviable = new SeedProfile.Lifecycle(lifecycle.growthStages(), lifecycle.lifespan(), lifecycle.maturation(), lifecycle.production(), lifecycle.yield(), lifecycle.potency(), lifecycle.harvest(), false, lifecycle.seedless());
        var p = new PlantInstance(0, 0, 0); p.plant(id("wheat"), new SeedProfile(base.packet(), base.product(), base.visualKey(), biology, nonviable, base.chemicals(), base.sourceName()), 0);
        p.pestLevel(6);
        var cycleRng = new Controlled(); cycleRng.floatRoll = .5f;
        PlantMetabolismEvaluator.cycle(p, new TrayProfile(1, 1, 0, 0, false, 10000, 10000), p.effectiveProfile(), 1, cycleRng,
                Optional.of(new PlantMetabolismEvaluator.Environment(101.3f, 293.15f)));
        assertTrue(p.health() < biology.endurance() - 6, "unviability and pests above inherited tolerance damage health");
        var persisted = PlantInstance.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                PlantInstance.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, p).getOrThrow()).getOrThrow();
        assertEquals(p.pests(), persisted.pests());
        var zeroLifecycle = new SeedProfile.Lifecycle(lifecycle.growthStages(), lifecycle.lifespan(), lifecycle.maturation(), lifecycle.production(), 0, lifecycle.potency(), lifecycle.harvest(), true, false);
        var zero = new SeedProfile(base.packet(), base.product(), base.visualKey(), biology, zeroLifecycle, base.chemicals(), base.sourceName());
        p.plant(id("wheat"), zero, 2); p.state(p.age(), p.health(), p.lastProduceAge(), p.skipAging(), true, false);
        assertEquals(0, PlantMetabolismEvaluator.previewHarvestYield(p, zero, 1));
        assertEquals(0, PlantMetabolismEvaluator.harvest(p, zero, 1).plannedYield());
    }
}
