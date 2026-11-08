package com.juicyslew.moonstation14.ms14.tesla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeslaBatteryStateTest {
    @Test void finiteChargeCapsAndSettlesOnlyActualKnownDelivery() {
        var battery=new TeslaBatteryState();
        assertTrue(battery.charge(5_000_000,5_000_000));
        assertFalse(battery.charge(5_000_000,5_000_000));
        assertEquals(0,battery.settle(350_000,false,20));
        assertEquals(0,battery.settle(0,true,20));
        assertEquals(350_000,battery.settle(350_000,true,20));
        assertEquals(4_650_000,battery.energyJoules());
        assertEquals(4_650_000,battery.settle(350_000,true,1000));
        assertEquals(0,battery.energyJoules());
    }
    @Test void rodsCanNeverStorePower() {
        var rod=new TeslaBatteryState();
        assertFalse(rod.charge(5_000_000,0));
        assertEquals(0,rod.settle(350_000,true,20));
        assertEquals(0,rod.energyJoules());
    }
    @Test void productionOfferIsEnergyBoundForEachSolveInterval() {
        var battery=new TeslaBatteryState(10_000);
        assertEquals(200_000,battery.offerWatts(350_000,1));
        assertEquals(10_000,battery.offerWatts(350_000,20));
        assertEquals(2_000,battery.offerWatts(350_000,100));
        assertEquals(2_000,battery.offerWatts(350_000,100)); // multiple observations reserve no energy
        assertEquals(10_000,battery.energyJoules());
        assertEquals(10_000,battery.settle(2_000,true,100));
        assertEquals(0,battery.offerWatts(350_000,20));
    }
    @Test void repeatedRestoreIsIdempotentAndClamped() {
        var battery=new TeslaBatteryState();
        battery.restore(4_000_000,5_000_000);
        battery.restore(4_000_000,5_000_000);
        assertEquals(4_000_000,battery.energyJoules());
        battery.restore(9_000_000,5_000_000);
        assertEquals(5_000_000,battery.energyJoules());
    }
}
