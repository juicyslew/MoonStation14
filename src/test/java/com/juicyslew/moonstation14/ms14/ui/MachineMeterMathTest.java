package com.juicyslew.moonstation14.ms14.ui;

import com.juicyslew.moonstation14.ms14.ui.client.MachineMeterMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineMeterMathTest {
    @Test
    void clampsBarAndRoundsPercent() {
        assertEquals(0, MachineMeterMath.fillWidth(80, -20));
        assertEquals(40, MachineMeterMath.fillWidth(80, 500));
        assertEquals(80, MachineMeterMath.fillWidth(80, 1500));
        assertEquals(0, MachineMeterMath.fillWidth(80, Float.NaN));
        assertEquals(0, MachineMeterMath.fillWidth(-3, 1000));
        assertEquals(0, MachineMeterMath.percent(-1));
        assertEquals(50, MachineMeterMath.percent(495));
        assertEquals(100, MachineMeterMath.percent(2000));
    }

    @Test
    void gradientEndpointsAndMidpoint() {
        assertEquals(0xffc34736, MachineMeterMath.chargeColor(-100));
        assertEquals(0xffde9b3d, MachineMeterMath.chargeColor(500));
        assertEquals(0xff58b879, MachineMeterMath.chargeColor(1000));
        assertEquals(0xff58b879, MachineMeterMath.chargeColor(1100));
    }
}
