package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import org.joml.Vector3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnedStepProbePositionTest {
    @Test
    void ownedStepProbeStartsAtCurrentAccumulatedCollisionPosition() {
        Vector3d center = SableOwnedStepProbe.offsetCandidate(new Vector3d(2, 3, 4),
                new Vector3d(-.2722214, -.0001, -.07069));
        assertEquals(1.7277786, center.x, 1e-12);
        assertEquals(2.9999, center.y, 1e-12);
        assertEquals(3.92931, center.z, 1e-12);
    }

    @Test
    void verticalStepIncrementRemainsTheSdkSuppliedCandidateOffset() {
        Vector3d center = SableOwnedStepProbe.offsetCandidate(new Vector3d(2, 3, 4),
                new Vector3d(-.2, 0, -.1));
        center.y += 1d / 16d;
        assertEquals(3.0625, center.y, 1e-12);
    }
}
