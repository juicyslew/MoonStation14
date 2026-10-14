package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NutritionMovementFactorTest {
    @Test
    void multipliesIndependentStatusAndPreservesOtherMovementFactors() {
        assertFactor(1d, NutritionMovementFactor.apply(1d, false, false));
        assertFactor(.75d, NutritionMovementFactor.apply(1d, true, false));
        assertFactor(.75d, NutritionMovementFactor.apply(1d, false, true));
        assertFactor(.5625d, NutritionMovementFactor.apply(1d, true, true));
        assertFactor(.6d, NutritionMovementFactor.apply(.8d, true, false));
        assertFactor(.45d, NutritionMovementFactor.apply(.8d, true, true));
        assertFactor(1d, NutritionMovementFactor.apply(1d, false, false)); // recovered
    }

    private static void assertFactor(double expected, double actual) {
        assertEquals(expected, actual, 1e-12);
    }
}
