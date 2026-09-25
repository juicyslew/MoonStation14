package com.juicyslew.moonstation14.ms14.movement.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementServerHandshakeTimeoutTest {
    @Test
    void pendingEndTimeoutUsesBoundedBeginAllowance() {
        assertFalse(MovementServerController.isPendingEndTimedOut(100, 199));
        assertTrue(MovementServerController.isPendingEndTimedOut(100, 200));
        assertTrue(MovementServerController.isPendingEndTimedOut(100, 201));
    }
}
