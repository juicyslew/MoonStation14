package com.juicyslew.moonstation14.gametest.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class GameTestProgressStateTest {
    @Test void identityAdmissionCapAndOverflowAreExplicit() {
        GameTestProgressState<Object, String> state = new GameTestProgressState<>(1);
        Object first = new String("same"), equal = new String("same");
        assertEquals(GameTestProgressState.Admission.NEW, state.admit(first, "one", 4, 100));
        assertEquals(GameTestProgressState.Admission.DUPLICATE, state.admit(first, "ignored", 4, 100));
        assertEquals(GameTestProgressState.Admission.OVERFLOW, state.admit(equal, "two", 4, 100));
        assertEquals(1, state.activeSize());
        assertEquals(1, state.counts().overflow());
        assertTrue(state.markOverflowReported());
        assertFalse(state.markOverflowReported());
    }

    @Test void completionIsIdempotentAndFreesAdmissionWithoutInventingOutcomes() {
        GameTestProgressState<Object, String> state = new GameTestProgressState<>(1);
        Object key = new Object();
        state.admit(key, "first", 0, 0);
        assertEquals("first", state.complete(key, GameTestProgressState.Outcome.FAIL));
        assertNull(state.complete(key, GameTestProgressState.Outcome.PASS));
        assertEquals(GameTestProgressState.Admission.NEW, state.admit(new Object(), "next", 0, 0));
        assertEquals(2, state.counts().started());
        assertEquals(1, state.counts().completed());
        assertEquals(1, state.counts().failed());
    }

    @Test void eitherHeartbeatClockCanTriggerButEmptyStateNeverDoes() {
        GameTestProgressState<Object, String> ticks = new GameTestProgressState<>(2);
        assertFalse(ticks.heartbeatDue(0, 0));
        ticks.admit(new Object(), "x", 10, 10);
        assertFalse(ticks.heartbeatDue(209, 10));
        assertTrue(ticks.heartbeatDue(210, 10));
        assertFalse(ticks.heartbeatDue(300, 10));
        assertTrue(ticks.heartbeatDue(300, 30_000_000_010L));

        GameTestProgressState<Object, String> wall = new GameTestProgressState<>(1);
        wall.admit(new Object(), "x", 1, 5);
        assertFalse(wall.heartbeatDue(2, 5 + GameTestProgressState.HEARTBEAT_NANOS - 1));
        assertTrue(wall.heartbeatDue(2, 5 + GameTestProgressState.HEARTBEAT_NANOS));
        wall.complete(wall.snapshot().get(0).getKey(), GameTestProgressState.Outcome.OTHER);
        assertFalse(wall.heartbeatDue(1000, 100_000_000_000L));
    }

    @Test void delayedNewCohortStartsItsOwnHeartbeatWindow() {
        GameTestProgressState<Object, String> state = new GameTestProgressState<>(2);
        Object old = new Object();
        state.admit(old, "old", 0, 0);
        assertEquals("old", state.complete(old, GameTestProgressState.Outcome.PASS));
        assertFalse(state.heartbeatDue(900, 90_000_000_000L)); // Empty polling does not anchor the next cohort.
        Object next = new Object();
        assertEquals(GameTestProgressState.Admission.NEW, state.admit(next, "next", 1000, 100_000_000_000L));
        assertFalse(state.heartbeatDue(1199, 129_999_999_999L));
        assertTrue(state.heartbeatDue(1200, 130_000_000_000L));
    }

    @Test void snapshotsAreImmutableAndShutdownClearsStateAndClock() {
        GameTestProgressState<Object, String> state = new GameTestProgressState<>(2);
        Object key = new Object();
        state.admit(key, "value", 3, 7);
        state.admit(new Object(), "second", 3, 7);
        assertEquals(GameTestProgressState.Admission.OVERFLOW, state.admit(new Object(), "overflow", 3, 7));
        assertEquals(new GameTestProgressState.Counts(2, 0, 0, 1), state.counts());
        var snapshot = state.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertTrue(snapshot.stream().anyMatch(entry -> "value".equals(entry.getValue())));
        assertTrue(state.markOverflowReported());
        assertFalse(state.markOverflowReported());
        state.clear();
        assertEquals(0, state.activeSize());
        assertEquals(new GameTestProgressState.Counts(0, 0, 0, 0), state.counts());
        assertFalse(state.heartbeatDue(300, 30_000_000_000L));
        assertTrue(state.markOverflowReported());
        assertFalse(state.markOverflowReported());
        assertTrue(new ArrayList<>(state.snapshot()).isEmpty());
    }

    @Test void diagnosticStringsBoundLengthAndStripLineBreaks() {
        assertEquals("a  b", GameTestProgressObserver.bounded("a\r\nb", 20));
        String clipped = GameTestProgressObserver.bounded("x".repeat(300), 256);
        assertEquals(256, clipped.length());
        assertFalse(clipped.contains("\n"));
        assertEquals("", GameTestProgressObserver.bounded(null, 256));
    }
}
