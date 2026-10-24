package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlantAutoHarvestLifecycleTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static SeedProfile seed(SeedProfile.Harvest mode, int yield, float production) {
        var base = BotanyTestSeedFixtures.resolved(id("wheat"));
        var old = base.lifecycle();
        return new SeedProfile(base.packet(), base.product(), base.visualKey(), base.biology(),
                new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), old.maturation(), production,
                         yield, old.potency(), mode, old.viable(), old.seedless()), base.chemicals(), base.consumeGasses(), base.exudeGasses(), base.mutationTargets(), base.scream(), base.sourceName());
    }
    private static final RandomGenerator RNG = new RandomGenerator() {
        @Override public long nextLong() { return 1; }
        @Override public float nextFloat() { return .5f; }
        @Override public int nextInt(int origin, int bound) { return origin; }
    };
    private static final Optional<PlantMetabolismEvaluator.Environment> ENV = Optional.of(
            new PlantMetabolismEvaluator.Environment(101.3f, 293.15f));
    private static final TrayProfile TRAY = new TrayProfile(3, 15, 50, 50, false, 10000, 10000);

    @Test void harvestsOnlyPreviouslyReadySelfHarvestPlantAndCommitsAtCurrentPostGrowthAge() {
        var seed = seed(SeedProfile.Harvest.SELF_HARVEST, 2, 1);
        var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), seed, 0);
        plant.state(2, plant.health(), 0, 0, true, false);
        AtomicInteger callbacks = new AtomicInteger();
        var result = PlantMetabolismEvaluator.cycle(plant, TRAY, seed, 15, RNG, ENV, null,
                (source, currentSeed, age, commit) -> {
                    callbacks.incrementAndGet(); assertSame(plant, source); assertSame(seed, currentSeed);
                    assertEquals(3, age); assertEquals(age, source.age());
                    commit.run(); return true;
                });
        assertEquals(1, callbacks.get()); assertEquals(3, plant.lastProduceAge());
        assertFalse(plant.ready()); assertEquals(PlantInstance.Status.GROWING, result.status());
    }

    @Test void failureKeepsReadyAndNewlyReadyWaitsUntilNextCycle() {
        var seed = seed(SeedProfile.Harvest.SELF_HARVEST, 2, 1);
        var ready = new PlantInstance(50, 50, 0); ready.plant(id("wheat"), seed, 0);
        ready.state(2, ready.health(), 0, 0, true, false);
        var failed = PlantMetabolismEvaluator.cycle(ready, TRAY, seed, 15, RNG, ENV, null,
                (source, currentSeed, age, commit) -> false);
        assertTrue(ready.ready()); assertEquals(0, ready.lastProduceAge()); assertEquals(PlantInstance.Status.HARVEST, failed.status());

        var growing = new PlantInstance(50, 50, 0); growing.plant(id("wheat"), seed, 0);
        growing.state(1, growing.health(), 0, 0, false, false);
        AtomicInteger callbacks = new AtomicInteger();
        PlantMetabolismEvaluator.cycle(growing, TRAY, seed, 15, RNG, ENV, null,
                (source, currentSeed, age, commit) -> { callbacks.incrementAndGet(); return false; });
        assertEquals(0, callbacks.get()); assertTrue(growing.ready(), "ordinary readiness is established after the auto-harvest phase");
    }

    @Test void absentDeadAndNotDuePlantsNeverCallAutoOutputOwner() {
        var seed = seed(SeedProfile.Harvest.SELF_HARVEST, 2, 1);
        AtomicInteger callbacks = new AtomicInteger();
        PlantMetabolismEvaluator.AutoHarvest auto = (source, currentSeed, age, commit) -> {
            callbacks.incrementAndGet(); return false;
        };
        var empty = new PlantInstance(50, 50, 0);
        PlantMetabolismEvaluator.cycle(empty, TRAY, null, 15, RNG, Optional.empty(), null, auto);
        var dead = new PlantInstance(50, 50, 0); dead.plant(id("wheat"), seed, 0);
        dead.state(2, 0, 0, 0, true, true);
        PlantMetabolismEvaluator.cycle(dead, TRAY, seed, 15, RNG, ENV, null, auto);
        var notDue = new PlantInstance(50, 50, 0); notDue.plant(id("wheat"), seed, 0);
        notDue.state(2, notDue.health(), 0, 0, true, false);
        PlantMetabolismEvaluator.poll(notDue, TRAY, notDue::effectiveProfile, 3, p -> {}, RNG, ENV,
                p -> {}, null, auto);
        assertEquals(0, callbacks.get()); assertTrue(notDue.ready());
    }

    @Test void automaticZeroYieldIsOneButManualZeroYieldRemainsZeroAndManualSelfHarvestRetains() {
        var seed = seed(SeedProfile.Harvest.SELF_HARVEST, 0, 1);
        assertEquals(1, PlantMetabolismEvaluator.automaticHarvestCount(seed));
        var plant = new PlantInstance(50, 50, 0); plant.plant(id("wheat"), seed, 0);
        plant.state(3, plant.health(), 0, 0, true, false);
        assertEquals(0, PlantMetabolismEvaluator.harvest(plant, seed, 1).plannedYield());
        assertNotNull(plant.seed()); assertFalse(plant.ready()); assertEquals(3, plant.lastProduceAge());
    }

    @Test void singleAutomaticSelfHarvestOutputMarksRetainedSourceShared() {
        var seed = seed(SeedProfile.Harvest.SELF_HARVEST, 0, 1);
        var source = new PlantInstance(50, 50, 0);
        source.plant(id("wheat"), seed, 0);
        var reference = source.seedReference();

        HydroponicsTrayBlockEntity.markAutomaticHarvestSourceShared(source,
                PlantMetabolismEvaluator.automaticHarvestCount(seed));

        assertSame(reference, source.seedReference());
        assertFalse(reference.unique(), "one output plus retained source are two reference holders");
        source.ensureUniqueSeed();
        assertNotSame(reference, source.seedReference(), "the next plant poll must detach from the output reference");
    }

    @Test void manualRepeatRetainsButNoRepeatClears() {
        var repeat = seed(SeedProfile.Harvest.REPEAT, 1, 1);
        var repeatPlant = new PlantInstance(50, 50, 0); repeatPlant.plant(id("wheat"), repeat, 0);
        repeatPlant.state(3, repeatPlant.health(), 0, 0, true, false);
        PlantMetabolismEvaluator.harvest(repeatPlant, repeat, 1);
        assertNotNull(repeatPlant.seed()); assertFalse(repeatPlant.ready());

        var noRepeat = seed(SeedProfile.Harvest.NO_REPEAT, 1, 1);
        var singlePlant = new PlantInstance(50, 50, 0); singlePlant.plant(id("wheat"), noRepeat, 0);
        singlePlant.state(3, singlePlant.health(), 0, 0, true, false);
        PlantMetabolismEvaluator.harvest(singlePlant, noRepeat, 1);
        assertNull(singlePlant.seed());
    }
}
