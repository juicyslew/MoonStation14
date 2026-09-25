package com.juicyslew.moonstation14.ms14.slip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftSlidingPhysicsTest {
    @Test
    void mapsNeutralZeroAndSmallFactorsAsSpecified() {
        assertEquals(.1d, MinecraftSlidingPhysics.acceleration(.1d, 1d), 0d);
        assertEquals(.546d, MinecraftSlidingPhysics.groundHorizontalRetention(.546d, 1d), 0d);
        assertEquals(0d, MinecraftSlidingPhysics.acceleration(.1d, 0d), 0d);
        assertEquals(1d, MinecraftSlidingPhysics.groundHorizontalRetention(.546d, 0d), 0d);
        assertEquals(.005d, MinecraftSlidingPhysics.acceleration(.1d, .05d), 1e-12);
        assertEquals(Math.pow(.546d, .05d), MinecraftSlidingPhysics.groundHorizontalRetention(.546d, .05d), 1e-12);
        assertEquals(.970d, MinecraftSlidingPhysics.groundHorizontalRetention(.546d, .05d), .001d);
        assertEquals(.2d, MinecraftSlidingPhysics.acceleration(.1d, 2d), 0d);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftSlidingPhysics.acceleration(1d, -1d));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSlidingPhysics.acceleration(1d, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSlidingPhysics.acceleration(1d, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSlidingPhysics.groundHorizontalRetention(.5d, -1d));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSlidingPhysics.groundHorizontalRetention(.5d, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MinecraftSlidingPhysics.groundHorizontalRetention(1.1d, 1d));
    }
}
