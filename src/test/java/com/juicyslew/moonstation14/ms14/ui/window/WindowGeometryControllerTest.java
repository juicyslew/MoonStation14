package com.juicyslew.moonstation14.ms14.ui.window;

import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowGeometryControllerTest {
    private static WindowGeometryController window(boolean resizable) {
        return new WindowGeometryController(320, 240, 40, 30, 180, 120, 100, 70, resizable);
    }

    @Test
    void dragIsBoundedOnEverySideAndUsesPressAsAnchor() {
        WindowGeometryController window = window(false);
        assertTrue(window.beginPointer(50, 40, 0));
        assertTrue(window.dragPointer(-1000, -1000));
        assertEquals(0, window.x());
        assertEquals(0, window.y());
        assertTrue(window.dragPointer(1000, 1000));
        assertEquals(140, window.x());
        assertEquals(120, window.y());
        assertEquals(180, window.width());
        assertTrue(window.releasePointer(0));
        assertFalse(window.dragPointer(0, 0));
    }

    @Test
    void titleButtonsBodyAndOtherMouseButtonsDoNotCapture() {
        WindowGeometryController window = window(true);
        assertFalse(window.beginPointer(50, 40, 1));
        assertFalse(window.beginPointer(215, 35, 0));
        assertFalse(window.beginPointer(60, 80, 0));
        assertFalse(window.beginPointer(220, 40, 0));
        assertTrue(window.beginPointer(50, 40, 0));
        assertFalse(window.beginPointer(50, 40, 0));
        assertFalse(window.releasePointer(1));
        assertTrue(window.isPointerCaptured());
        assertTrue(window.releasePointer(0));
    }

    @Test
    void resizeIsOptInAndClampedToMinAndViewport() {
        WindowGeometryController fixed = window(false);
        assertFalse(fixed.beginPointer(218, 148, 0));
        WindowGeometryController window = window(true);
        assertTrue(window.beginPointer(218, 148, 0));
        assertTrue(window.dragPointer(-1000, -1000));
        assertEquals(100, window.width());
        assertEquals(70, window.height());
        assertTrue(window.dragPointer(1000, 1000));
        assertEquals(280, window.width());
        assertEquals(210, window.height());
        assertEquals(40, window.x());
        assertEquals(30, window.y());
        assertTrue(window.releasePointer(0));
    }

    @Test
    void resizeAtScreenEdgeMayShrinkBelowMinButNeverEscapeViewport() {
        WindowGeometryController window = new WindowGeometryController(120, 90, 100, 75,
                80, 70, 80, 70, true);
        assertEquals(40, window.x());
        assertEquals(20, window.y());
        window.setBounds(110, 80, 1, 1);
        assertEquals(40, window.x());
        assertEquals(20, window.y());
        window.setScreenSize(50, 40);
        assertEquals(50, window.width());
        assertEquals(40, window.height());
        assertEquals(0, window.x());
        assertEquals(0, window.y());
    }

    @Test
    void relayoutCancelsCaptureAndInvalidCoordinatesCannotMoveWindow() {
        WindowGeometryController window = window(true);
        assertFalse(window.beginPointer(Double.NaN, 40, 0));
        assertTrue(window.beginPointer(50, 40, 0));
        assertTrue(window.dragPointer(Double.POSITIVE_INFINITY, 100));
        assertEquals(40, window.x());
        assertEquals(30, window.y());
        window.setScreenSize(150, 100);
        assertFalse(window.isPointerCaptured());
        assertFalse(window.dragPointer(100, 100));
        assertEquals(150, window.width());
        assertEquals(100, window.height());
        assertEquals(0, window.x());
        assertEquals(0, window.y());
    }

    @Test
    void rejectsInvalidViewportAndMinimum() {
        assertThrows(IllegalArgumentException.class, () -> new WindowGeometryController(0, 20, 0, 0, 10, 10, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new WindowGeometryController(20, 20, 0, 0, 10, 10, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> window(false).setScreenSize(20, 0));
    }
}
