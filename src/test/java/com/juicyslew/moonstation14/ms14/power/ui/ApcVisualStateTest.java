package com.juicyslew.moonstation14.ms14.power.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApcVisualStateTest {
    @Test void fullIsStrictlyAboveNinetyPercent() {
        assertEquals(ApcVisualState.FULL, ApcVisualState.fromSolve(true, true, 0, 0, 900_001, 900_001));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, true, 0, 0, 900_000, 900_000));
    }

    @Test void chargingIncludesOpenBreakerWhenBatteryActuallyGainsEnergy() {
        assertEquals(ApcVisualState.CHARGING, ApcVisualState.fromSolve(true, true, 5000, 0, 500_000, 505_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, true, 5000, 0, 500_000, 500_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, false, 5000, 0, 500_000, 505_000));
    }

    @Test void balancedOperationIsNotLack() {
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, true, 100, 100, 500_000, 500_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, true, 100, 200, 500_000, 500_000));
    }

    @Test void lackRequiresMeasuredInputAndActualBatteryDrain() {
        assertEquals(ApcVisualState.LACK, ApcVisualState.fromSolve(true, true, 100, 200, 500_000, 499_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, true, 0, 100, 500_000, 499_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(false, true, 100, 200, 500_000, 499_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, false, 100, 200, 500_000, 499_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, true, Double.NaN, 200, 500_000, 499_000));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.fromSolve(true, true, 100, 200, 500_000, Double.NEGATIVE_INFINITY));
    }

    @Test void invalidWireWordsFailClosed() {
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.decode(-1));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.decode(0));
        assertEquals(ApcVisualState.FULL, ApcVisualState.decode(1));
        assertEquals(ApcVisualState.CHARGING, ApcVisualState.decode(2));
        assertEquals(ApcVisualState.LACK, ApcVisualState.decode(3));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.decode(4));
        assertEquals(ApcVisualState.UNKNOWN, ApcVisualState.decode(999));
    }
}
