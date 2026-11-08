package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlantChemicalMutationTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static SeedProfile wheat() {
        return BotanyTestSeedFixtures.resolved(id("wheat"));
    }
    @Test void sampleFloorMinAndExistingInherentAreAppliedImmutablyPerInstance() {
        var table = new WeightedRandomFillSolution(java.util.List.of(
                new WeightedRandomFillSolution.Group(5f, 1f, java.util.List.of(id("nutriment")))));
        var profile = wheat(); var genetics = new GeneticValue(id("wheat"), profile);
        var first = new PlantInstance(50, 50, 0); first.plant(id("wheat"), profile, 0);
        var second = new PlantInstance(50, 50, 0); second.plant(id("wheat"), profile, 0);
        long revision = first.revision();
        float[] rolls = {0f, 0f, .236f}; AtomicInteger index = new AtomicInteger();
        first.genetics(PlantChemicalMutation.apply(genetics, table, () -> rolls[index.getAndIncrement()]));
        assertEquals(revision + 1, first.revision());
        assertEquals(profile, second.effectiveProfile());
        var chemical = first.effectiveProfile().chemicals().get(id("nutriment"));
        assertTrue(chemical.inherent());
        assertEquals(21.18f, chemical.max(), .0001f);
        assertEquals(100f / 21.18f, chemical.potencyDivisor(), .0001f);
        float[] epsilon = {0f, 0f, .0001f}; index.set(0);
        var changed = PlantChemicalMutation.apply(genetics, table, () -> epsilon[index.getAndIncrement()]);
        assertEquals(20.01f, changed.profile().chemicals().get(id("nutriment")).max(), .0001f);
        var newTable = new WeightedRandomFillSolution(java.util.List.of(
                new WeightedRandomFillSolution.Group(.05f, 1f, java.util.List.of(id("acquired")))));
        float[] newRolls = {0f, 0f, .5f}; index.set(0);
        var acquired = PlantChemicalMutation.apply(genetics, newTable, () -> newRolls[index.getAndIncrement()])
                .profile().chemicals().get(id("acquired"));
        assertFalse(acquired.inherent()); assertEquals(.01f, acquired.min()); assertEquals(.03f, acquired.max(), .0001f);
    }
    @Test void sourceFixedPointEpsilonAndRepeatedCentAddition() {
        assertEquals(1, BotanyFixedPoint.fromFloat(.0099999f));
        assertEquals(1, BotanyFixedPoint.fromFloat(.01f));
        long cents = BotanyFixedPoint.fromFloat(.01f);
        for (int i = 0; i < 29; i++) cents += BotanyFixedPoint.fromFloat(.01f);
        assertEquals(30, cents);
        var chemical = new SeedProfile.Chemical(.01f, .01f, 100, true);
        var extended = chemical.extendCents(BotanyFixedPoint.fromFloat(.29f));
        assertEquals(.30f, extended.max());
        assertTrue(extended.inherent());
    }
    @Test void chemicalMutationPreservesNonemptyGasGenesInReturnedAndSourceProfiles() {
        var profile = wheat();
        var oxygen = com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN;
        var ammonia = com.juicyslew.moonstation14.ms14.atmos.core.GasType.AMMONIA;
        var targets=java.util.List.of(id("tomato"),id("tomato"));
        var withGas = new SeedProfile(profile.packet(), profile.product(), profile.visualKey(), profile.biology(),
                profile.lifecycle(), profile.chemicals(), Map.of(oxygen, .2f), Map.of(ammonia, .3f),targets, profile.scream(), profile.sourceName());
        var genetics = new GeneticValue(id("wheat"), withGas);
        var table = new WeightedRandomFillSolution(java.util.List.of(
                new WeightedRandomFillSolution.Group(5f, 1f, java.util.List.of(id("nutriment")))));
        float[] rolls = {0f, 0f, .5f}; AtomicInteger index = new AtomicInteger();
        var changed = PlantChemicalMutation.apply(genetics, table, () -> rolls[index.getAndIncrement()]);

        assertTrue(changed.profile().chemicals().get(id("nutriment")).max() > withGas.chemicals().get(id("nutriment")).max(),
                "existing canonical chemical entry is extended");
        assertEquals(Map.of(oxygen, .2f), changed.profile().consumeGasses());
        assertEquals(Map.of(ammonia, .3f), changed.profile().exudeGasses());
        assertEquals(Map.of(oxygen, .2f), genetics.profile().consumeGasses());
        assertEquals(Map.of(ammonia, .3f), genetics.profile().exudeGasses());
        assertEquals(targets,changed.profile().mutationTargets());
        assertEquals(profile.chemicals(), withGas.chemicals());
    }
    @Test void harvestMutationRetainsMutationTargets() {
        var base=wheat(); var targets=java.util.List.of(id("tomato"),id("potato"),id("tomato"));
        var profile=new SeedProfile(base.packet(),base.product(),base.visualKey(),base.biology(),base.lifecycle(),base.chemicals(),Map.of(),Map.of(),targets, base.scream(), base.sourceName());
        var changed=new PlantHarvestMutation().apply(new GeneticValue(id("wheat"),profile));
        assertEquals(targets,changed.profile().mutationTargets());
        assertEquals(targets,profile.mutationTargets());
    }
    @Test void holderMutationFloatsRoundTripWithoutSharingAndAdvanceRevision() {
        var first = new PlantInstance(50, 50, 0);
        var second = new PlantInstance(50, 50, 0);
        first.adjustMutationLevel(1.375f); first.adjustMutationModifier(.25f);
        assertEquals(1.375f, first.mutationLevel()); assertEquals(1.25f, first.mutationModifier());
        assertEquals(0f, second.mutationLevel()); assertEquals(1f, second.mutationModifier());
        var decoded = PlantInstance.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                PlantInstance.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, first).getOrThrow()).getOrThrow();
        assertEquals(1.375f, decoded.mutationLevel()); assertEquals(1.25f, decoded.mutationModifier());
        first.adjustMutationLevel(-2); first.adjustMutationModifier(5);
        assertEquals(0f, first.mutationLevel()); assertEquals(3f, first.mutationModifier());
    }
}
