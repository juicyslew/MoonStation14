package com.juicyslew.moonstation14.ms14.atmos.machine.flow;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VolumeAndFilterFlowPolicyTest {
    private static GasMixture gas(GasType type, double moles, double temperature, double volume) {
        return new GasMixture(Map.of(type, moles), temperature, volume);
    }

    @Test void volumePumpTransfersRequestedFractionAndEnforcesOrdinaryThresholds() {
        GasMixture inlet = gas(GasType.OXYGEN, 100, 300, .2);
        GasMixture outlet = GasMixture.vacuum();
        var result = VolumePumpFlowPolicy.plan(inlet, outlet, Optional.empty(), .2, .2,
                .2, 1, .5, .01, 9000, false, 1000, .1).orElseThrow();
        assertEquals(50, result.movedByGas().get(GasType.OXYGEN), 1e-4);
        assertEquals(50, result.inletAfter().moles(GasType.OXYGEN), 1e-4);
        assertEquals(50, result.outletAfter().moles(GasType.OXYGEN), 1e-4);
        assertEquals(50 * inlet.heatCapacity() / 100 * 300, result.energyMoved(), 1e-2);
        assertEquals(inlet.thermalEnergy() + outlet.thermalEnergy(),
                result.inletAfter().thermalEnergy() + result.outletAfter().thermalEnergy(), 1e-2);
        assertTrue(VolumePumpFlowPolicy.plan(inlet, gas(GasType.NITROGEN, 1000, 300, .2), Optional.empty(),
                .2,.2,.2,1,.1,.01,9000,false,1000,.1).isEmpty());
    }

    @Test void overclockLeaksOnlyWhenTileMixtureExistsAndHonorsPressureDifferenceGate() {
        GasMixture inlet = gas(GasType.OXYGEN, 100, 300, .2);
        GasMixture environment = GasMixture.vacuum();
        var result = VolumePumpFlowPolicy.plan(inlet, GasMixture.vacuum(), Optional.of(environment),
                .2,.2,.2,1,.5,.01,9000,true,1000,.1).orElseThrow();
        assertEquals(45, result.outletAfter().moles(GasType.OXYGEN), 1e-4);
        assertEquals(5, result.environmentAfter().orElseThrow().moles(GasType.OXYGEN), 1e-4);
        assertEquals(50, result.movedByGas().get(GasType.OXYGEN), 1e-4);
        assertEquals(50 * inlet.heatCapacity() / 100 * 300, result.energyMoved(), 1e-2);
        assertEquals(inlet.thermalEnergy() + environment.thermalEnergy(), result.inletAfter().thermalEnergy()
                + result.outletAfter().thermalEnergy() + result.environmentAfter().orElseThrow().thermalEnergy(), 1e-2);
        assertTrue(VolumePumpFlowPolicy.plan(inlet, gas(GasType.NITROGEN, 200, 300, .2), Optional.empty(),
                .2,.2,.2,1,.1,.01,9000,true,1000,.1).isEmpty());
    }

    @Test void filterSeparatesSelectedSpeciesAndRespectsIndependentOutletCaps() {
        GasMixture inlet = new GasMixture(Map.of(GasType.OXYGEN, 80d, GasType.NITROGEN, 20d), 300, .2);
        GasMixture filter = GasMixture.vacuum();
        GasMixture outlet = GasMixture.vacuum();
        var result = GasFilterFlowPolicy.plan(inlet, filter, outlet, Optional.of(GasType.OXYGEN),
                .2,.2,.2,1,.1,1,4500).orElseThrow();
        assertEquals(40, result.filteredByGas().get(GasType.OXYGEN), 1e-4);
        assertEquals(10, result.passedByGas().get(GasType.NITROGEN), 1e-4);
        assertEquals(40, result.inletAfter().moles(GasType.OXYGEN), 1e-4);
        assertEquals(10, result.inletAfter().moles(GasType.NITROGEN), 1e-4);
        assertEquals(40, result.filterAfter().moles(GasType.OXYGEN), 1e-4);
        assertEquals(10, result.outletAfter().moles(GasType.NITROGEN), 1e-4);
        assertEquals(inlet.thermalEnergy(), result.inletAfter().thermalEnergy()
                + result.filterAfter().thermalEnergy() + result.outletAfter().thermalEnergy(), 1e-2);
    }

    @Test void filterCanPassWhenSelectedBranchIsFullAndNoSelectionPassesAllGases() {
        GasMixture inlet = new GasMixture(Map.of(GasType.OXYGEN, 10d, GasType.NITROGEN, 10d), 300, .2);
        GasMixture fullFilter = gas(GasType.OXYGEN, 1000, 300, .2);
        var partial = GasFilterFlowPolicy.plan(inlet, fullFilter, GasMixture.vacuum(), Optional.of(GasType.OXYGEN),
                .2,.2,.2,1,1,1,4500).orElseThrow();
        assertTrue(partial.filteredByGas().isEmpty());
        assertTrue(partial.passedByGas().get(GasType.NITROGEN) > 0);
        assertEquals(10, partial.inletAfter().moles(GasType.OXYGEN), 1e-4);
        assertEquals(0, partial.outletAfter().moles(GasType.OXYGEN), 1e-4);
        var passthrough = GasFilterFlowPolicy.plan(inlet, GasMixture.vacuum(), GasMixture.vacuum(), Optional.empty(),
                .2,.2,.2,1,1,.5,4500).orElseThrow();
        assertTrue(passthrough.filteredByGas().isEmpty());
        assertEquals(20, passthrough.outletAfter().totalMoles(), 1e-4);
        assertEquals(1.0, GasFilterFlowPolicy.SS14_DEFAULT_TRANSFER_RATE_M3_PER_SECOND, 0);
    }

    @Test void recombinedPumpAndFilterSourceRetainFractionalContainerVolumesAndInventoryAcceptsThem() {
        double inletVolume=(double)(float).37,outletVolume=(double)(float).13,filterVolume=(double)(float).29;
        GasMixture inlet= new GasMixture(Map.of(GasType.OXYGEN,20d,GasType.NITROGEN,10d),310,inletVolume);
        GasMixture outlet=new GasMixture(Map.of(GasType.PLASMA,1d),300,outletVolume);
        var pump=VolumePumpFlowPolicy.plan(inlet,outlet,Optional.empty(),inletVolume,outletVolume,
                inletVolume,1,.25,0,10000,false,0,0).orElseThrow();
        assertEquals(inletVolume,pump.inletAfter().volumeCubicMeters(),0);
        assertEquals(outletVolume,pump.outletAfter().volumeCubicMeters(),0);
        assertEquals(inlet.totalMoles()+outlet.totalMoles(),pump.inletAfter().totalMoles()+pump.outletAfter().totalMoles(),1e-5);
        assertEquals(inlet.thermalEnergy()+outlet.thermalEnergy(),pump.inletAfter().thermalEnergy()+pump.outletAfter().thermalEnergy(),1e-2);
        assertAcceptedByInventory(pump.inletAfter());assertAcceptedByInventory(pump.outletAfter());

        GasMixture filterIn=new GasMixture(Map.of(GasType.OXYGEN,80d,GasType.NITROGEN,20d),300,inletVolume);
        GasMixture filterTank=new GasMixture(Map.of(),GasMixture.MIN_TEMPERATURE_KELVIN,filterVolume);
        GasMixture filterOut=new GasMixture(Map.of(),GasMixture.MIN_TEMPERATURE_KELVIN,outletVolume);
        var filtered=GasFilterFlowPolicy.plan(filterIn,filterTank,filterOut,Optional.of(GasType.OXYGEN),
                inletVolume,filterVolume,outletVolume,inletVolume,1,.25,10000).orElseThrow();
        assertEquals(inletVolume,filtered.inletAfter().volumeCubicMeters(),0);
        assertEquals(filterVolume,filtered.filterAfter().volumeCubicMeters(),0);
        assertEquals(outletVolume,filtered.outletAfter().volumeCubicMeters(),0);
        assertEquals(filterIn.totalMoles(),filtered.inletAfter().totalMoles()+filtered.filterAfter().totalMoles()+filtered.outletAfter().totalMoles(),1e-5);
        assertEquals(filterIn.thermalEnergy(),filtered.inletAfter().thermalEnergy()+filtered.filterAfter().thermalEnergy()+filtered.outletAfter().thermalEnergy(),1e-2);
        assertAcceptedByInventory(filtered.inletAfter());assertAcceptedByInventory(filtered.filterAfter());assertAcceptedByInventory(filtered.outletAfter());
    }

    private static void assertAcceptedByInventory(GasMixture proposed) {
        var key=new PipeTopology.NodeKey(new net.minecraft.core.BlockPos(0,0,0),PipeTopology.Layer.PRIMARY);
        var id=UUID.randomUUID();
        PipeGasInventory inventory=new PipeGasInventory().admit(key,id,proposed.volumeCubicMeters());
        var accepted=inventory.withNetworkMixtures(Map.of(key,proposed));
        var network=accepted.network(key).orElseThrow();
        assertEquals(proposed.volumeCubicMeters(),network.mixture().volumeCubicMeters(),0);
        assertEquals(proposed.gasMoles(),network.mixture().gasMoles());
        assertEquals(proposed.thermalEnergy(),network.mixture().thermalEnergy(),1e-2);
    }
}
