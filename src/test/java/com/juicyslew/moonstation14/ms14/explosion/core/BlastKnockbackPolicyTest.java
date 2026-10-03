package com.juicyslew.moonstation14.ms14.explosion.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlastKnockbackPolicyTest {
    @Test
    void launchesOutwardHorizontallyAndIsIndependentOfDistance() {
        var east = BlastKnockbackPolicy.calculate(1, 0, 0, 0, 1, 0, 0);
        var farEast = BlastKnockbackPolicy.calculate(1, 0, 0, 0, 100, 0, 0);
        var north = BlastKnockbackPolicy.calculate(1, 0, 0, 0, 0, 0, -1);
        assertTrue(east.applicable());
        assertEquals(1.0, east.velocity().x(), 1e-12);
        assertEquals(0.0, east.velocity().y(), 1e-12);
        assertEquals(east.velocity(), farEast.velocity());
        assertEquals(-1.0, north.velocity().z(), 1e-12);
    }

    @Test
    void appliesSquareRootScalingThenCapsSpeed() {
        var low = BlastKnockbackPolicy.calculate(0.25, 0, 0, 0, 1, 0, 0).velocity();
        var quarter = BlastKnockbackPolicy.calculate(1, 0, 0, 0, 1, 0, 0).velocity();
        var capped = BlastKnockbackPolicy.calculate(10, 0, 0, 0, 1, 0, 0).velocity();
        assertEquals(2 * low.x(), quarter.x(), 1e-12);
        assertEquals(BlastKnockbackPolicy.MAX_SPEED, capped.x(), 1e-12);
    }

    @Test
    void includesVerticalOutwardDirectionAndCoincidentFallbackIsDeterministic() {
        var up = BlastKnockbackPolicy.calculate(1, 0, 0, 0, 0, 2, 0);
        assertEquals(1, up.velocity().y(), 1e-12);
        var first = BlastKnockbackPolicy.calculate(1, 2, 3, 4, 2, 3, 4);
        var second = BlastKnockbackPolicy.calculate(1, 2, 3, 4, 2, 3, 4);
        assertEquals(first, second);
        assertEquals(new BlastKnockbackPolicy.Velocity(1, 0, 0), first.velocity());
    }

    @Test
    void rejectsInvalidInputsAndZeroIntensityWithoutEffect() {
        for (double intensity : new double[]{-1, 10.0001, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(BlastKnockbackPolicy.calculate(intensity, 0, 0, 0, 1, 0, 0).applicable());
        assertFalse(BlastKnockbackPolicy.calculate(0, 0, 0, 0, 1, 0, 0).applicable());
        assertFalse(BlastKnockbackPolicy.calculate(1, 0, Double.NaN, 0, 1, 0, 0).applicable());
        assertFalse(BlastKnockbackPolicy.calculate(1, 0, 0, 0, 1, Double.NEGATIVE_INFINITY, 0).applicable());
        var invalid = BlastKnockbackPolicy.calculate(1, 0, 0, 0, Double.NaN, 0, 0);
        assertEquals(new BlastKnockbackPolicy.Velocity(0, 0, 0), invalid.velocity());
        assertTrue(Double.isFinite(invalid.velocity().x()));
    }

    @Test
    void rejectsFiniteCoordinatesWhoseDifferenceOverflows() {
        var result = BlastKnockbackPolicy.calculate(1,
                -Double.MAX_VALUE, 0, 0, Double.MAX_VALUE, 0, 0);

        assertFalse(result.applicable());
        assertEquals(new BlastKnockbackPolicy.Velocity(0, 0, 0), result.velocity());
    }

    @Test
    void calculationDoesNotMutateItsInputs() {
        double[] origin = {1, 2, 3};
        double[] target = {4, 5, 6};
        double[] originalOrigin = origin.clone(), originalTarget = target.clone();
        BlastKnockbackPolicy.calculate(2, origin[0], origin[1], origin[2], target[0], target[1], target[2]);
        assertArrayEquals(originalOrigin, origin);
        assertArrayEquals(originalTarget, target);
    }
}
