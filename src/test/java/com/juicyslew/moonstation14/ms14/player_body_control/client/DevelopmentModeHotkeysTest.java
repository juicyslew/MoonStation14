package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DevelopmentModeHotkeysTest {
    @Test void sendsOnlyTheTwoExplicitCtrlShiftCommandsOutsideScreens() {
        assertEquals("ms14dev creative", DevelopmentModeHotkeys.commandForChord(true, false, true, false));
        assertEquals("ms14dev return", DevelopmentModeHotkeys.commandForChord(true, false, false, true));
        assertNull(DevelopmentModeHotkeys.commandForChord(false, false, true, false));
        assertNull(DevelopmentModeHotkeys.commandForChord(true, true, true, false));
        assertNull(DevelopmentModeHotkeys.commandForChord(true, false, false, false));
    }

    @Test void heldKeyRepeatsDoNotCreateAdditionalCommands() {
        assertTrue(DevelopmentModeHotkeys.pressedThisTick(true, false));
        assertFalse(DevelopmentModeHotkeys.pressedThisTick(true, true));
        assertFalse(DevelopmentModeHotkeys.pressedThisTick(false, true));
    }
}
