package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.ms14.botany.ui.GrinderControl;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrinderWindowInputTest {
    @Test void paintedActionRowIsInsideWindowBoundsThroughItsBottomPixel() {
        assertTrue(GrinderWindow.containsBounds(78, 132, 0, 0));
        assertTrue(GrinderWindow.containsBounds(78, 162, 0, 0));
        assertFalse(GrinderWindow.containsBounds(78, 170, 0, 0));
    }

    @Test void registeredWindowActionRouteDispatchesEjectAllWhenIdleEvenWithoutPower() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        // Same production hit route used by GrinderWindow.mouseClicked; these are window-relative painted bounds.
        assertTrue(GrinderWindow.routeActionClick(10, 32 + 119, 0, 0,
                false, false, false, dispatched::add));
        assertEquals(java.util.List.of(GrinderControl.Operation.EJECT_ALL), dispatched);
    }

    @Test void busyWindowConsumesEjectAllClickWithoutDispatch() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        assertTrue(GrinderWindow.routeActionClick(78, 127, 0, 0,
                true, false, false, dispatched::add));
        assertTrue(dispatched.isEmpty());
    }

    @Test void juiceActionDispatchesOnlyWhenAuthoritativeSnapshotEnablesIt() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        assertTrue(GrinderWindow.routeActionClick(78, 32 + 95, 0, 0, false, true, true, dispatched::add));
        assertEquals(java.util.List.of(GrinderControl.Operation.JUICE), dispatched);
        dispatched.clear();
        assertTrue(GrinderWindow.routeActionClick(78, 32 + 95, 0, 0, false, true, false, dispatched::add));
        assertTrue(dispatched.isEmpty());
        assertFalse(GrinderWindow.routeActionClick(78, 32 + 110, 0, 0, false, true, true, dispatched::add));
    }

    @Test void grindActionRemainsSeparateAndBusyActionsNeverDispatch() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        assertTrue(GrinderWindow.routeActionClick(10, 32 + 95, 0, 0, false, true, false, dispatched::add));
        assertEquals(java.util.List.of(GrinderControl.Operation.GRIND), dispatched);
        dispatched.clear();
        assertTrue(GrinderWindow.routeActionClick(78, 32 + 95, 0, 0, true, false, true, dispatched::add));
        assertTrue(dispatched.isEmpty());
    }

    @Test void eachPaintedActionRectangleIsExactlyItsExclusiveHitRectangle() {
        var buttons = java.util.List.of(GrinderControl.Operation.GRIND, GrinderControl.Operation.JUICE,
                GrinderControl.Operation.EJECT_ALL, GrinderControl.Operation.EJECT_BEAKER);
        for (var operation : buttons) {
            var bounds = GrinderWindow.actionBounds(operation, 20, 30);
            int cx = bounds.left() + bounds.width() / 2, cy = bounds.top() + bounds.height() / 2;
            var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
            assertTrue(WindowChromeButtons.contains(bounds, cx, cy), operation + " center is painted inside semantic bounds");
            assertTrue(GrinderWindow.routeActionClick(cx, cy, 20, 30, false, true, true, dispatched::add));
            assertEquals(java.util.List.of(operation), dispatched, operation + " center dispatches matching control");

            dispatched.clear();
            assertFalse(GrinderWindow.routeActionClick(bounds.left() + bounds.width(), cy, 20, 30,
                    false, true, true, dispatched::add), operation + " right edge is exclusive");
            assertFalse(GrinderWindow.routeActionClick(cx, bounds.top() + bounds.height(), 20, 30,
                    false, true, true, dispatched::add), operation + " bottom edge is exclusive");
            assertTrue(dispatched.isEmpty());
        }
    }

    @Test void actionGapsAndAreaBelowWindowDoNotDispatch() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        assertFalse(GrinderWindow.routeActionClick(20 + 65, 30 + 130, 20, 30, false, true, true, dispatched::add));
        assertFalse(GrinderWindow.routeActionClick(20 + 20, 30 + 165, 20, 30, false, true, true, dispatched::add));
        assertTrue(dispatched.isEmpty());
        assertFalse(WindowChromeButtons.contains(GrinderWindow.actionBounds(GrinderControl.Operation.JUICE, 20, 30),
                20 + 65, 30 + 130));
    }

    @Test void disabledJuiceAndBusyModeClicksDoNotFallbackToGrind() {
        var dispatched = new java.util.ArrayList<GrinderControl.Operation>();
        var juice = GrinderWindow.actionBounds(GrinderControl.Operation.JUICE, 0, 0);
        int cx = juice.left() + juice.width() / 2, cy = juice.top() + juice.height() / 2;
        assertTrue(GrinderWindow.routeActionClick(cx, cy, 0, 0, false, true, false, dispatched::add));
        assertTrue(dispatched.isEmpty());
        assertTrue(GrinderWindow.routeActionClick(cx, cy, 0, 0, true, true, true, dispatched::add));
        assertTrue(dispatched.isEmpty());
    }
}
