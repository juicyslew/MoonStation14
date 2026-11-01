package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeCaptureFocusPolicyTest {
    @Test void rejectsUnavailableFocusWithoutRecovery() {
        assertEquals("user-focus-unavailable", MeleeCaptureFocusPolicy.reason(false, false, true));
        assertEquals("menu-open", MeleeCaptureFocusPolicy.reason(true, true, true));
        assertEquals("user-focus-released", MeleeCaptureFocusPolicy.reason(true, false, false));
        assertTrue(MeleeCaptureFocusPolicy.permits(true, false, true));
        assertFalse(MeleeCaptureFocusPolicy.permits(false, false, true));
    }
    @Test void lostFocusIsTerminalUnlessUserRestoresItExplicitly() {
        assertFalse(MeleeCaptureFocusPolicy.permits(true, false, false));
        assertFalse(MeleeCaptureFocusPolicy.permits(false, false, false));
    }
}
