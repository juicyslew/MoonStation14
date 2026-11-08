package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlantCareMetabolismTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private static SeedProfile profile(float ageMaturation, int yield, boolean viable, boolean seedless) {
        return new SeedProfile(id("packet"), id("product"), "plant",
                new SeedProfile.Biology(20, 2, 3, 293, 4, 80, 120, 5, 10, 4, 6),
                new SeedProfile.Lifecycle(5, 25, ageMaturation, 4, yield, 8, SeedProfile.Harvest.REPEAT, viable, seedless),
                Map.of(id("chemical"), new SeedProfile.Chemical(.1f, .4f, 10)), Map.of(GasType.OXYGEN, 1f),
                Map.of(GasType.CARBON_DIOXIDE, 2f), List.of(id("target")), SeedProfile.Scream.DISABLED, "test-plant-name");
    }

    @Test void cryoxadoneMatureAndImmatureBranchesPreserveExactDeviationAndDrawCount() {
        var seed = profile(10, 2, true, false);
        var mature = new PlantInstance(50, 50, 0); mature.plant(new GeneticValue(id("species"), seed), 0);
        mature.state(11, 18, 6, 0, false, false);
        var rolls = new AtomicInteger();
        mature.cryoxadone(seed, () -> { rolls.incrementAndGet(); return 7; });
        assertEquals(1, rolls.get());
        assertEquals(2, mature.age()); // max(10 - 1, age - NextInt(7, 10)) == 9
        assertEquals(2, mature.lastProduceAge()); assertEquals(1, mature.skipAging());
        assertTrue(mature.consumeForceCycleUpdate()); assertFalse(mature.consumeForceCycleUpdate());

        var immature = new PlantInstance(50, 50, 0); immature.plant(new GeneticValue(id("species"), seed), 0);
        immature.state(10, 18, 6, 0, false, false);
        immature.cryoxadone(seed, () -> { fail("immature branch must not roll"); return 0; });
        assertEquals(8, immature.age()); // int truncation of 10 / 5
        assertEquals(1, rolls.get());

        var fractionalSeed = profile(10.5f, 2, true, false);
        var fractional = new PlantInstance(50, 50, 0);
        fractional.plant(new GeneticValue(id("species"), fractionalSeed), 0);
        fractional.state(12, 18, 0, 0, false, false);
        fractional.cryoxadone(fractionalSeed, () -> 8);
        assertEquals(3, fractional.age()); // cast occurs after max(9.5, 12 - 8), truncating 9.5
    }

    @Test void growthMatchesSourcePositiveNegativeAndZeroYieldEdges() {
        var zeroYield = profile(10, 0, true, false);
        var plant = new PlantInstance(50, 50, 0); plant.plant(new GeneticValue(id("species"), zeroYield), 0);
        plant.state(2, 10, 5, 0, false, false);
        plant.affectGrowth(3, zeroYield); assertEquals(5, plant.age());
        plant.state(12, 10, 5, 0, false, false);
        plant.affectGrowth(2, zeroYield); assertEquals(3, plant.lastProduceAge());
        plant.affectGrowth(-2, zeroYield); assertEquals(1, plant.lastProduceAge());
        plant.affectGrowth(0, zeroYield); assertEquals(1, plant.lastProduceAge());
        plant.state(12, 10, 5, 0, true, false); plant.affectGrowth(3, zeroYield);
        assertEquals(5, plant.lastProduceAge(), "ready harvest suppresses source yield-zero last-produce adjustment");
    }

    @Test void healthChecksDeathImmediatelyButDefersUpperClampUntilWholePollSanity() {
        var seed = profile(10, 2, true, false);
        var healed = new PlantInstance(50, 50, 0); healed.plant(new GeneticValue(id("species"), seed), 0);
        healed.state(2, 20, 0, 0, false, false);
        healed.adjustCareHealth(10);
        assertEquals(30, healed.health(), "reagent health can temporarily exceed endurance");
        assertFalse(healed.dead());
        healed.adjustCareHealth(-10);
        assertEquals(20, healed.health(), "later reagent loss observes prior un-clamped health");
        healed.clampCareHealth(20);
        assertEquals(20, healed.health());

        var lethal = new PlantInstance(50, 50, 0); lethal.plant(new GeneticValue(id("species"), seed), 0);
        lethal.state(2, 20, 0, 0, true, false);
        lethal.adjustCareHealth(-21);
        assertTrue(lethal.dead()); assertFalse(lethal.ready());
        lethal.adjustCareHealth(50);
        assertTrue(lethal.dead(), "later healing effect cannot revive after CheckHealth/Die");
        assertEquals(-1, lethal.health(), "lower health is clamped only at end-of-poll sanity");
        lethal.clampCareHealth(20);
        assertEquals(0, lethal.health());
    }

    @Test void geneticCareCopiesPreserveOtherProfileFieldsAndImplementCareMutations() {
        var original = new GeneticValue(id("species"), profile(10, 3, false, true));
        var both = original.diethylamine(true, true);
        assertEquals(26, both.profile().lifecycle().lifespan());
        assertEquals(21, both.profile().biology().endurance());
        var one = original.diethylamine(false, true);
        assertEquals(25, one.profile().lifecycle().lifespan()); assertEquals(21, one.profile().biology().endurance());
        var viable = original.viable(); assertTrue(viable.profile().lifecycle().viable());
        var restored = original.restoreSeeds(); assertFalse(restored.profile().lifecycle().seedless());
        for (var changed : List.of(both, one, viable, restored)) {
            assertEquals(original.species(), changed.species());
            assertEquals(original.profile().chemicals(), changed.profile().chemicals());
            assertEquals(original.profile().consumeGasses(), changed.profile().consumeGasses());
            assertEquals(original.profile().exudeGasses(), changed.profile().exudeGasses());
            assertEquals(original.profile().mutationTargets(), changed.profile().mutationTargets());
            assertEquals(original.profile().packet(), changed.profile().packet());
            assertEquals(original.profile().product(), changed.profile().product());
        }
    }
}
