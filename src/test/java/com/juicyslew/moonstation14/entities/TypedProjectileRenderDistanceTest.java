package com.juicyslew.moonstation14.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedProjectileRenderDistanceTest {
    private static boolean visible(double distance, int chunks, double scale) {
        return TypedProjectileEntity.withinTrackingRenderDistance(distance * distance, chunks, scale);
    }

    @Test
    void usesConfiguredTrackingRangeInsteadOfSmallCollisionBounds() {
        for (double distance : new double[]{6.5, 20.0, 64.0, 127.9})
            assertTrue(visible(distance, 8, 1.0), "expected visibility at " + distance);
        assertFalse(visible(128.0, 8, 1.0));
        assertFalse(visible(128.1, 8, 1.0));
    }

    @Test
    void viewScaleAdjustsPresentationRange() {
        assertTrue(visible(63.9, 8, 0.5));
        assertFalse(visible(64.0, 8, 0.5));
        assertTrue(visible(255.9, 8, 2.0));
        assertFalse(visible(256.0, 8, 2.0));
    }

    @Test
    void rejectsInvalidInputs() {
        assertFalse(TypedProjectileEntity.withinTrackingRenderDistance(-1.0, 8, 1.0));
        assertFalse(TypedProjectileEntity.withinTrackingRenderDistance(Double.NaN, 8, 1.0));
        assertFalse(TypedProjectileEntity.withinTrackingRenderDistance(Double.POSITIVE_INFINITY, 8, 1.0));
        assertFalse(TypedProjectileEntity.withinTrackingRenderDistance(1.0, -1, 1.0));
        assertFalse(TypedProjectileEntity.withinTrackingRenderDistance(1.0, 8, Double.NaN));
        assertFalse(TypedProjectileEntity.withinTrackingRenderDistance(1.0, 8, Double.POSITIVE_INFINITY));
        assertFalse(TypedProjectileEntity.withinTrackingRenderDistance(1.0, 8, 0.0));
    }
}
