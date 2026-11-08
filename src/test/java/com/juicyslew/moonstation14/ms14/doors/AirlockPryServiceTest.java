package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirlockPryServiceTest {
    @Test void pryRequiresStableOpenOrClosedPhaseThroughoutTimedInteraction() {
        assertTrue(DoorPrySupport.stablePhase(AirlockState.DoorPhase.OPEN));
        assertTrue(DoorPrySupport.stablePhase(AirlockState.DoorPhase.CLOSED));
        assertFalse(DoorPrySupport.stablePhase(AirlockState.DoorPhase.OPENING));
        assertFalse(DoorPrySupport.stablePhase(AirlockState.DoorPhase.CLOSING));
        assertFalse(DoorPrySupport.stablePhase(AirlockState.DoorPhase.OPENING_PASSABLE));
        assertFalse(DoorPrySupport.stablePhase(AirlockState.DoorPhase.CLOSING_PASSABLE));
    }
}
