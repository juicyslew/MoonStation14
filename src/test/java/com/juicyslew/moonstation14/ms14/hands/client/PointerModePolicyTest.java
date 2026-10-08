package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointerModePolicyTest {
    @Test void manualCursorPersistsAcrossEmptyWindowsAndPeriodicRefreshes() {
        var policy = new PointerModePolicy();
        policy.manualOpen();
        for (int tick : new int[]{0, 40, 80}) {
            assertTrue(policy.open());
            assertFalse(policy.closeWhenEmpty());
            if (tick == 40) policy.manualOpen(); // another periodic state refresh
        }
        assertEquals(PointerModePolicy.Origin.MANUAL, policy.origin());
    }

    @Test void automaticPopupCursorClosesOnLastWindowAndExplicitDismissalAlwaysWins() {
        var policy = new PointerModePolicy();
        policy.popupOpen();
        assertTrue(policy.closeWhenEmpty());
        assertFalse(policy.open());
        policy.manualOpen();
        policy.dismiss();
        assertFalse(policy.open());
        policy.manualOpen(); // L-Alt toggling back on is a new intent
        assertTrue(policy.open());
        policy.authorityClear();
        assertFalse(policy.open());
    }

    @Test void wireAndStorageWindowKeepSharedPointerButWireClosureReleasesOnlyAutomaticIntent() {
        var policy = new PointerModePolicy();
        policy.popupOpen();
        assertTrue(PointerModePolicy.keepOpenForWindow(true, false));
        assertTrue(PointerModePolicy.keepOpenForWindow(false, true));
        assertFalse(PointerModePolicy.keepOpenForWindow(false, false));
        assertTrue(policy.closeWhenEmpty()); // after wire and storage windows both close

        policy.manualOpen();
        assertFalse(policy.closeWhenEmpty()); // X/server close never erases manual intent
        assertTrue(policy.open());
    }

    @Test void sharedHostReturnsAfterChatScreenReleasesToGameplayOnlyWhenPointerStillOpen() {
        assertTrue(PointerModePolicy.shouldRestoreHost(true, true, true));
        assertFalse(PointerModePolicy.shouldRestoreHost(false, true, true));
        assertFalse(PointerModePolicy.shouldRestoreHost(true, false, true));
        assertFalse(PointerModePolicy.shouldRestoreHost(true, true, false));
    }
}
