package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VentReleaseDecisionTest {
    @Test
    void waitsForReleaseAbovePrecisionBudget() {
        assertFalse(VentilationFixtureGameTests.VentReleaseDecision.shouldComplete(4.532933235168457E-5,
                4.532933235168457E-5, 0.0010433711649966426));
        assertTrue(VentilationFixtureGameTests.VentReleaseDecision.shouldComplete(0.01, 0.01, 0.001));
    }

    @Test
    void rejectsMissingOrNonconservingTransfer() {
        assertFalse(VentilationFixtureGameTests.VentReleaseDecision.shouldComplete(0.01, 0, 0.001));
        assertFalse(VentilationFixtureGameTests.VentReleaseDecision.shouldComplete(0.01, 0.02, 0.001));
    }
}
