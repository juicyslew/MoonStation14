package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyActionMenuWindowTest {
    @Test
    void sharedHalfScaleGeometryKeepsPaintedBoundsAndPointerRoutingAligned() {
        assertEquals(14, BodyActionLayout.toScreenSize(28));
        assertEquals(15, BodyActionLayout.toScreenSize(30));
        var layout = BodyActionLayout.calculate((int)BodyActionLayout.screenToLogical(640),
                (int)BodyActionLayout.screenToLogical(360), 24, 0);
        assertEquals(95, BodyActionLayout.toScreenSize(layout.width()));
        assertEquals(137, BodyActionLayout.toScreenSize(layout.height()));
        double rowX = (layout.left() + 10) * BodyActionLayout.ACTION_UI_SCALE;
        double rowY = (layout.top() + BodyActionLayout.HEADER_HEIGHT + 4) * BodyActionLayout.ACTION_UI_SCALE;
        assertEquals(0, layout.rowAt(BodyActionLayout.screenToLogical(rowX),
                BodyActionLayout.screenToLogical(rowY), 24));
        assertEquals(-1, layout.rowAt(BodyActionLayout.screenToLogical((layout.left() + 10) * .5),
                BodyActionLayout.screenToLogical((layout.top() + 28) * .5), 24));
        var close = BodyActionLayout.screenBounds(layout.left() + layout.width() - WindowChromeButtons.EDGE - WindowChromeButtons.SIZE,
                layout.top() + WindowChromeButtons.EDGE, WindowChromeButtons.SIZE, WindowChromeButtons.SIZE);
        assertEquals(9, close.width());
        assertEquals(9, close.height());
        assertTrue(WindowChromeButtons.contains(close, close.left(), close.top()));
        var movable = new WindowGeometryController(1280, 720, 400, 200, 190, 150, 80, 65, false);
        assertTrue(movable.beginPointer(BodyActionLayout.screenToLogical(210),
                BodyActionLayout.screenToLogical(105), 0, WindowGeometryController.TITLE_HEIGHT,
                WindowGeometryController.TITLE_BUTTON_SPACE));
        assertTrue(movable.dragPointer(BodyActionLayout.screenToLogical(240),
                BodyActionLayout.screenToLogical(125)));
        assertEquals(460, movable.x());
        assertEquals(240, movable.y());
    }

    @Test
    void halfScaleLayoutFitsNarrowViewportAndKeepsRowsReachable() {
        int logicalWidth = (int)Math.ceil(BodyActionLayout.screenToLogical(90));
        int logicalHeight = (int)Math.ceil(BodyActionLayout.screenToLogical(80));
        var layout = BodyActionLayout.calculate(logicalWidth, logicalHeight, 5, 99);
        assertTrue(BodyActionLayout.toScreenSize(layout.width()) <= 90);
        assertTrue(BodyActionLayout.toScreenSize(layout.height()) <= 80);
        assertEquals(5 - layout.visibleRows(), layout.scrollOffset());
        assertEquals(layout.scrollOffset(), layout.rowAt(layout.left() + 1,
                layout.top() + BodyActionLayout.HEADER_HEIGHT + 1, 5));
    }

    @Test
    void tinyViewportsClampPanelChromeAndExposeNoPhantomRows() {
        for (int physicalHeight : new int[]{1, 8, 16, 24, 34}) {
            for (int physicalWidth : new int[]{1, 8, 16, 24, 34}) {
                int logicalWidth = (int)Math.ceil(BodyActionLayout.screenToLogical(physicalWidth));
                int logicalHeight = (int)Math.ceil(BodyActionLayout.screenToLogical(physicalHeight));
                var layout = BodyActionLayout.calculate(logicalWidth, logicalHeight, 5, 0);
                int screenLeft = BodyActionLayout.logicalToScreen(layout.left());
                int screenTop = BodyActionLayout.logicalToScreen(layout.top());
                int screenRight = screenLeft + BodyActionLayout.toScreenSize(layout.width());
                int screenBottom = screenTop + BodyActionLayout.toScreenSize(layout.height());
                assertTrue(screenLeft >= 0 && screenTop >= 0);
                assertTrue(screenRight <= physicalWidth, "width=" + physicalWidth + " height=" + physicalHeight);
                assertTrue(screenBottom <= physicalHeight, "width=" + physicalWidth + " height=" + physicalHeight);
                if (physicalHeight <= 34) assertEquals(0, layout.visibleRows());
                assertEquals(-1, layout.rowAt(layout.left() + .5, layout.top() + .5, 5));

                var chrome = BodyActionLayout.closeBounds(layout.width(), layout.height());
                assertTrue(chrome.left() >= 0 && chrome.top() >= 0);
                assertTrue(chrome.left() + chrome.width() <= layout.width());
                assertTrue(chrome.top() + chrome.height() <= layout.height());
                var controller = new WindowGeometryController(logicalWidth, logicalHeight,
                        layout.left(), layout.top(), layout.width(), layout.height(),
                        Math.min(80, Math.max(1, logicalWidth)), Math.min(65, Math.max(1, logicalHeight)), false);
                assertFalse(controller.beginPointer(-1, -1, 0));
            }
        }
    }

    @Test
    void menuRemainsCompactAndScrollableOnLargeViewports() {
        var first = BodyActionLayout.calculate(1920, 1080, 24, 0);
        var last = BodyActionLayout.calculate(1920, 1080, 24, 17);
        assertTrue(first.height() < 400);
        assertEquals(7, first.visibleRows());
        assertEquals(17, last.scrollOffset());
        assertTrue(last.rowAt(last.left() + 5, last.top() + BodyActionLayout.HEADER_HEIGHT, 24) >= 17);
    }

    @Test
    void menuChromeSupportsDragWithoutCapturingCloseButtonAndCloseBoundsAreRouted() {
        var window = new WindowGeometryController(640, 360, 220, 110, 190, 150, 80, 65, false);
        var close = WindowChromeButtons.closeBounds(window.x() + window.width() - WindowChromeButtons.EDGE
                        - WindowChromeButtons.SIZE, window.y() + WindowChromeButtons.EDGE,
                WindowChromeButtons.SIZE, WindowChromeButtons.SIZE);

        assertTrue(WindowChromeButtons.contains(close, close.left() + 1, close.top() + 1));
        assertFalse(window.beginPointer(close.left() + 1, close.top() + 1, 0,
                WindowGeometryController.TITLE_HEIGHT, WindowGeometryController.TITLE_BUTTON_SPACE));
        assertFalse(WindowChromeButtons.contains(close, window.x() - 1, window.y()));

        assertTrue(window.beginPointer(window.x() + 20, window.y() + 10, 0,
                WindowGeometryController.TITLE_HEIGHT, WindowGeometryController.TITLE_BUTTON_SPACE));
        assertTrue(window.dragPointer(window.x() + 80, window.y() + 50));
        assertTrue(window.releasePointer(0));
        assertEquals(280, window.x());
        assertEquals(150, window.y());
    }
}
