package com.juicyslew.moonstation14.ms14.movement.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MovementVisualFacingMathTest {
    @Test
    void mapsCardinalAndDiagonalWishToMinecraftYaw() {
        assertEquals(0d, MovementVisualFacingMath.yawDegrees((short) 0, (short) 1000).orElseThrow(), 1e-9);
        assertEquals(-90d, MovementVisualFacingMath.yawDegrees((short) 1000, (short) 0).orElseThrow(), 1e-9);
        assertEquals(90d, MovementVisualFacingMath.yawDegrees((short) -1000, (short) 0).orElseThrow(), 1e-9);
        assertEquals(-45d, MovementVisualFacingMath.yawDegrees((short) 1000, (short) 1000).orElseThrow(), 1e-9);
        assertEquals(180d, MovementVisualFacingMath.yawDegrees((short) 0, (short) -1000).orElseThrow(), 1e-9);
    }

    @Test
    void zeroQuantizedWishDoesNotProvideANewFacing() {
        assertFalse(MovementVisualFacingMath.yawDegrees((short) 0, (short) 0).isPresent());
    }
}
