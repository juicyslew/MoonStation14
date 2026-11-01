package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FirelockFixtureScenarioPlanTest {
    @Test void fixtureLightingAndGasCellPlanAreBoundedAndLegacyPlanUnchanged() {
        var fixture=VentilationFixturePlan.firelockPlan();
        assertEquals(3,fixture.blocks().stream().filter(c->c.role()==VentilationFixturePlan.BlockRole.LIGHT).count());
        for (int x : new int[]{2,6,10}) assertEquals(VentilationFixturePlan.BlockRole.LIGHT,
                fixture.blocks().stream().filter(c->c.x()==x&&c.y()==3&&c.z()==4).findFirst().orElseThrow().role());
        assertEquals(46,fixture.firelockConnectedCells().size());
        assertEquals(0,VentilationFixturePlan.standard().blocks().stream().filter(c->c.role()==VentilationFixturePlan.BlockRole.LIGHT).count());
    }
    @Test void differentialScenariosUseClearlyDistinctTargets() {
        for(var scenario:java.util.List.of(VentilationFixturePlan.FirelockScenario.PRESSURE_DIFFERENTIAL,VentilationFixturePlan.FirelockScenario.TEMPERATURE_DIFFERENTIAL,VentilationFixturePlan.FirelockScenario.COMBINED_DIFFERENTIAL)) {
            var targets=VentilationFixturePlan.firelockDifferentialTargets(scenario);
            assertEquals(101.3,targets.leftPressureKPa()); assertEquals(293.15,targets.leftTemperatureKelvin());
            assertEquals(scenario==VentilationFixturePlan.FirelockScenario.TEMPERATURE_DIFFERENTIAL?101.3:450,targets.rightPressureKPa());
            assertEquals(scenario==VentilationFixturePlan.FirelockScenario.PRESSURE_DIFFERENTIAL?293.15:700,targets.rightTemperatureKelvin());
            assertEquals(scenario!=VentilationFixturePlan.FirelockScenario.TEMPERATURE_DIFFERENTIAL,Math.abs(targets.rightPressureKPa()-targets.leftPressureKPa())>1);
            assertEquals(scenario!=VentilationFixturePlan.FirelockScenario.PRESSURE_DIFFERENTIAL,Math.abs(targets.rightTemperatureKelvin()-targets.leftTemperatureKelvin())>1);
        }
    }
}
