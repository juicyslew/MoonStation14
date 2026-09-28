package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleCharacterDeathHandlerTest {
    @Test
    void confirmedCallbackRequiresEveryGateAndExactServerBody() {
        assertTrue(eligible(true, false, true, true, true, true));
        assertFalse(eligible(false, false, true, true, true, true));
        assertFalse(eligible(true, true, true, true, true, true));
        assertFalse(eligible(true, false, false, true, true, true)); // canceled vanilla death
        assertFalse(eligible(true, false, true, false, true, true));
        assertFalse(eligible(true, false, true, true, false, true));
        assertFalse(eligible(true, false, true, true, true, false));
    }

    private static boolean eligible(boolean enabled, boolean conflict, boolean confirmed,
                                    boolean serverThread, boolean exact, boolean dead) {
        return LifecycleCharacterDeathHandler.callbackEligible(enabled, conflict, confirmed,
                serverThread, exact, dead);
    }
}
