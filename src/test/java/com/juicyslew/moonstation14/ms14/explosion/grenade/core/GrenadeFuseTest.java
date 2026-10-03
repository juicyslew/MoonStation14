package com.juicyslew.moonstation14.ms14.explosion.grenade.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeFuseTest {
    @Test void primeHoldThrowAndExpiry() {
        GrenadeFuse armed = GrenadeFuse.safe().prime(10);
        assertEquals(70, armed.deadline());
        assertEquals(60, armed.remaining(10));
        assertEquals(30, armed.remaining(40)); // carrying/transferring has no timing operation
        assertEquals(GrenadeFuse.Phase.SPENT, armed.evaluate(70).state().phase());
        assertTrue(armed.evaluate(70).expired());
        assertFalse(armed.evaluate(70).state().evaluate(70).expired());
    }

    @Test void beepsAtPrimeAndFortyTicksOnce() {
        GrenadeFuse armed = GrenadeFuse.safe().prime(5);
        assertTrue(armed.evaluate(5).beepDue());
        assertFalse(armed.evaluate(5).state().evaluate(5).beepDue());
        GrenadeFuse afterPrime = armed.evaluate(5).state();
        assertFalse(afterPrime.evaluate(44).beepDue());
        GrenadeFuse afterForty = afterPrime.evaluate(45).state();
        assertFalse(afterForty.evaluate(45).beepDue());
        assertFalse(armed.evaluate(64).expired());
        assertTrue(armed.evaluate(65).expired());
    }

    @Test void rejectRetryAndBadInputs() {
        assertThrows(IllegalStateException.class, () -> GrenadeFuse.safe().prime(0).prime(1));
        assertThrows(IllegalArgumentException.class, () -> GrenadeFuse.safe().prime(-1));
        assertThrows(IllegalArgumentException.class, () -> GrenadeFuse.safe().prime(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> GrenadeFuse.restore(null, -1, false, false));
        assertThrows(IllegalArgumentException.class, () -> GrenadeFuse.restore(GrenadeFuse.Phase.ARMED, 0, false, false));
        assertThrows(IllegalArgumentException.class, () -> GrenadeFuse.safe().evaluate(-1));
    }

    @Test void absoluteDeadlineSurvivesRestart() {
        GrenadeFuse armed = GrenadeFuse.safe().prime(100);
        GrenadeFuse reloaded = GrenadeFuse.restore(armed.phase(), armed.deadline(), true, false);
        assertEquals(30, reloaded.remaining(130));
        assertTrue(reloaded.evaluate(140).beepDue());
        assertTrue(reloaded.evaluate(160).expired());
    }

    @Test void expiryDoesNotReplayMissedBeepBacklog() {
        GrenadeFuse armed = GrenadeFuse.safe().prime(0);
        GrenadeFuse.Evaluation late = armed.evaluate(60);
        assertTrue(late.expired());
        assertFalse(late.beepDue());
        assertFalse(late.state().evaluate(60).expired());
        assertFalse(late.state().evaluate(60).beepDue());
    }

    @Test void milestonesAreExposedAsImmutableState() {
        GrenadeFuse atPrime = GrenadeFuse.safe().prime(0).evaluate(0).state();
        assertTrue(atPrime.beepedAtPrime());
        assertFalse(atPrime.beepedAtForty());
        GrenadeFuse atForty = atPrime.evaluate(40).state();
        assertTrue(atForty.beepedAtPrime());
        assertTrue(atForty.beepedAtForty());
    }
}
