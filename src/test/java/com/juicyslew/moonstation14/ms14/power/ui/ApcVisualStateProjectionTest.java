package com.juicyslew.moonstation14.ms14.power.ui;

import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApcVisualStateProjectionTest {
    @Test void strictNinetyPercentBoundaryAndActualEnergyDirectionDriveDisplay() {
        double cap=PowerDeviceRules.APC_CAPACITY_JOULES;
        assertEquals(ApcVisualState.LACK,ApcVisualState.fromSolve(true,true,100,100,cap*.90,cap*.90));
        assertEquals(ApcVisualState.FULL,ApcVisualState.fromSolve(true,true,100,100,cap*.90,cap*.9001));
        assertEquals(ApcVisualState.CHARGING,ApcVisualState.fromSolve(true,true,100,1,cap*.5,cap*.5001));
        assertEquals(ApcVisualState.LACK,ApcVisualState.fromSolve(true,true,100,1,cap*.5,cap*.4999));
        assertEquals(ApcVisualState.LACK,ApcVisualState.fromSolve(true,true,100,100,cap*.5,cap*.5));
    }
}
