package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.ms14.botany.ui.GrinderControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrinderWindowInputTest {
    @Test void paintedActionRowIsInsideWindowBoundsThroughItsBottomPixel() {
        assertTrue(GrinderWindow.containsBounds(78, 132, 0, 0));
        assertTrue(GrinderWindow.containsBounds(78, 140, 0, 0));
        assertFalse(GrinderWindow.containsBounds(78, 148, 0, 0));
    }

    @Test void registeredWindowActionRouteDispatchesEjectAllWhenIdleEvenWithoutPower() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        // Same production hit route used by GrinderWindow.mouseClicked; these are window-relative painted bounds.
        assertTrue(GrinderWindow.routeActionClick(68 + 10, 32 + 95, 0, 0,
                false, false, dispatched::add));
        assertEquals(java.util.List.of(GrinderControl.Operation.EJECT_ALL), dispatched);
    }

    @Test void busyWindowConsumesEjectAllClickWithoutDispatch() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        assertTrue(GrinderWindow.routeActionClick(78, 127, 0, 0,
                true, false, dispatched::add));
        assertTrue(dispatched.isEmpty());
    }
}
