package com.juicyslew.moonstation14.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrowerClearanceTest {
    @Test void spawnOverlapAndClearanceSweepAreSuppressedThenReturnIsAllowed() {
        var state = new ThrowerClearance();
        assertTrue(state.suppress(true)); // spawn overlap
        assertTrue(state.suppress(true)); // continued overlap / forward sweep
        assertTrue(state.suppress(false)); // clearance sweep itself cannot self-hit
        assertTrue(state.cleared());
        assertFalse(state.suppress(true)); // physical return after clearance
    }

    @Test void continuousOverlapCannotBecomeARepeatContact() {
        var state = new ThrowerClearance();
        for (int i = 0; i < 100; i++) assertTrue(state.suppress(true));
        assertFalse(state.cleared());
    }

    @Test void eachNewThrowResetsClearance() {
        var state = new ThrowerClearance();
        assertTrue(state.suppress(false));
        assertFalse(state.suppress(true));
        state.reset();
        assertTrue(state.suppress(true));
        assertFalse(state.cleared());
    }

}
