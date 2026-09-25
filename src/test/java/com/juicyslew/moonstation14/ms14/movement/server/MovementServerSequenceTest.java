package com.juicyslew.moonstation14.ms14.movement.server;

import com.juicyslew.moonstation14.ms14.movement.protocol.MovementSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementServerSequenceTest {
    @Test
    void acceptsSequentialCommandsBeforeOneMotorTickAndRejectsGapsAndStaleInput() {
        MovementSession session = new MovementSession();
        assertEquals(MovementSession.Result.ACCEPTED, session.begin(7, true, true, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.acknowledge(7, true));
        assertEquals(MovementSession.Result.ACCEPTED, session.commit(7, true));

        // Simulate two network deliveries in one server tick: the latest command can replace
        // the one-slot pending wish while both sequence numbers advance monotonically.
        assertTrue(MovementServerController.acceptNextSequence(session, 7, 1));
        assertTrue(MovementServerController.acceptNextSequence(session, 7, 2));
        assertEquals(2, session.lastSequence());
        assertFalse(MovementServerController.acceptNextSequence(session, 7, 2));
        assertFalse(MovementServerController.acceptNextSequence(session, 7, 4));
        assertEquals(2, session.lastSequence());
    }
}
