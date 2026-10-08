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
}
