package com.juicyslew.moonstation14.ms14.atmos.reaction;

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
        return new GasReactionData(Map.of(fuel, .01, GasType.OXYGEN, .01),
                373.149, 2000, 0, priority, List.of(new GasReactionData.Effect(effect)));
    }

    private static GasMixture mix(double temperature, double tritium, double plasma, double oxygen, double n2o) {
        return new GasMixture(Map.of(GasType.TRITIUM, tritium, GasType.PLASMA, plasma,
                GasType.OXYGEN, oxygen, GasType.NITROUS_OXIDE, n2o), temperature);
    }

    private static void budget(GasMixture before, HotspotKernel.Result result) {
        for (GasType gas : GasType.values()) {
            double events = result.events().stream().mapToDouble(e -> e.speciesDelta().getOrDefault(gas, 0.0)).sum();
            assertEquals(before.moles(gas) + events, result.mixture().moles(gas), 1e-10, gas.name());
        }
        double heat = result.events().stream().mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum();
        assertEquals(before.thermalEnergy() + heat, result.mixture().thermalEnergy(),
                Math.max(1e-8, before.thermalEnergy() * 1e-12));
        assertTrue(Double.isFinite(result.mixture().heatCapacity()));
        assertTrue(result.mixture().heatCapacity() >= 0);
        assertTrue(Double.isFinite(result.mixture().temperatureKelvin()));
    }

    @Test void coldFuelOxygenAndNoActualFireQuenchWithoutChange() {
        for (var gas : List.of(mix(373.15, 1, 0, 1, 0), mix(400, 0, 0, 1, 0),
                mix(400, 1, 0, 0, 0), mix(373.151, 0, 1, 100, 0),
                mix(850, 0, 0, 0, 2))) {
            var result = HotspotKernel.evaluate(gas, CATALOG, null);
            assertSame(gas, result.mixture());
            assertTrue(result.nextState().isEmpty());
            assertTrue(result.events().isEmpty());
            assertEquals(0, result.totalFireExtentMoles());
            budget(gas, result);
        }
    }

    @Test void seedPartitionAndMergeAccountForSpeciesAndJoules() {
        var gas = mix(400, 1, 0, 1, 2);
        var result = HotspotKernel.evaluate(gas, CATALOG, null);
        assertEquals(1, result.events().size());
        assertEquals(GasReactionData.EffectType.TRITIUM_FIRE, result.events().getFirst().effect());
        assertEquals(.001, result.totalFireExtentMoles(), 1e-12);
        assertEquals(.999, result.mixture().moles(GasType.TRITIUM), 1e-12);
        assertEquals(.9995, result.mixture().moles(GasType.OXYGEN), 1e-12);
        assertEquals(.001, result.mixture().moles(GasType.WATER_VAPOR), 1e-12);
        assertEquals(2, result.mixture().moles(GasType.NITROUS_OXIDE));
        assertEquals(284, result.events().getFirst().energyDeltaJoules(), 1e-8);
        assertEquals(.14, result.nextState().orElseThrow().fraction(), 1e-12);
        budget(gas, result);
    }

    @Test void growthThresholdOnlyBurnsFullCellOnFollowingPass() {
        var gas = mix(400, 1, 0, 1, 0);
        var near = HotspotKernel.evaluate(gas, CATALOG, new HotspotKernel.HotspotState(.94, 400));
        assertEquals(.0094, near.totalFireExtentMoles(), 1e-12);
        assertEquals(1, near.nextState().orElseThrow().fraction());
        budget(gas, near);
        var full = HotspotKernel.evaluate(near.mixture(), CATALOG, near.nextState().orElseThrow());
        assertTrue(full.totalFireExtentMoles() > near.totalFireExtentMoles());
        assertEquals(1, full.nextState().orElseThrow().fraction());
        budget(near.mixture(), full);
    }

    @Test void reconcilesStaleTemperatureWithoutAddingHeatAndRejectsInvalidState() {
        var gas = mix(400, 1, 0, 1, 0);
        var stale = HotspotKernel.evaluate(gas, CATALOG, new HotspotKernel.HotspotState(.1, 500));
        budget(gas, stale);
        assertEquals(stale.mixture().temperatureKelvin(), stale.nextState().orElseThrow().temperatureKelvin());
        assertThrows(IllegalArgumentException.class, () -> new HotspotKernel.HotspotState(Double.NaN, 400));
        assertThrows(IllegalArgumentException.class, () -> new HotspotKernel.HotspotState(-1, 400));
        var tiny = mix(400, .001, 0, .001, 0);
        assertSame(tiny, HotspotKernel.evaluate(tiny, CATALOG, null).mixture());
        var vacuum = GasMixture.vacuum();
        budget(vacuum, HotspotKernel.evaluate(vacuum, CATALOG, null));
    }

    @Test void exactFullThresholdAndLowInventorySeed() {
        var gas = mix(400, .01, 0, .01, 0);
        var seed = HotspotKernel.evaluate(gas, CATALOG, null);
        assertEquals(1, seed.nextState().orElseThrow().fraction());
        assertEquals(.0001, seed.totalFireExtentMoles(), 1e-12);
        budget(gas, seed);
        var below = HotspotKernel.evaluate(mix(400, 1, 0, 1, 0), CATALOG,
                new HotspotKernel.HotspotState(Math.nextDown(.95), 400));
        var at = HotspotKernel.evaluate(mix(400, 1, 0, 1, 0), CATALOG,
                new HotspotKernel.HotspotState(.95, 400));
        assertTrue(below.totalFireExtentMoles() < at.totalFireExtentMoles());
        assertEquals(.01, at.totalFireExtentMoles(), 1e-12);
    }

    @Test void mixedFuelSkipsNonburningPlasmaSeedAndIgnitesEligibleTritium() {
        var requested = mix(400, .01, 1, 1, 0);
        var requestedResult = HotspotKernel.evaluate(requested, CATALOG, null);
        assertEquals(1, requestedResult.nextState().orElseThrow().fraction());
        assertEquals(GasReactionData.EffectType.TRITIUM_FIRE, requestedResult.events().getFirst().effect());
        assertEquals(.01, requestedResult.events().getFirst().extentMoles(), 1e-12);
        budget(requested, requestedResult);

        var before = mix(400, .01, 1, .1, 0);
        var result = HotspotKernel.evaluate(before, CATALOG, null);
        assertEquals(1, result.events().size());
        assertEquals(GasReactionData.EffectType.TRITIUM_FIRE, result.events().getFirst().effect());
        assertEquals(.001, result.totalFireExtentMoles(), 1e-12);
        assertEquals(1, result.nextState().orElseThrow().fraction());
        assertEquals(1, result.mixture().moles(GasType.PLASMA));
        budget(before, result);

        var below = mix(400, Math.nextDown(.01), 1, .1, 0);
        var quenched = HotspotKernel.evaluate(below, CATALOG, null);
        assertSame(below, quenched.mixture());
        assertTrue(quenched.events().isEmpty());
        assertTrue(quenched.nextState().isEmpty());
        budget(below, quenched);
    }

    @Test void plasmaSeedMustPassStrictPreliminaryRateWithoutOverIgniting() {
        var before = mix(400, 0, 2, 2, 0);
        var result = HotspotKernel.evaluate(before, CATALOG, null);
        assertEquals(1, result.events().size());
        assertEquals(GasReactionData.EffectType.PLASMA_FIRE, result.events().getFirst().effect());
        assertTrue(result.totalFireExtentMoles() > 0);
        double burned = before.moles(GasType.PLASMA) - result.mixture().moles(GasType.PLASMA);
        assertTrue(burned > .0003, "preliminary rate must be strictly above the cutoff");
        assertTrue(burned < .000301, "seed should not fall back to a full-cell burn");
        budget(before, result);

        var ordinary = HotspotKernel.evaluate(mix(1643.15, 0, 1, 100, 0), CATALOG, null);
        assertEquals(.1 + 40 * ordinary.totalFireExtentMoles(),
                ordinary.nextState().orElseThrow().fraction(), 1e-12);
        assertTrue(ordinary.totalFireExtentMoles() > 0);
        assertTrue(ordinary.totalFireExtentMoles() < .05);
        var below = mix(400, 0, Math.nextDown(.01), 100, 0);
        assertTrue(HotspotKernel.evaluate(below, CATALOG, null).events().isEmpty());
    }
}
