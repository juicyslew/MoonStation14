package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.random.RandomGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Gas lifecycle coverage drives the same GasExchange boundary bound by the live tray adapter. */
class PlantGasMetabolismTest {
    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath("moonstation14", s); }
    private static final TrayProfile TRAY = new TrayProfile(60, 100, 0, 0, false, 1000, 500);
    private static final RandomGenerator RANDOM = new RandomGenerator() {
        @Override public long nextLong() { return 0; }
        @Override public float nextFloat() { return .99f; }
        @Override public int nextInt(int origin, int bound) { return origin; }
        @Override public int nextInt(int bound) { return 0; }
    };
    private static SeedProfile seed(Map<GasType, Float> consume, Map<GasType, Float> exude, float potency) {
        return new SeedProfile(id("packet"), id("produce"), "plant",
                new SeedProfile.Biology(100, 0, 0, 293, 10, 50, 150, 99, 99, 99, 99),
                new SeedProfile.Lifecycle(5, 100, 10, 5, 1, potency, SeedProfile.Harvest.REPEAT), Map.of(), consume, exude, List.of(), SeedProfile.Scream.DISABLED, "test-plant-name");
    }
    private static PlantInstance plant(SeedProfile seed) {
        PlantInstance plant = new PlantInstance(0, 0, 0); plant.plant(id("seed"), seed, 0); return plant;
    }
    private static final class Cell implements PlantMetabolismEvaluator.GasExchange {
        final EnumMap<GasType, Double> moles = new EnumMap<>(GasType.class);
        int consumeCalls, exudeCalls, samples;
        boolean writable = true, available = true, exterior;
        float temperature = 293.15f;
        float pressure = 100;
        Cell() { for (GasType type : GasType.values()) moles.put(type, 0d); }
        @Override public Optional<PlantMetabolismEvaluator.Environment> environment() {
            samples++;
            return available ? Optional.of(new PlantMetabolismEvaluator.Environment(pressure, temperature)) : Optional.empty();
        }
        @Override public boolean consume(GasType gas, double amount) {
            consumeCalls++;
            double have = moles.getOrDefault(gas, 0d);
            if (!writable || exterior || !available || have < amount) return false;
            moles.put(gas, have - amount); pressure -= amount; return true;
        }
        @Override public boolean exude(GasType gas, double amount, float temp) {
            exudeCalls++;
            if (!writable || exterior || !available) return false;
            moles.merge(gas, amount, Double::sum); pressure += amount; assertEquals(temperature, temp); return true;
        }
    }
    private static PlantMetabolismEvaluator.Result cycle(PlantInstance p, SeedProfile s, long now, Cell cell) {
        return PlantMetabolismEvaluator.cycle(p, TRAY, s, now, RANDOM, cell.environment(), cell);
    }

    @Test void emptyAndDeadHoldersNeverTouchWorldAndLegacyEmptyMapsStillCycle() {
        Cell cell = new Cell();
        PlantInstance empty = new PlantInstance(0, 0, 0);
        cycle(empty, null, 100, cell);
        assertEquals(0, cell.consumeCalls + cell.exudeCalls);
        PlantInstance dead = plant(seed(Map.of(GasType.OXYGEN, 2f), Map.of(GasType.NITROGEN, 2f), 20));
        dead.state(0, 0, 0, 0, false, true);
        cycle(dead, dead.effectiveProfile(), 100, cell);
        assertEquals(0, cell.consumeCalls + cell.exudeCalls);
        PlantInstance ordinary = plant(seed(Map.of(), Map.of(), 20));
        assertTrue(PlantMetabolismEvaluator.cycle(ordinary, TRAY, ordinary.effectiveProfile(), 100, RANDOM,
                Optional.of(new PlantMetabolismEvaluator.Environment(100, 293.15f))).changed());
        assertEquals(0, cell.consumeCalls + cell.exudeCalls);
    }

    @Test void requiredGasEqualityConsumesExactlyAndJustShortNeverPartiallyConsumesOrDamagesPerMissingGas() {
        SeedProfile two = seed(Map.of(GasType.OXYGEN, 2f, GasType.NITROGEN, 3f), Map.of(), 20);
        Cell enough = new Cell(); enough.moles.put(GasType.OXYGEN, 2d); enough.moles.put(GasType.NITROGEN, 3d);
        PlantInstance healthy = plant(two); cycle(healthy, two, 100, enough);
        PlantInstance baseline = plant(seed(Map.of(), Map.of(), 20)); cycle(baseline, baseline.effectiveProfile(), 100, new Cell());
        assertEquals(0, enough.moles.get(GasType.OXYGEN)); assertEquals(0, enough.moles.get(GasType.NITROGEN));
        assertEquals(baseline.health(), healthy.health());
        Cell shortCell = new Cell(); shortCell.moles.put(GasType.OXYGEN, 2d); shortCell.moles.put(GasType.NITROGEN, 2.999d);
        PlantInstance damaged = plant(two); float shortBefore = damaged.health(); cycle(damaged, two, 100, shortCell);
        assertEquals(0d, shortCell.moles.get(GasType.OXYGEN)); assertEquals(2.999d, shortCell.moles.get(GasType.NITROGEN));
        assertEquals(baseline.health() - 1, damaged.health(), "one missing species causes one source speed-unit of damage");
        Cell bothMissing = new Cell(); PlantInstance twiceDamaged = plant(two);
        cycle(twiceDamaged, two, 100, bothMissing);
        assertEquals(baseline.health() - 2, twiceDamaged.health(), "damage counts each missing gas species");
    }

    @Test void pressureUsesPostConsumptionMixtureAndExudationFollowsPressureCheckAtUnchangedTemperature() {
        SeedProfile s = seed(Map.of(GasType.OXYGEN, 2f), Map.of(GasType.NITROGEN, 2f), 20);
        Cell cell = new Cell(); cell.pressure = 51; cell.moles.put(GasType.OXYGEN, 2d);
        PlantInstance p = plant(s); cycle(p, s, 100, cell);
        PlantInstance baseline = plant(seed(Map.of(), Map.of(), 20)); Cell baselineCell = new Cell(); baselineCell.pressure = 51;
        cycle(baseline, baseline.effectiveProfile(), 100, baselineCell);
        assertEquals(baseline.health() - 1, p.health(), "pressure crosses low tolerance only after consumption");
        assertEquals(1, cell.exudeCalls); assertEquals(293.15f, cell.temperature);
    }

    @Test void exudationUsesTwoMidpointEvenRoundsThenMinimumOneAndPerEntryCount() {
        SeedProfile ties = seed(Map.of(), Map.of(GasType.OXYGEN, .5f), 3f);
        Cell one = new Cell(); cycle(plant(ties), ties, 100, one);
        assertEquals(2d, one.moles.get(GasType.OXYGEN), "Round(.5 * Round(3)) = Round(1.5) = 2");
        SeedProfile multi = seed(Map.of(), Map.of(GasType.OXYGEN, 0f, GasType.NITROGEN, .5f), 3f);
        Cell two = new Cell(); cycle(plant(multi), multi, 100, two);
        assertEquals(1d, two.moles.get(GasType.OXYGEN));
        assertEquals(1d, two.moles.get(GasType.NITROGEN), "Round(.5 * 3 / 2) is ties-to-even 1");
    }

    @Test void exchangeRunsOnlyOnCycleAndUsesFreshlyResolvedPostMutationGasMap() {
        SeedProfile original = seed(Map.of(), Map.of(), 20);
        SeedProfile mutated = seed(Map.of(GasType.OXYGEN, 1f), Map.of(), 20);
        PlantInstance p = plant(original); Cell cell = new Cell(); cell.moles.put(GasType.OXYGEN, 1d);
        PlantMetabolismEvaluator.poll(p, TRAY, p::effectiveProfile, 60, ignored -> {}, RANDOM,
                Optional.of(new PlantMetabolismEvaluator.Environment(100, 293.15f)), ignored -> {}).orElseThrow();
        assertEquals(0, cell.consumeCalls);
        PlantMetabolismEvaluator.poll(p, TRAY, p::effectiveProfile, 120, ignored -> {}, RANDOM,
                Optional.of(new PlantMetabolismEvaluator.Environment(100, 293.15f)),
                instance -> instance.genetics(new GeneticValue(id("seed"), mutated)), cell).orElseThrow();
        assertEquals(1, cell.consumeCalls); assertEquals(0d, cell.moles.get(GasType.OXYGEN));
        assertTrue(original.consumeGasses().isEmpty()); assertEquals(1f, mutated.consumeGasses().get(GasType.OXYGEN));
    }

    @Test void missingAdapterAndFailedExteriorOrUnavailableWritesNeverClaimSuccessfulMetabolism() {
        SeedProfile required = seed(Map.of(GasType.OXYGEN, 1f), Map.of(), 20);
        PlantInstance without = plant(required);
        var failed = PlantMetabolismEvaluator.cycle(without, TRAY, required, 100, RANDOM,
                Optional.of(new PlantMetabolismEvaluator.Environment(100, 293.15f)));
        assertFalse(failed.changed());
        assertTrue(failed.gasExchange().attempted());
        assertEquals(List.of(GasType.OXYGEN), failed.gasExchange().unmetRequiredGases());
        for (boolean exterior : new boolean[]{false, true}) {
            Cell cell = new Cell(); cell.writable = false; cell.exterior = exterior;
            cell.moles.put(GasType.OXYGEN, 1d);
            PlantInstance p = plant(required); cycle(p, required, 100, cell);
            PlantInstance baseline = plant(seed(Map.of(), Map.of(), 20)); cycle(baseline, baseline.effectiveProfile(), 100, new Cell());
            assertEquals(1d, cell.moles.get(GasType.OXYGEN)); assertEquals(baseline.health() - 1, p.health());
        }
        Cell unavailable = new Cell(); unavailable.available = false;
        PlantInstance p = plant(required); float health = p.health();
        assertFalse(cycle(p, required, 100, unavailable).changed());
        assertEquals(health, p.health()); assertEquals(0, unavailable.consumeCalls);
    }

    @Test void zeroConsumptionNeedsNoCellWriteButZeroExudationStillRequiresMinimumOneMoleWrite() {
        SeedProfile zeroNeed = seed(Map.of(GasType.OXYGEN, 0f), Map.of(), 20);
        PlantInstance noContext = plant(zeroNeed);
        assertTrue(PlantMetabolismEvaluator.cycle(noContext, TRAY, zeroNeed, 100, RANDOM,
                Optional.of(new PlantMetabolismEvaluator.Environment(100, 293.15f))).changed());
        PlantInstance baseline = plant(seed(Map.of(), Map.of(), 20)); cycle(baseline, baseline.effectiveProfile(), 100, new Cell());
        assertEquals(baseline.health(), noContext.health());
        Cell unavailable = new Cell(); unavailable.available = false;
        PlantInstance withContext = plant(zeroNeed);
        assertFalse(cycle(withContext, zeroNeed, 100, unavailable).changed());
        assertEquals(0, unavailable.consumeCalls); assertEquals(100f, withContext.health());

        SeedProfile zeroExude = seed(Map.of(), Map.of(GasType.OXYGEN, 0f), 0);
        Cell writable = new Cell(); var success = cycle(plant(zeroExude), zeroExude, 100, writable);
        assertTrue(success.gasExchange().attempted()); assertTrue(success.gasExchange().failedExudationGases().isEmpty());
        assertEquals(1d, writable.moles.get(GasType.OXYGEN));
        Cell exterior = new Cell(); exterior.exterior = true;
        var failedExude = cycle(plant(zeroExude), zeroExude, 100, exterior);
        assertEquals(List.of(GasType.OXYGEN), failedExude.gasExchange().failedExudationGases());
        assertEquals(0d, exterior.moles.get(GasType.OXYGEN));
    }

    @Test void noDueCycleAndDeadHolderDoNotPublishAnAttemptedGasReport() {
        SeedProfile s = seed(Map.of(), Map.of(GasType.OXYGEN, 1f), 20);
        PlantInstance p = plant(s); Cell cell = new Cell();
        var initialPoll = PlantMetabolismEvaluator.poll(p, TRAY, p::effectiveProfile, 0, ignored -> {}, RANDOM,
                cell.environment(), ignored -> {}, cell).orElseThrow();
        assertFalse(initialPoll.gasExchange().attempted());
        assertTrue(PlantMetabolismEvaluator.poll(p, TRAY, p::effectiveProfile, 1, ignored -> {}, RANDOM,
                cell.environment(), ignored -> {}, cell).isEmpty());
        p.state(0, 0, 0, 0, false, true);
        var dead = cycle(p, s, 100, cell);
        assertFalse(dead.gasExchange().attempted()); assertTrue(dead.gasExchange().failedExudationGases().isEmpty());
    }
}
