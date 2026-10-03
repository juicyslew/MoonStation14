package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotspotPartitionContinuationTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"), new GasReactionData(
                    Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01), 373.149, 2000,
                    0, -1, List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)))));

    private static GasMixture mix(double temperature) {
        return new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), temperature);
    }

    @Test void projectedHotEnergyEqualsBurnedHotPlusSweptColdBudget() {
        var gas = mix(300);
        double fraction = .25;
        double hotTemperature = 400;
        double hotEnergy = fraction * gas.heatCapacity() * hotTemperature;
        double coldEnergy = gas.thermalEnergy() - hotEnergy;
        var result = HotspotKernel.evaluate(gas, CATALOG,
                new HotspotKernel.HotspotState(fraction, hotTemperature));
        var next = result.nextState().orElseThrow();
        double growth = 40 * result.totalFireExtentMoles();
        double expectedActiveEnergy = hotEnergy
                + result.events().stream().mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum()
                + coldEnergy * growth / (1 - fraction);
        double projectedEnergy = next.fraction() * result.mixture().heatCapacity() * next.temperatureKelvin();
        assertEquals(expectedActiveEnergy, projectedEnergy, 1e-8);
        assertEquals(gas.thermalEnergy() + result.events().stream()
                .mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum(),
                result.mixture().thermalEnergy(), 1e-8);
    }

    @Test void nearFullExternallyDebitedPartitionCanContinueOnNextPass() {
        var before = mix(500);
        var afterDebit = before.withThermalEnergy(before.thermalEnergy() * .9);
        var reconciled = HotspotKernel.reconcileAfterHeatTransfer(before, afterDebit,
                new HotspotKernel.HotspotState(.96, 500)).orElseThrow();
        double hotBudget = reconciled.fraction() * afterDebit.heatCapacity() * reconciled.temperatureKelvin();
        assertTrue(hotBudget <= afterDebit.thermalEnergy());
        assertTrue(afterDebit.thermalEnergy() - hotBudget >= 0);
        var next = HotspotKernel.evaluate(afterDebit, CATALOG, reconciled);
        assertTrue(next.events().size() > 0);
        assertTrue(next.nextState().isPresent());
    }
}
