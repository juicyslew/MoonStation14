package com.juicyslew.moonstation14.ms14.doors.ui.client;

import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirlockWireLightStyleTest {
    @Test void onIsClearlyBrighterRedThanOffAndBlinkHasDistinctPhases() {
        assertTrue(AirlockWireLightStyle.isOn(AirlockWireIndicators.Light.ON, 0));
        assertFalse(AirlockWireLightStyle.isOn(AirlockWireIndicators.Light.OFF, 0));
        assertTrue(AirlockWireLightStyle.isOn(AirlockWireIndicators.Light.BLINK_SLOW, 0));
        assertFalse(AirlockWireLightStyle.isOn(AirlockWireIndicators.Light.BLINK_SLOW, 10));
        assertTrue(((AirlockWireLightStyle.ON >> 16) & 255) > ((AirlockWireLightStyle.OFF >> 16) & 255));
        assertTrue(((AirlockWireLightStyle.ON >> 8) & 255) < 64);
        assertTrue(((AirlockWireLightStyle.OFF >> 8) & 255) < 64);
    }

    @Test void onCoreIsLargerAndNeitherStateUsesAWhiteBezel() {
        assertTrue(AirlockWireLightStyle.diameter(true) > AirlockWireLightStyle.diameter(false));
        assertEquals(8, AirlockWireLightStyle.diameter(true));
        assertEquals(4, AirlockWireLightStyle.diameter(false));
        assertEquals(0xff, (AirlockWireLightStyle.ON >>> 24) & 0xff);
        assertTrue(((AirlockWireLightStyle.ON >> 16) & 255) > ((AirlockWireLightStyle.ON >> 8) & 255));
    }
}
