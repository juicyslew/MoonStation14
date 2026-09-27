package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalExposureMathTest {
    private static final double EPSILON = 1e-9;

    @Test
    void humanProfileStartsAtSs14BaseTemperatureAndUsesApproximateHeatCapacity() {
        var atBaseline = expose(310.15, gas(310.15), 1.0);
        assertEquals(310.15, atBaseline.bodyTemperatureKelvin(), 0.0);
        assertEquals(42.0 * 70.0,
                ThermalExposureMath.ThermalProfile.HUMAN.bodyHeatCapacityJoulesPerKelvin(), 0.0);
    }

    @Test
    void normalAirMovesBodySlightlyTowardAmbientAndDoesNotMutateGas() {
        GasMixture gas = GasMixture.breathableAir();
        double originalEnergy = gas.thermalEnergy();
        double originalMoles = gas.totalMoles();
        ThermalExposureMath.ExposureResult result = expose(310.15, gas, 1.0);

        assertTrue(result.bodyTemperatureKelvin() < 310.15);
        assertTrue(result.bodyTemperatureKelvin() > 300.0);
        assertEquals(0.0, result.damage().heat(), EPSILON);
        assertEquals(0.0, result.damage().cold(), EPSILON);
        assertEquals(originalEnergy, gas.thermalEnergy(), 0.0);
        assertEquals(originalMoles, gas.totalMoles(), 0.0);
    }

    @Test
    void hotAndColdGasMoveBodyInExpectedDirectionSmoothly() {
        var mildlyHot = expose(310.15, gas(330.0), 1.0);
        var hotter = expose(310.15, gas(400.0), 1.0);
        var mildlyCold = expose(310.15, gas(250.0), 1.0);
        var colder = expose(310.15, gas(100.0), 1.0);

        assertTrue(mildlyHot.bodyTemperatureKelvin() > 310.15);
        assertTrue(hotter.bodyTemperatureKelvin() > mildlyHot.bodyTemperatureKelvin());
        assertTrue(mildlyCold.bodyTemperatureKelvin() < 310.15);
        assertTrue(colder.bodyTemperatureKelvin() < mildlyCold.bodyTemperatureKelvin());
    }

    @Test
    void vacuumHasNoHeatExchange() {
        var result = expose(310.15, GasMixture.vacuum(), 1.0);
        assertEquals(310.15, result.bodyTemperatureKelvin(), 0.0);
        assertEquals(0.0, result.environmentEnergyDeltaJoules(), 0.0);
    }

    @Test
    void damageMatchesSs14HeatLogisticAndColdSquareRootAtGoldenTemperatures() {
        assertEquals(0.0, expose(325.0, gas(325.0), 1.0).damage().heat(), 0.0);
        assertEquals(0.0, expose(260.0, gas(260.0), 1.0).damage().cold(), 0.0);
        assertEquals(0.029999937500156726, expose(326.0, gas(326.0), 1.0).damage().heat(), EPSILON);
        assertEquals(2.9390239488445102, expose(425.0, gas(425.0), 1.0).damage().heat(), EPSILON);
        assertEquals(12.0, expose(10325.0, gas(10325.0), 1.0).damage().heat(), EPSILON);
        assertEquals(0.11094003924504584, expose(255.0, gas(255.0), 1.0).damage().cold(), EPSILON);
    }

    @Test
    void damageScalesWithElapsedTimeWithoutClampingTotal() {
        var oneSecondHeat = expose(10325.0, gas(10325.0), 1.0);
        var twoSecondHeat = expose(10325.0, gas(10325.0), 2.0);
        var oneSecondCold = expose(255.0, gas(255.0), 1.0);
        var twoSecondCold = expose(255.0, gas(255.0), 2.0);

        assertEquals(12.0, oneSecondHeat.damage().heat(), EPSILON);
        assertEquals(24.0, twoSecondHeat.damage().heat(), EPSILON);
        assertEquals(0.11094003924504584, oneSecondCold.damage().cold(), EPSILON);
        assertEquals(0.22188007849009167, twoSecondCold.damage().cold(), EPSILON);
    }

    @Test
    void bodyStateDamageIsAvailableWithoutAnAtmosphereMixture() {
        assertEquals(0.029999937500156726,
                ThermalExposureMath.damageAt(326.0, ThermalExposureMath.ThermalProfile.HUMAN, 1.0).heat(), EPSILON);
        assertEquals(0.0,
                ThermalExposureMath.damageAt(310.15, ThermalExposureMath.ThermalProfile.HUMAN, 1.0).heat(), 0.0);
        assertThrows(IllegalArgumentException.class, () ->
                ThermalExposureMath.damageAt(Double.NaN, ThermalExposureMath.ThermalProfile.HUMAN, 1.0));
    }

    @Test
    void rejectsInvalidTemperaturesDurationsAndProfiles() {
        assertThrows(IllegalArgumentException.class, () -> expose(0.0, gas(300.0), 1.0));
        assertThrows(IllegalArgumentException.class, () -> expose(Double.NaN, gas(300.0), 1.0));
        assertThrows(IllegalArgumentException.class, () -> expose(300.0, gas(300.0), 0.0));
        assertThrows(IllegalArgumentException.class, () -> expose(300.0, gas(300.0), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ThermalExposureMath.ThermalProfile(
                0.0, 42.0, 0.1, 325.0, 260.0, 1.5, 0.1, 8.0));
        assertThrows(IllegalArgumentException.class, () -> new ThermalExposureMath.ThermalProfile(
                70.0, Double.NaN, 0.1, 325.0, 260.0, 1.5, 0.1, 8.0));
        assertThrows(IllegalArgumentException.class, () -> new ThermalExposureMath.ThermalProfile(
                70.0, 42.0, 0.1, 325.0, 260.0, 1.5, 0.1, Double.MAX_VALUE));
    }

    private static ThermalExposureMath.ExposureResult expose(double bodyKelvin, GasMixture gas, double seconds) {
        return ThermalExposureMath.expose(bodyKelvin, gas, ThermalExposureMath.ThermalProfile.HUMAN, seconds);
    }

    private static GasMixture gas(double temperatureKelvin) {
        return new GasMixture(Map.of(GasType.OXYGEN, 1.0), temperatureKelvin);
    }
}
