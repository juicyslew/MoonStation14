package com.juicyslew.moonstation14.ms14.player_body_control.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedSpectatorGuardTest {
    @Test
    void committedOwnerMayWatchOnlyExactlyOwnedBody() {
        assertTrue(CommittedSpectatorGuard.rejectTeleport(true));
        assertFalse(CommittedSpectatorGuard.rejectForeignCamera(true, true));
        assertTrue(CommittedSpectatorGuard.rejectForeignCamera(true, false));
    }

    @Test
    void normalSpectatorAndUncommittedSessionKeepVanillaCameraControls() {
        assertFalse(CommittedSpectatorGuard.rejectTeleport(false));
        assertFalse(CommittedSpectatorGuard.rejectForeignCamera(false, true));
        assertFalse(CommittedSpectatorGuard.rejectForeignCamera(false, false));
        assertFalse(CommittedSpectatorGuard.blockTeleport(null));
        assertFalse(CommittedSpectatorGuard.blockCamera(null, null));
    }

    @Test
    void committedSessionWithoutSafeSnapshotDeniesEvenNullCameraTarget() {
        var absent = CommittedSpectatorGuard.Ownership.absent();
        var unavailable = CommittedSpectatorGuard.Ownership.unavailable();
        assertTrue(CommittedSpectatorGuard.rejectTeleport(unavailable.committed()));
        assertTrue(CommittedSpectatorGuard.rejectCamera(new CommittedSpectatorGuard.Ownership[] {
                absent, unavailable, absent
        }, null));
        assertTrue(CommittedSpectatorGuard.rejectCamera(new CommittedSpectatorGuard.Ownership[] {
                unavailable, unavailable, absent
        }, null));
        assertFalse(CommittedSpectatorGuard.rejectCamera(new CommittedSpectatorGuard.Ownership[] {
                absent, absent, absent
        }, null));
    }

    @Test
    void diagnosticThrottleAllowsFirstAttemptAndOnePerIntervalWithClockRewind() {
        assertTrue(CommittedSpectatorGuard.logDue(100, null));
        assertFalse(CommittedSpectatorGuard.logDue(100, 100L));
        assertFalse(CommittedSpectatorGuard.logDue(199, 100L));
        assertTrue(CommittedSpectatorGuard.logDue(200, 100L));
        assertTrue(CommittedSpectatorGuard.logDue(1, 200L));
    }
}
