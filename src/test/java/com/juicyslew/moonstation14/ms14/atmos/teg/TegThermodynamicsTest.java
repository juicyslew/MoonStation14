package com.juicyslew.moonstation14.ms14.atmos.teg;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TegThermodynamicsTest {
    @Test void upstreamReferenceVectorAndEfficiencyDeficit() {
        GasMixture hot = gas(GasType.OXYGEN, 10, 1000);
        GasMixture cold = gas(GasType.NITROGEN, 10, 300);
        TegThermodynamics.Result result = TegThermodynamics.generate(hot, cold, .65, 2);
        double transfer = 84_000 * .65 * Math.tanh(700.0 / 700.0);
        assertEquals(transfer, result.transferJoules(), 1e-3);
        assertEquals(hot.thermalEnergy() - result.first().thermalEnergy(), result.hotThermalEnergyRemovedJoules(), 0);
        assertEquals(result.second().thermalEnergy() - cold.thermalEnergy(), result.coldThermalEnergyAddedJoules(), 0);
        assertEquals(transfer * 2, result.generatedEnergyJoules(), 1e-3);
        assertEquals(84_000, result.maximumWorkJoules(), 1e-3);
        assertEquals(1000 - transfer / 200, result.first().temperatureKelvin(), 1e-3);
        assertEquals(300 + transfer * .35 / 300, result.second().temperatureKelvin(), 1e-3);
        assertEquals(10, result.first().moles(GasType.OXYGEN), 0);
        assertEquals(0, result.first().moles(GasType.NITROGEN), 0);
        assertEquals(transfer * .65, result.hotThermalEnergyRemovedJoules()
                - result.coldThermalEnergyAddedJoules(), Math.ulp((float) hot.thermalEnergy()) * 2.0);
    }

    @Test void equalTemperaturesProduceNoPower() {
        var result = TegThermodynamics.generate(gas(GasType.OXYGEN, 1, 300),
                gas(GasType.NITROGEN, 1, 300), .65, 1);
        assertEquals(0, result.generatedEnergyJoules());
        assertEquals(0, result.maximumWorkJoules());
    }

    @Test void emptyMixtureDoesNotProcess() {
        GasMixture empty = GasMixture.vacuum();
        var result = TegThermodynamics.generate(empty, gas(GasType.OXYGEN, 1, 800), .65, 1);
        assertSame(empty, result.first());
        assertEquals(0, result.generatedEnergyJoules());
        assertEquals(0, result.maximumWorkJoules());
    }

    @Test void efficiencyIsBoundedAndZeroOneEdgesAreValid() {
        GasMixture hot = gas(GasType.OXYGEN, 10, 1000);
        GasMixture cold = gas(GasType.NITROGEN, 10, 300);
        assertThrows(IllegalArgumentException.class,
                () -> TegThermodynamics.generate(hot, cold, 1.01, 1));
        var zero = TegThermodynamics.generate(hot, cold, 0, 1);
        assertEquals(0, zero.transferJoules());
        assertEquals(0, zero.hotThermalEnergyRemovedJoules());
        var one = TegThermodynamics.generate(hot, cold, 1, 1);
        assertTrue(one.transferJoules() > 0);
        assertEquals(0, one.coldThermalEnergyAddedJoules(), 0);
    }

    @Test void reportsActualFloatBackedGasEnergyChangesForUnequalHeatCapacities() {
        GasMixture hot = gas(GasType.OXYGEN, 10, 1000);
        GasMixture cold = gas(GasType.NITROGEN, 10, 300);
        var result = TegThermodynamics.generate(hot, cold, .65, 1);
        assertEquals(hot.thermalEnergy() - result.first().thermalEnergy(),
                result.hotThermalEnergyRemovedJoules(), 0);
        assertEquals(result.second().thermalEnergy() - cold.thermalEnergy(),
                result.coldThermalEnergyAddedJoules(), 0);
        assertEquals(result.transferJoules(), result.hotThermalEnergyRemovedJoules(), 0.02);
        assertEquals(result.transferJoules() * .35, result.coldThermalEnergyAddedJoules(), 0.02);
    }

    @Test void temperatureBoundariesStayRepresentableAndOutOfRangeStepIsRejected() {
        GasMixture atMin = gas(GasType.OXYGEN, 1, GasMixture.MIN_TEMPERATURE_KELVIN);
        GasMixture justHotter = gas(GasType.NITROGEN, 1, GasMixture.MIN_TEMPERATURE_KELVIN + .01);
        var minResult = TegThermodynamics.generate(justHotter, atMin, .65, 1);
        assertTrue(minResult.first().temperatureKelvin() >= GasMixture.MIN_TEMPERATURE_KELVIN);

        GasMixture atMax = gas(GasType.OXYGEN, 1, GasMixture.MAX_TEMPERATURE_KELVIN);
        GasMixture belowMax = gas(GasType.NITROGEN, 1, GasMixture.MAX_TEMPERATURE_KELVIN - 1);
        var maxResult = TegThermodynamics.generate(atMax, belowMax, .65, 1);
        assertTrue(maxResult.first().temperatureKelvin() <= GasMixture.MAX_TEMPERATURE_KELVIN);
    }

    @Test void swappedStreamsRemainSpeciesSeparateAndHotStreamCools() {
        GasMixture cold = gas(GasType.NITROGEN, 10, 300);
        GasMixture hot = gas(GasType.PLASMA, 1, 1000);
        var result = TegThermodynamics.generate(cold, hot, .65, 1);
        assertTrue(result.first().temperatureKelvin() > cold.temperatureKelvin());
        assertTrue(result.second().temperatureKelvin() < hot.temperatureKelvin());
        assertEquals(10, result.first().moles(GasType.NITROGEN), 0);
        assertEquals(1, result.second().moles(GasType.PLASMA), 0);
        assertTrue(Double.isFinite(result.generatedEnergyJoules()));
    }

    private static GasMixture gas(GasType type, double moles, double temperature) {
        return new GasMixture(Map.of(type, moles), temperature);
    }
}
