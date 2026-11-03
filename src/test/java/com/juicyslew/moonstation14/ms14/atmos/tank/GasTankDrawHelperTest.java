package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GasTankDrawHelperTest {
    @Test void drawsFixedOutletPacketProportionallyWithoutChangingTankVolume() {
        var profile = new GasTankData(.005, 293.15, Map.of(), ModGasTanks.OXYGEN_ID, 21.3, 30);
        var tank = new GasTankContents(ModGasTanks.OXYGEN_FILLED_ID,
                new GasMixture(Map.of(GasType.OXYGEN, .01, GasType.NITROGEN, .01), 293.15, .005));
        var result = GasTankDrawHelper.drawOutput(tank, profile, .5);
        assertEquals(.0005, result.inhaled().volumeCubicMeters(), 1e-8);
        assertEquals(.005, result.remaining().mixture().volumeCubicMeters(), 1e-8);
        assertEquals(result.inhaled().moles(GasType.OXYGEN), result.inhaled().moles(GasType.NITROGEN), 1e-8);
        assertEquals(.02, result.remaining().mixture().totalMoles() + result.inhaled().totalMoles(), 1e-7);
        assertEquals(.02, tank.mixture().totalMoles(), 1e-7); // original snapshot remains untouched
    }

    @Test void zeroOutletAndDepletedTankDoNotRefill() {
        var profile = new GasTankData(.005, 293.15, Map.of(), ModGasTanks.OXYGEN_ID, 0, 30);
        var tank = new GasTankContents(ModGasTanks.OXYGEN_ID, new GasMixture(Map.of(), 293.15, .005));
        var result = GasTankDrawHelper.drawOutput(tank, profile, .5);
        assertEquals(0, result.inhaled().totalMoles());
        assertEquals(0, result.remaining().mixture().totalMoles());
    }

    @Test void veryLowPressureTankGivesAvailableMixedGasAtTankTemperature() {
        var profile = new GasTankData(.005, 300, Map.of(), ModGasTanks.OXYGEN_ID, 21.3, 30);
        var tank = new GasTankContents(ModGasTanks.OXYGEN_FILLED_ID,
                new GasMixture(Map.of(GasType.OXYGEN, 1e-6, GasType.NITROGEN, 1e-6), 300, .005));
        assertTrue(tank.mixture().pressureKpa() < profile.outletPressureKpa());
        var result = GasTankDrawHelper.drawOutput(tank, profile, .5);
        assertEquals(2e-6, result.inhaled().totalMoles(), 1e-8);
        assertEquals(300, result.inhaled().temperatureKelvin(), 1e-3);
        assertEquals(result.inhaled().moles(GasType.OXYGEN), result.inhaled().moles(GasType.NITROGEN), 1e-10);
        assertEquals(0, result.remaining().mixture().totalMoles(), 0);
        assertThrows(IllegalArgumentException.class, () -> GasTankDrawHelper.drawOutput(tank, profile, Double.POSITIVE_INFINITY));
    }
}
