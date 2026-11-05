package com.juicyslew.moonstation14.ms14.eva.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvaFixturePlanTest {
    @Test void interiorIsBoundedAndBodyRelative() {
        var origin=new BlockPos(100,64,-20);var cells=EvaFixturePlan.interior(origin);
        assertEquals(7*4*7,cells.size());assertTrue(cells.contains(origin.offset(1,1,1)));
        assertTrue(cells.contains(origin.offset(7,4,7)));
        assertFalse(cells.contains(origin));assertFalse(cells.contains(origin.offset(8,2,2)));
        var movedOrigin=origin.offset(40,12,-31);
        assertEquals(cells.stream().map(p->p.offset(40,12,-31)).toList(),EvaFixturePlan.interior(movedOrigin));
    }
    @Test void roofLightsStayOnSealedRoofAndOutsideInterior() {
        var origin=new BlockPos(10,20,30);
        var lights=EvaFixturePlan.lightingPositions(origin);
        assertEquals(4,lights.size());
        assertTrue(lights.stream().allMatch(p->p.getY()==origin.getY()+EvaFixturePlan.HEIGHT-1));
        assertTrue(lights.stream().allMatch(p->!EvaFixturePlan.interior(origin).contains(p)));
        assertTrue(lights.stream().allMatch(p->EvaFixturePlan.isLightingPosition(origin,p)));
    }
    @Test void lowTankIsOneOutletBreathAtResolvedPhysicalTankVolume() {
        var profile=new com.juicyslew.moonstation14.ms14.atmos.tank.GasTankData(0.01,293.15,
                java.util.Map.of(GasType.OXYGEN,100d),
                com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.id("oxygen_tank"),21.3,30);
        var contents=EvaFixturePlan.lowOxygenContents(profile);
        assertEquals(profile.volumeCubicMeters(),contents.mixture().volumeCubicMeters(),1e-8);
        assertEquals(GasMixture.molesForPressureDelta(21.3,.0005,293.15),contents.mixture().totalMoles(),1e-8);
        assertEquals(0,contents.mixture().moles(GasType.NITROGEN));
    }
    @Test void finiteScenariosHaveAuthoredPressureAndTemperatureAndVacuumHasNoMoles() {
        double volume=1.25;
        var normal=EvaFixturePlan.mixture("normal",volume);
        assertEquals(101.325,normal.pressureKpa(),.01);assertEquals(293.15,normal.temperatureKelvin(),.01);
        var cold=EvaFixturePlan.mixture("cold",volume);assertEquals(100,cold.temperatureKelvin(),.01);assertEquals(101.325,cold.pressureKpa(),.02);
        var hot=EvaFixturePlan.mixture("hot",volume);assertEquals(600,hot.temperatureKelvin(),.01);assertEquals(101.325,hot.pressureKpa(),.02);
        var vacuum=EvaFixturePlan.mixture("vacuum",volume);assertEquals(0,vacuum.totalMoles());assertEquals(0,vacuum.pressureKpa());assertEquals(volume,vacuum.volumeCubicMeters());
        var contaminated=EvaFixturePlan.mixture("contaminated",volume);assertEquals(101.325,contaminated.pressureKpa(),.02);assertTrue(contaminated.moles(GasType.PLASMA)>0);
        assertThrows(IllegalArgumentException.class,()->EvaFixturePlan.mixture("normal",0));
        assertThrows(IllegalArgumentException.class,()->EvaFixturePlan.mixture("unknown",volume));
    }

    @Test void readyFixtureBuildSeedsKnownFiniteVacuumAfterEquipmentSetup() {
        var vacuum=EvaFixturePlan.mixture("vacuum",GasMixture.breathableAir().volumeCubicMeters());
        assertTrue(Double.isFinite(vacuum.volumeCubicMeters()));
        assertTrue(vacuum.volumeCubicMeters()>0);
        assertEquals(0,vacuum.totalMoles());
        assertEquals(0,vacuum.pressureKpa());
        assertEquals(9*6*9,EvaFixturePlan.WIDTH*EvaFixturePlan.HEIGHT*EvaFixturePlan.DEPTH);
    }
}
