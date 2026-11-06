package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClientOwnedSimulationClockTest {
    @Test void restoredAuthorityAndEachReplayIntentOwnConsecutiveSteps() {
        var clock = new ClientOwnedSimulationClock();
        clock.restore(7, 120, 40);
        assertEquals(new ClientOwnedSimulationClock.Interval(120, 121), clock.advance(7, 41));
        assertEquals(new ClientOwnedSimulationClock.Interval(121, 122), clock.advance(7, 43));
        assertNull(clock.advance(7, 43));
        assertNull(clock.advance(6, 44));
    }

    @Test void unanchoredClockDoesNotInventSdkTime() {
        var clock = new ClientOwnedSimulationClock();
        assertNull(clock.advance(1, 1));
        clock.restore(1, 0, 0);
        assertEquals(new ClientOwnedSimulationClock.Interval(0, 1), clock.advance(1, 1));
    }

    @Test void failedPreparedIntervalDoesNotAdvanceAndCanBeRetried() {
        var clock = new ClientOwnedSimulationClock();
        clock.restore(2, 9, 4);
        var proposed = clock.prepare(2, 5);
        assertEquals(new ClientOwnedSimulationClock.Interval(9, 10), proposed);
        assertEquals(9, clock.tick());
        assertTrue(clock.commit(proposed, 2, 5));
        assertFalse(clock.commit(proposed, 2, 6));
        assertEquals(10, clock.tick());
    }

    @Test void clearedPresentationClockCannotReuseSuspendedIntervalAndAcceptsFreshAuthorityAnchor() {
        var clock = new ClientOwnedSimulationClock();
        clock.restore(12, 70, 4);
        var stale = clock.prepare(12, 5);
        assertEquals(new ClientOwnedSimulationClock.Interval(70, 71), stale);
        clock.reset();
        assertFalse(clock.anchored());
        assertNull(clock.prepare(12, 6), "suspension must not leave the old published interval clock live");
        clock.restore(12, 100, 8);
        var resumed = clock.prepare(12, 9);
        assertEquals(new ClientOwnedSimulationClock.Interval(100, 101), resumed);
        assertTrue(clock.commit(resumed, 12, 9));
        assertEquals(101, clock.tick());
        assertFalse(clock.commit(stale, 12, 10), "a pre-suspension interval cannot commit after a fresh restore");
    }

    @Test void timelineIdentityIsSessionBoundButIndependentOfMedicalAndTrackerEligibility() {
        Object level = new Object();
        UUID body = UUID.randomUUID();
        assertTrue(OwnedSimulationTimelineIdentity.matches(true, 12, 12, MobHarnessKind.CHARACTER,
                41, body, level, 41, body, level));
        assertFalse(OwnedSimulationTimelineIdentity.matches(false, 12, 12, MobHarnessKind.CHARACTER,
                41, body, level, 41, body, level));
        assertFalse(OwnedSimulationTimelineIdentity.matches(true, 12, 13, MobHarnessKind.CHARACTER,
                41, body, level, 41, body, level));
        assertFalse(OwnedSimulationTimelineIdentity.matches(true, 12, 12, MobHarnessKind.CHARACTER,
                41, body, level, 42, body, level));
        assertFalse(OwnedSimulationTimelineIdentity.matches(true, 12, 12, MobHarnessKind.CHARACTER,
                41, body, level, 41, UUID.randomUUID(), level));
        assertFalse(OwnedSimulationTimelineIdentity.matches(true, 12, 12, MobHarnessKind.CHARACTER,
                41, body, level, 41, body, new Object()));
        assertFalse(OwnedSimulationTimelineIdentity.matches(true, 12, 12, MobHarnessKind.GHOST,
                41, body, level, 41, body, level));
    }
}
