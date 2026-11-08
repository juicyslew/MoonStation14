package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointerModePolicyTest {
    @Test void lastAnalyzerCloseExitsManualOrAutomaticPointerOnlyWhenScreenIsOwnedAndNoWindowsRemain() {
        for (var origin : PointerModePolicy.Origin.values()) {
            var policy=new PointerModePolicy();
            if(origin==PointerModePolicy.Origin.MANUAL) policy.manualOpen();
            if(origin==PointerModePolicy.Origin.POPUP_AUTOMATIC) policy.popupOpen();
            assertFalse(policy.lastAnalyzerClosed(true,true));
            assertEquals(origin,policy.origin());
            assertFalse(policy.lastAnalyzerClosed(false,false));
            assertEquals(origin,policy.origin());
            assertEquals(origin!=PointerModePolicy.Origin.CLOSED,policy.lastAnalyzerClosed(false,true));
            assertEquals(PointerModePolicy.Origin.CLOSED,policy.origin());
        }
    }
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

    @Test void committedGhostOwnerIsContextBoundAndManualIntentNeedsNoWindow() {
        var policy = new PointerModePolicy();
        policy.manualOpen();
        assertTrue(policy.open());
        assertFalse(policy.closeWhenEmpty());
        assertTrue(PointerModePolicy.shouldRestoreHost(policy.open(), true, true));
        assertFalse(PointerModePolicy.shouldRestoreHost(policy.open(), true, false));
        // Authority replacement is a hard barrier, not a reopen-on-next-tick condition.
        policy.authorityClear();
        assertFalse(policy.open());
        assertFalse(PointerModePolicy.shouldRestoreHost(policy.open(), true, true));
    }

    @Test void openingSharedWorkspaceCreatesAutomaticIntentButPreservesManualIntent() {
        var automatic = new PointerModePolicy();
        automatic.workspaceOpen(); // production openSharedPointerWorkspace policy path
        assertEquals(PointerModePolicy.Origin.POPUP_AUTOMATIC, automatic.origin());
        assertTrue(automatic.open());
        var ghost = java.util.UUID.randomUUID();
        assertTrue(PointerModePolicy.preserveForGhostContext(
                new com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads.Context(ghost, 12),
                new com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads.Context(ghost, 12)));
        assertTrue(PointerModePolicy.keepOpenForWindow(false, true)); // visible overlay prevents empty cleanup
        assertTrue(automatic.closeWhenEmpty()); // cleanup after the final window closes clears automatic intent
        assertFalse(automatic.closeWhenEmpty()); // cleanup is idempotent after automatic intent is cleared
        assertFalse(automatic.open());

        var manual = new PointerModePolicy();
        manual.manualOpen();
        manual.workspaceOpen();
        assertEquals(PointerModePolicy.Origin.MANUAL, manual.origin());
        assertFalse(manual.closeWhenEmpty()); // L-Alt/manual intent survives window closure
        assertTrue(manual.open());
    }
}
