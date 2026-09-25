package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementStartupGateTest {
    @AfterEach
    void stopServer() {
        MovementStartupGate.onServerStopped();
    }

    @Test
    void isClosedBeforeStartupAndAfterStop() {
        assertFalse(MovementStartupGate.enabledForServer());

        MovementStartupGate.onServerStarting(true);
        assertTrue(MovementStartupGate.enabledForServer());

        MovementStartupGate.onServerStopped();
        assertFalse(MovementStartupGate.enabledForServer());
    }

    @Test
    void latchesTheStartupValueInsteadOfFollowingLaterConfigChanges() {
        MovementStartupGate.onServerStarting(false);
        assertFalse(MovementStartupGate.enabledForServer());

        MovementStartupGate.onServerStarting(true);
        assertTrue(MovementStartupGate.enabledForServer());
    }
}
