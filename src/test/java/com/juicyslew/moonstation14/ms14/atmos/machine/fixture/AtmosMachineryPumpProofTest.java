package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineryPumpProofTest {
    @Test void preseededReceiverWithoutDirectionalPaidWorkIsNotAProof() {
        assertFalse(AtmosMachineryFixtureService.pumpTransferObserved(
                10,10,5,5, 100,100, 50,50, 0,0,true));
        assertFalse(AtmosMachineryFixtureService.pumpTransferObserved(
                10,9,5,6, 100,90, 50,60, 1,2,false));
        assertTrue(AtmosMachineryFixtureService.pumpTransferObserved(
                10,9,5,6, 100,90, 50,60, 1,2,true));
    }
    @Test void roomBoundaryProvesPaidTransferWithoutVacuumPortBaseline() {
        var room=new AtmosMachineryFixtureService.PumpRoomReadings(240,230,0,5,100,95,0,2);
        assertTrue(AtmosMachineryFixtureService.pumpTransferObserved(room,1,2,true));
        var unchanged=new AtmosMachineryFixtureService.PumpRoomReadings(240,240,0,0,100,100,0,0);
        assertFalse(AtmosMachineryFixtureService.pumpTransferObserved(unchanged,1,2,true));
        // The actual port's vacuum-to-filled transition is intentionally not the measured boundary.
        assertFalse(AtmosMachineryFixtureService.pumpTransferObserved(0,0,0,5,0,0,0,2,1,2,true));
    }
    @Test void thermalDecisionUsesRoomTemperatureAndCommittedThermalReceipt() {
        var freezer=new AtmosMachineryFixtureService.ThermalRoomProof(293.15,292.15);
        assertTrue(AtmosMachineryFixtureService.thermalRoomObserved(freezer,AtmosMachineryFixturePlan.Kind.FREEZER,8,-4));
        assertFalse(AtmosMachineryFixtureService.thermalRoomObserved(new AtmosMachineryFixtureService.ThermalRoomProof(293.15,293.15),AtmosMachineryFixturePlan.Kind.FREEZER,8,-4));
        assertFalse(AtmosMachineryFixtureService.thermalRoomObserved(new AtmosMachineryFixtureService.ThermalRoomProof(293.15,294.15),AtmosMachineryFixturePlan.Kind.FREEZER,8,-4));
    }
    @Test void valveFlowRequiresOpenAndDirectionalRoomPlasmaChange() {
        assertTrue(AtmosMachineryFixtureService.valveRoomFlowObserved(true,20,18,0,2));
        assertFalse(AtmosMachineryFixtureService.valveRoomFlowObserved(true,20,20,0,2));
        assertFalse(AtmosMachineryFixtureService.valveRoomFlowObserved(false,20,18,0,2));
        assertFalse(AtmosMachineryFixtureService.valveRoomFlowObserved(true,20,20,0,0));
    }
    @Test void exchangerRequiresOppositeMeasuredRoomAndPipeChangesWithMatchingReceipt() {
        var proof=new AtmosMachineryFixtureService.ExchangerRoomProof(350,349,1000,900);
        assertTrue(AtmosMachineryFixtureService.exchangerObserved(proof,500,600,100));
        assertFalse(AtmosMachineryFixtureService.exchangerObserved(proof,500,600,-100));
        assertFalse(AtmosMachineryFixtureService.exchangerObserved(proof,500,500,100));
        assertTrue(AtmosMachineryFixtureService.exchangerObserved(new AtmosMachineryFixtureService.ExchangerRoomProof(350,351,1000,1100),600,500,-100));
    }
}
