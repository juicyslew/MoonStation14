package com.juicyslew.moonstation14.ms14.effect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementSpeedCompatibilityTest {
    @Test
    void onlyEqualPairsAreAdmittedAndUnequalShapesWarnOnce() {
        assertTrue(MovementSpeedCompatibility.supports(.65f, .65f));
        assertTrue(MovementSpeedCompatibility.supports(0f, -0f));
        assertFalse(MovementSpeedCompatibility.supports(.65f, .8f));

        List<String> warnings = new ArrayList<>();
        MovementSpeedCompatibility.resetWarnings();
        MovementSpeedCompatibility.setWarningSink(warnings::add);
        try {
            MovementSpeedCompatibility.warnUnequalOnce(.65f, .8f);
            MovementSpeedCompatibility.warnUnequalOnce(.65f, .8f);
            MovementSpeedCompatibility.warnUnequalOnce(-0f, .8f);
            MovementSpeedCompatibility.warnUnequalOnce(0f, .8f);
            assertEquals(2, warnings.size());
        } finally {
            MovementSpeedCompatibility.resetWarnings();
            MovementSpeedCompatibility.resetWarningSink();
        }
    }
}
