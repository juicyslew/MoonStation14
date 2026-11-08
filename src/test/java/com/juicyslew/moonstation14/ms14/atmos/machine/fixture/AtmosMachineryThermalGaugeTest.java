package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineryThermalGaugeTest {
    @Test void heaterPrimingBoundIsFiniteAndRejectsInvalidProfiles() {
        assertEquals(8, AtmosMachineryFixtureGameTests.heaterPrimingEvents(1.2, 2, 1, .2, .6, 100, 18));
        assertEquals(136, AtmosMachineryFixtureGameTests.heaterPrimingTicks(8, 12));
        assertEquals(-1, AtmosMachineryFixtureGameTests.heaterPrimingTicks(0, 12));
        assertEquals(-1, AtmosMachineryFixtureGameTests.heaterPrimingEvents(0, 2, 1, .2, .6, 100, 18));
        assertEquals(-1, AtmosMachineryFixtureGameTests.heaterPrimingEvents(1.2, 2, 1, 2, .6, 100, 18));
    }

    @Test void thermalLightsRequireMeasuredDirectionAndCommittedWork() {
        assertTrue(AtmosMachineryThermalGauge.warming(293.15,300,8,4));
        assertTrue(AtmosMachineryThermalGauge.cooling(293.15,280,8,-4));
        assertFalse(AtmosMachineryThermalGauge.warming(293.15,300,0,4),"unpaid work cannot light");
        assertFalse(AtmosMachineryThermalGauge.warming(293.15,300,8,0),"power/temperature target without committed heat does not light");
        assertFalse(AtmosMachineryThermalGauge.cooling(293.15,280,8,2),"wrong-sign work cannot claim cooling");
        assertFalse(AtmosMachineryThermalGauge.warming(293.15,293.15+AtmosMachineryThermalGauge.floatDeadband(293.15,293.15),8,4),
                "float-precision deadband avoids readout noise claiming transfer");
    }

    @Test void exchangerLightsOnlyForOppositeMeasuredEnergyAndMatchingReceipt() {
        assertTrue(AtmosMachineryThermalGauge.oppositeExchange(1000,900,500,600,100));
        assertTrue(AtmosMachineryThermalGauge.oppositeExchange(900,1000,600,500,-100));
        assertFalse(AtmosMachineryThermalGauge.oppositeExchange(1000,900,500,600,0),"paid/committed receipt is required");
        assertFalse(AtmosMachineryThermalGauge.oppositeExchange(1000,900,500,600,-100),"receipt direction must match measured pipe change");
        assertFalse(AtmosMachineryThermalGauge.oppositeExchange(1000,900,500,500,100),"one-sided change is not opposite exchange");
    }
}
