package com.juicyslew.moonstation14.ms14.atmos.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtmosphereTransferTest {
    @Test
    void exchangeConservesEveryGasAndThermalEnergy() {
        GasMixture first = new GasMixture(Map.of(GasType.OXYGEN, 4.0, GasType.NITROGEN, 1.0), 500.0);
        GasMixture second = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.NITROGEN, 5.0), 200.0);
        AtmosphereTransfer.Result result = AtmosphereTransfer.step(first, second, 0.25);
        assertEquals(first.moles(GasType.OXYGEN) + second.moles(GasType.OXYGEN),
                result.first().moles(GasType.OXYGEN) + result.second().moles(GasType.OXYGEN), 1e-12);
        assertEquals(first.moles(GasType.NITROGEN) + second.moles(GasType.NITROGEN),
                result.first().moles(GasType.NITROGEN) + result.second().moles(GasType.NITROGEN), 1e-12);
        assertEquals(first.thermalEnergy() + second.thermalEnergy(),
                result.first().thermalEnergy() + result.second().thermalEnergy(), 1e-9);
    }

    @Test
    void transferIsSymmetricAndHotColdPairMovesTowardEquilibrium() {
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 600.0);
        GasMixture cold = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 200.0);
        AtmosphereTransfer.Result forward = AtmosphereTransfer.step(hot, cold, 0.4);
        AtmosphereTransfer.Result reversed = AtmosphereTransfer.step(cold, hot, 0.4);
        assertEquals(forward.first().temperatureKelvin(), reversed.second().temperatureKelvin(), 1e-10);
        assertEquals(forward.second().temperatureKelvin(), reversed.first().temperatureKelvin(), 1e-10);
        assertEquals(520.0, forward.first().temperatureKelvin(), 1e-10);
        assertEquals(280.0, forward.second().temperatureKelvin(), 1e-10);
        GasMixture nearEquilibriumHot = hot;
        GasMixture nearEquilibriumCold = cold;
        for (int i = 0; i < 20; i++) {
            AtmosphereTransfer.Result next = AtmosphereTransfer.step(nearEquilibriumHot, nearEquilibriumCold, 0.4);
            nearEquilibriumHot = next.first();
            nearEquilibriumCold = next.second();
        }
        assertEquals(400.0, nearEquilibriumHot.temperatureKelvin(), 0.01);
        assertEquals(400.0, nearEquilibriumCold.temperatureKelvin(), 0.01);
    }

    @Test
    void vacuumReceivesGasAndEnergyConservatively() {
        GasMixture occupied = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 300.0);
        GasMixture vacuum = GasMixture.vacuum();
        double energy = occupied.thermalEnergy() + vacuum.thermalEnergy();
        AtmosphereTransfer.Result result = AtmosphereTransfer.step(occupied, vacuum, 0.5);
        assertEquals(1.0, result.second().moles(GasType.OXYGEN), 1e-12);
        assertEquals(energy, result.first().thermalEnergy() + result.second().thermalEnergy(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> AtmosphereTransfer.step(occupied, vacuum, 0.51));
    }
}
