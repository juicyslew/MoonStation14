package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VentilationFixtureCommandResultTest {
    @Test void firelockActionsRequireTheirOwnPositiveSuccessResults() {
        String[] success = {"Built finite ventilation fixture", "Initialized connected firelock", "Reset using trusted operator",
                "Applied pressure_warning", "Fixture pressure: UNKNOWN", "Moved existing controlled body",
                "Fixture APC turned on", "Fixture APC already on"};
        for (int action = 0; action < success.length; action++)
            assertTrue(VentilationFixtureCommands.successfulResult(action, success[action]), "action " + action);
        String[] refusal = {"Partial fixture", "Initialization guard admitted setup, but gas setup rejected",
                "No saved fixture marker", "Enter refused", "No fixture status", "APC breaker transition rejected"};
        for (String result : refusal)
            for (int action = 0; action < 8; action++)
                assertFalse(VentilationFixtureCommands.successfulResult(action, result), "action " + action + ": " + result);
        assertFalse(VentilationFixtureCommands.successfulResult(8, "Fixture informational"));
    }

    @Test void legacyCoordinateStatusStillUsesLegacyActionMeaning() {
        assertTrue(VentilationFixtureCommands.legacySuccessfulResultForTest(2, "Fixture status: OK"));
    }
}
