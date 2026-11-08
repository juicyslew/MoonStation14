package com.juicyslew.moonstation14.ms14.atmos.machine.flow;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MachineFlowPolicyTest {
    @Test void pressurePumpTargetsPressureAndHasNoFlowCapOrGradientGate() {
        GasMixture inlet = new GasMixture(Map.of(GasType.OXYGEN, 1000d), 300);
        var plan = PressurePumpFlowPolicy.plan(inlet, GasMixture.vacuum(), 0.2, 0.2, 101.325, 4500).orElseThrow();
        assertTrue(plan.destinationAfter().pressureKpa(0.2) <= 101.325);
        assertTrue(plan.packet().totalMoles() > 0);
        assertTrue(PressurePumpFlowPolicy.plan(inlet, plan.destinationAfter(), 0.2, 0.2, 101.325, 4500).isEmpty());
    }

    @Test void pressurePumpMovesOnlyFiniteInletInventoryWhileVolumePumpKeepsItsRateFraction() {
        GasMixture inlet=new GasMixture(Map.of(GasType.NITROGEN,0.01d,GasType.CARBON_DIOXIDE,0.02d),564d,.2d);
        var pressure=PressurePumpFlowPolicy.plan(inlet,GasMixture.vacuum(),.2,.2,.05,4500).orElseThrow();
        assertTrue(pressure.packet().totalMoles()<=inlet.totalMoles());
        assertEquals(inlet.totalMoles(),pressure.packet().totalMoles()+pressure.sourceAfter().totalMoles(),1e-6);
        assertEquals(inlet.thermalEnergy(),pressure.packet().thermalEnergy()+pressure.sourceAfter().thermalEnergy(),1e-3);
        assertTrue(pressure.packet().moles(GasType.NITROGEN)>0&&pressure.packet().moles(GasType.CARBON_DIOXIDE)>0);
        var volume=VolumePumpFlowPolicy.plan(inlet,GasMixture.vacuum(),java.util.Optional.empty(),.2,.2,
                .2,1,.5,.01,9000,false,1000,.1).orElseThrow();
        assertEquals(.005d,volume.outletAfter().moles(GasType.NITROGEN),1e-6);
        assertEquals(.01d,volume.outletAfter().moles(GasType.CARBON_DIOXIDE),1e-6);
    }

    @Test void mixerRetainsRequestedRatioWhenOneInputLimitsAndConservesEnergy() {
        GasMixture a = new GasMixture(Map.of(GasType.OXYGEN, 0.01), 600);
        GasMixture b = new GasMixture(Map.of(GasType.NITROGEN, 100d), 200);
        GasMixture out = GasMixture.vacuum();
        var p = GasMixerFlowPolicy.plan(a, b, out, 0.2, 0.2, 0.2, 0.5, 101.325, 4500).orElseThrow();
        // The mixer ratio is a partial-pressure ratio: unequal inlet temperatures
        // require unequal mole amounts under the ideal gas law.
        assertEquals(p.inletOneMoles() * 600, p.inletTwoMoles() * 200, 1e-5);
        assertEquals(a.thermalEnergy()+b.thermalEnergy()+out.thermalEnergy(),
                p.inletOneAfter().thermalEnergy()+p.inletTwoAfter().thermalEnergy()+p.outletAfter().thermalEnergy(), 1e-3);
    }

    @Test void mixerSupportsSingleInletAndZeroRatio() {
        GasMixture a = GasMixture.vacuum();
        GasMixture b = new GasMixture(Map.of(GasType.NITROGEN, 10d), 300);
        var p = GasMixerFlowPolicy.plan(a, b, GasMixture.vacuum(), 0.2, 0.2, 0.2, 0, 101.325, 4500).orElseThrow();
        assertEquals(0, p.inletOneMoles());
        assertTrue(p.inletTwoMoles() > 0);
        assertTrue(GasMixerFlowPolicy.plan(a, b, GasMixture.vacuum(), 1).isEmpty());
    }
}
