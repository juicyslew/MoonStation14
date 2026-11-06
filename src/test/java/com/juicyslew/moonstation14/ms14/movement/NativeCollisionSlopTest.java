package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NativeCollisionSlopTest {
    @Test
    void normalizesOnlyNativeFloatScaleAxisResidue() {
        assertEquals(0, NativeCollisionSlop.acceptedAxis(0, 2.11019511198e-7));
        assertEquals(0, NativeCollisionSlop.acceptedAxis(.01, -2.11019511198e-7));
        assertEquals(.01, NativeCollisionSlop.acceptedAxis(.01, .0100005));
        assertEquals(-.01, NativeCollisionSlop.acceptedAxis(-.01, -.0100005));
        assertEquals(.01001, NativeCollisionSlop.acceptedAxis(.01, .01001));
        assertEquals(-.001, NativeCollisionSlop.acceptedAxis(.01, -.001), "unsafe wrong-sign movement is not hidden");
        assertEquals(.6, NativeCollisionSlop.acceptedAxis(-.01, .6), "valid bounded upward step remains intact");
        assertThrows(IllegalArgumentException.class,
                () -> NativeCollisionSlop.accepted(new MovementVector(0, 0, 0),
                        new MovementVector(Double.NaN, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> NativeCollisionSlop.accepted(new MovementVector(0, 0, 0),
                        new MovementVector(Double.POSITIVE_INFINITY, 0, 0)));
    }
}
