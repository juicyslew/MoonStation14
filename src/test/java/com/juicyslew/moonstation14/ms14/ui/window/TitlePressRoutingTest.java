package com.juicyslew.moonstation14.ms14.ui.window;

import com.juicyslew.moonstation14.ms14.ui.client.window.TitlePressRouting;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TitlePressRoutingTest {
    private static boolean press(double x, double y, int button, boolean widgetHit) {
        return TitlePressRouting.isTitlePress(x, y, button, 20, 30, 350,
                WindowGeometryController.TITLE_HEIGHT, 400, 240, widgetHit);
    }

    @Test
    void emptyTitleRoutesBeforeContainerButCloseAndOtherWidgetsDoNot() {
        assertTrue(press(40, 40, 0, false));
        assertFalse(press(350, 40, 0, true)); // close button
        assertFalse(press(40, 40, 0, true)); // a fitted/overlapping control
        assertFalse(press(40, 90, 0, false)); // body
        assertFalse(press(40, 40, 1, false)); // secondary button
        assertFalse(press(370, 40, 0, false)); // outside artwork
        assertFalse(press(40, 57, 0, false)); // just below title
    }

    @Test
    void clippedArtworkDoesNotCaptureOutsideViewportOrVisibleTitle() {
        assertTrue(TitlePressRouting.isTitlePress(5, 5, 0, -75, -20, 350, 27, 200, 100, false));
        assertFalse(TitlePressRouting.isTitlePress(5, 10, 0, -75, -20, 350, 27, 200, 100, false));
        assertFalse(TitlePressRouting.isTitlePress(-1, 5, 0, -75, -20, 350, 27, 200, 100, false));
        assertFalse(TitlePressRouting.isTitlePress(200, 5, 0, -75, -20, 350, 27, 200, 100, false));
        assertFalse(press(Double.NaN, 40, 0, false));
    }

    @Test
    void routedPressCapturesControllerDragAndReleaseWithoutResize() {
        WindowGeometryController geometry = new WindowGeometryController(400, 240, 20, 30,
                350, 190, 350, 190, false);
        assertTrue(press(40, 40, 0, false));
        assertTrue(geometry.beginPointer(40, 40, 0));
        assertTrue(geometry.dragPointer(60, 55));
        assertEquals(40, geometry.x());
        assertEquals(45, geometry.y());
        assertEquals(350, geometry.width());
        assertEquals(190, geometry.height());
        assertTrue(geometry.releasePointer(0));
        assertFalse(geometry.dragPointer(80, 80));
    }
}
