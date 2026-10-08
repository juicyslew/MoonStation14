package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlledSoundDispatchTest {
    @Test void selectsByActiveListenerPositionRatherThanVanillaCarrierPosition() {
        // The carrier is parked 100 blocks away; its controlled body's camera is at the sound.
        assertTrue(ControlledSoundDispatch.withinRange(0, 0, 0, 1, 0, 0));
        assertFalse(ControlledSoundDispatch.withinRange(0, 0, 0, 100, 0, 0));
        assertFalse(ControlledSoundDispatch.withinRange(0, 0, 0, Double.NaN, 0, 0));
    }
}
