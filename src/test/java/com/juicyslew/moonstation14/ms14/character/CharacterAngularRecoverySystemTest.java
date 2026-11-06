package com.juicyslew.moonstation14.ms14.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterAngularRecoverySystemTest {
    @Test void supersedingOwnerClearsForCriticalDeathOrContainmentOnly() {
        assertFalse(CharacterAngularRecoverySystem.shouldClearSupersededState(true, false));
        assertTrue(CharacterAngularRecoverySystem.shouldClearSupersededState(false, false));
        assertTrue(CharacterAngularRecoverySystem.shouldClearSupersededState(true, true));
        assertTrue(CharacterAngularRecoverySystem.shouldClearSupersededState(false, true));
    }
}
