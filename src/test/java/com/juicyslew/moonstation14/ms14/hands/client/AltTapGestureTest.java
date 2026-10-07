package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AltTapGestureTest {
    @Test void togglesOnlyOnUnusedAltRelease() {
        var gesture = new AltTapGesture();
        gesture.pressAlt();
        assertTrue(gesture.releaseAlt());
        gesture.pressAlt();
        gesture.otherPress();
        assertFalse(gesture.releaseAlt());
        gesture.pressAlt();
        assertTrue(gesture.releaseAlt());
        assertFalse(gesture.releaseAlt());
    }
}
