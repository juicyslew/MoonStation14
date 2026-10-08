package com.juicyslew.moonstation14.ms14.medical.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NeutralPortraitMathTest {
    @Test void directInventoryTransformCancelsVanillaVerticalModelFlip() {
        var up = NeutralPortrait.uprightPose().transform(new org.joml.Vector3f(0, 1, 0));
        assertEquals(-1.0f, up.y, 1.0e-5f);
    }
}
