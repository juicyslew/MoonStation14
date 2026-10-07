package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HotspotKernelTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"), fire(GasType.TRITIUM, -1,
                    GasReactionData.EffectType.TRITIUM_FIRE),
            ResourceLocation.parse("moonstation14:plasma_fire"), fire(GasType.PLASMA, -2,
                    GasReactionData.EffectType.PLASMA_FIRE),
            ResourceLocation.parse("moonstation14:n2o_decomposition"), new GasReactionData(
                    Map.of(GasType.NITROUS_OXIDE, .01), 850, 2000, 0, 0,
                    List.of(new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION)))));

    private static GasReactionData fire(GasType fuel, int priority, GasReactionData.EffectType effect) {
        return new GasReactionData(Map.of(fuel, .01, GasType.OXYGEN, .01), 373.149, 2000, 0, priority,
                List.of(new GasReactionData.Effect(effect)));
    }

    private static GasMixture mix(double temperature, double tritium, double plasma, double oxygen, double n2o) {
        return new GasMixture(Map.of(GasType.TRITIUM, tritium, GasType.PLASMA, plasma,
                GasType.OXYGEN, oxygen, GasType.NITROUS_OXIDE, n2o), temperature);
    }

    private static HotspotState ready(double liters, double temperature) {
        return new HotspotState(liters, temperature, true, false);
    }

    @Test void onlyAlreadyProcessedHotspotsResetExcitedGroupCooldowns() {
        assertFalse(HotspotKernel.shouldResetExcitedGroupCooldowns(null));
        assertFalse(HotspotKernel.shouldResetExcitedGroupCooldowns(new HotspotState(0, 200, false, false)));
        assertTrue(HotspotKernel.shouldResetExcitedGroupCooldowns(new HotspotState(0, 200, true, false)),
                "an already-processed but now-fizzling hotspot still resets before viability checks");
    }

    @Test void firstDueVisitOnlyClearsSkipBeforeAnyQuenchOrTemperatureCheck() {
        var gas = mix(293, .1, 0, 1, 0);
        var state = new HotspotState(.01, 200, false, true);
        var result = HotspotKernel.process(CATALOG, gas, state, 0);
        assertSame(gas, result.mixture());
        assertTrue(result.events().isEmpty());
        assertEquals(new HotspotState(.01, 200, true, true), result.nextState().orElseThrow());
    }

    @Test void temperatureMetadataBurnsHotPartitionWithoutHeatingColdBulk() {
        var gas = mix(300, 1, 0, 1, 0);
        var result = HotspotKernel.process(CATALOG, gas, ready(250, 400), 0);
        assertFalse(result.events().isEmpty());
        assertEquals(.0025, result.fireExtentMoles(), 1e-12);
        assertEquals(1, result.mixture().moles(GasType.TRITIUM) + result.mixture().moles(GasType.WATER_VAPOR), 1e-7);
        assertTrue(result.mixture().temperatureKelvin() < 400);
        assertEquals(result.fireExtentMoles() > 0
                && result.nextState().orElseThrow().temperatureKelvin() > 423.15,
                result.spreadEligibleThisPass());
    }

    @Test void partialSpreadRequiresPositiveFireExtentAndHotLocalFlame() {
        var gas = mix(300, 1, 0, 1, 0);
        var hot = HotspotKernel.process(CATALOG, gas, ready(250, 900), 0);
        assertTrue(hot.fireExtentMoles() > 0);
        assertTrue(hot.nextState().orElseThrow().temperatureKelvin() > 423.15);
        assertTrue(hot.spreadEligibleThisPass());
        var warm = HotspotKernel.process(CATALOG, gas, ready(250, 400), 0);
        assertEquals(warm.fireExtentMoles() > 0 && warm.nextState().orElseThrow().temperatureKelvin() > 423.15,
                warm.spreadEligibleThisPass());
        assertFalse(HotspotKernel.process(new PrototypeCatalog<>(Map.of()), gas, ready(250, 900), 0)
                .spreadEligibleThisPass());
    }

    @Test void extinctionThresholdIsPointFourLitersNotPointSixteenOrOne() {
        var gas = mix(400, 1, 0, 1, 0);
        double thresholdLiters = AtmosphereUnits.referenceTileQuantity(1.0);
        for (double liters : new double[]{.16, .3}) {
            var survives = HotspotKernel.process(CATALOG, gas, ready(liters, 400), 0);
            assertTrue(survives.nextState().isEmpty(), liters + " liters is below the 0.4 L minimum");
        }
        var at = HotspotKernel.process(CATALOG, gas, ready(thresholdLiters, 400), 0);
        assertTrue(at.nextState().isEmpty());
        assertTrue(at.events().isEmpty());
        var above = HotspotKernel.process(CATALOG, gas,
                ready(.40001, 400), 0);
        assertTrue(above.nextState().isPresent(), "volume above the extinction gate persists even with insufficient reaction inventory");
        assertTrue(above.events().isEmpty());
    }

    @Test void boostControlsWhetherExistingMetadataChangesAndDoesNotRescaleExistingVolume() {
        var gas = mix(400, 1, 0, 1, 0);
        var old = new HotspotState(100, 500, false, true);
        var unchanged = HotspotKernel.expose(gas, old, 800, 250, false, true).orElseThrow();
        assertEquals(old, unchanged);
        var boosted = HotspotKernel.expose(gas, old, 800, 250, true, true).orElseThrow();
        assertEquals(250, boosted.volumeLiters());
        assertEquals(800, boosted.temperatureKelvin());
        assertFalse(boosted.skippedFirstProcess());
        assertTrue(boosted.bypass());
    }

    @Test void newQuarterCellExposureScalesByTwentyFiveOnceAndRequiresStrictFuelAndOxygen() {
        var gas = mix(400, 1, 0, 1, 0);
        var seeded = HotspotKernel.expose(gas, null, 400, 250, false, true).orElseThrow();
        assertEquals(6250, seeded.volumeLiters());
        double cutoff = AtmosphereUnits.referenceTileQuantity(.5);
        double belowCutoff = Math.nextDown((float) cutoff);
        var atCutoff = new GasMixture(Map.of(GasType.OXYGEN, belowCutoff,
                GasType.TRITIUM, belowCutoff, GasType.PLASMA, 0.0), 400);
        assertTrue(HotspotKernel.expose(atCutoff, null, 400, 10, false, true).isEmpty());
        var belowFuel = new GasMixture(Map.of(GasType.OXYGEN, cutoff + .1,
                GasType.TRITIUM, belowCutoff, GasType.PLASMA, 0.0), 400);
        assertTrue(HotspotKernel.expose(belowFuel, null, 400, 10, false, true).isEmpty());
    }

    @Test void ignitionGateUsesFloatNormalizedThresholdAndCombinedFuel() {
        double threshold = (double) (float) AtmosphereUnits.referenceTileQuantity(.5);
        var exact = mix(400, threshold, 0, threshold, 0);
        assertTrue(HotspotKernel.expose(exact, null, 400, 10, false, true).isEmpty());
        var mixedExact = mix(400, (double) .1f, (double) .1f, Math.nextUp((float) threshold), 0);
        assertTrue(HotspotKernel.expose(mixedExact, null, 400, 10, false, true).isEmpty());
        var above = mix(400, Math.nextUp((float) threshold), 0, Math.nextUp((float) threshold), 0);
        assertTrue(HotspotKernel.expose(above, null, 400, 10, false, true).isPresent());
    }

    @Test void bypassRequiresStrictlyMoreThanNinetyFivePercentAndReusesPriorExtent() {
        var gas = mix(500, 1, 0, 1, 0);
        var bypass = HotspotKernel.process(CATALOG, gas, ready(951, 900), .012);
        assertSame(gas, bypass.mixture());
        assertTrue(bypass.events().isEmpty());
        assertEquals(.012, bypass.fireExtentMoles());
        assertEquals(480, bypass.nextState().orElseThrow().volumeLiters());
        var exactly = HotspotKernel.process(CATALOG, gas, ready(950, 900), .012);
        assertFalse(exactly.events().isEmpty());
    }

    @Test void fullPriorityCatalogRunsButOnlyFireEventsContributeToExtent() {
        var gas = mix(900, 1, 0, 1, 2);
        var result = HotspotKernel.process(CATALOG, gas, ready(250, 900), 0);
        assertTrue(result.events().stream().anyMatch(e -> e.effect() == GasReactionData.EffectType.N2O_DECOMPOSITION));
        assertTrue(result.events().stream().anyMatch(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE));
        double fireExtent = result.events().stream()
                .filter(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                        || e.effect() == GasReactionData.EffectType.PLASMA_FIRE)
                .mapToDouble(GasReactionEvaluator.Event::extentMoles).sum();
        assertEquals(fireExtent, result.fireExtentMoles(), 1e-12);
        assertEquals(fireExtent * 40000, result.nextState().orElseThrow().volumeLiters(), 1e-8);
    }

    @Test void lastFireEffectResultWinsEvenWhenItHasNoReactionEvent() {
        var gas = mix(373.15, 1, .02, 1, 0);
        var result = HotspotKernel.process(CATALOG, gas, ready(250, 373.15), 0);
        assertTrue(result.events().stream().anyMatch(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE));
        assertFalse(result.events().stream().anyMatch(e -> e.effect() == GasReactionData.EffectType.PLASMA_FIRE));
        assertEquals(0, result.fireExtentMoles());
        assertEquals(0, result.nextState().orElseThrow().volumeLiters());
    }

    @Test void zeroFireExtentKeepsZeroVolumeStateUntilFollowingDuePass() {
        var empty = new PrototypeCatalog<GasReactionData>(Map.of());
        var gas = mix(400, 1, 0, 1, 0);
        var first = HotspotKernel.process(empty, gas, ready(20, 400), 0);
        assertTrue(first.events().isEmpty());
        assertEquals(0, first.nextState().orElseThrow().volumeLiters());
        var next = HotspotKernel.process(empty, gas, first.nextState().orElseThrow(), 0);
        assertTrue(next.nextState().isEmpty());
    }

    @Test void priorWholeFireResultIsReusedOnlyByBypass() {
        var empty = new PrototypeCatalog<GasReactionData>(Map.of());
        var gas = mix(400, 1, 0, 1, 0);
        var partial = HotspotKernel.process(empty, gas, ready(100, 400), 7);
        assertEquals(0, partial.nextState().orElseThrow().volumeLiters());
        var bypass = HotspotKernel.process(empty, gas, ready(951, 400), 7);
        assertSame(gas, bypass.mixture());
        assertTrue(bypass.events().isEmpty());
        assertEquals(280000, bypass.nextState().orElseThrow().volumeLiters());
    }

    @Test void explicitHeatTransferReconciliationPreservesMetadataEvenWhenGasQuenches() {
        var before = mix(500, 1, 0, 1, 0);
        var state = ready(250, 600);
        var reconciled = HotspotKernel.reconcileAfterHeatTransfer(before, GasMixture.vacuum(), state).orElseThrow();
        assertEquals(state, reconciled);
    }

    @Test void explicitStateAndExposureRetainInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> new HotspotState(Double.NaN, 400, true, false));
        assertThrows(IllegalArgumentException.class, () -> new HotspotState(-1, 400, true, false));
        assertThrows(IllegalArgumentException.class, () -> new HotspotState(1, Double.POSITIVE_INFINITY, true, false));
        var gas = mix(400, 1, 0, 1, 0);
        assertTrue(HotspotKernel.expose(gas, null, Double.NaN, 10, false, true).isEmpty());
        assertTrue(HotspotKernel.expose(gas, null, 400, Double.POSITIVE_INFINITY, false, true).isEmpty());
        assertTrue(HotspotKernel.expose(gas, null, 400, 0, false, true).isEmpty());
    }
}
