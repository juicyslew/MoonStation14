package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoragePopupLayoutTest {
    @Test void newDefaultsSitJustAboveHandBarAndClampOnSmallScreens() {
        var position = PopupDefaultLayout.calculate(StoragePopupWorkspace.HostKind.BAG, 91, 44, 320, 240, java.util.List.of());
        var bar = HandBarLayout.calculate(320, 240);
        assertEquals(Math.min(bar.leftHand().y(), bar.back().y()) - 4, position.y() + position.height());
        var small = PopupDefaultLayout.calculate(StoragePopupWorkspace.HostKind.WORLD, 91, 44, 80, 30, java.util.List.of());
        assertTrue(small.x() >= 0 && small.y() >= 0);
        var workspace = new StoragePopupWorkspace(80, 30);
        var world = StoragePopupWorkspace.HostKey.world(java.util.UUID.randomUUID());
        workspace.open(world, true, small.x(), small.y(), small.width(), small.height());
        var window = workspace.window(world);
        assertTrue(window.x() + window.width() <= 80 && window.y() + window.height() <= 30);
    }

    @Test void defaultPlacementShiftsToAvoidExistingBottomWindow() {
        var existing = PopupDefaultLayout.calculate(StoragePopupWorkspace.HostKind.BAG, 91, 44, 320, 240, java.util.List.of());
        var next = PopupDefaultLayout.calculate(StoragePopupWorkspace.HostKind.BELT, 91, 44, 320, 240,
                java.util.List.of(existing));
        assertFalse(existing.x() < next.x() + next.width() && next.x() < existing.x() + existing.width()
                && existing.y() < next.y() + next.height() && next.y() < existing.y() + existing.height());
    }

    @Test void dimensionsAndKindsAreFixed() {
        assertEquals(84, StoragePopupLayout.outerWidth(7));
        assertEquals(44, StoragePopupLayout.outerHeight(4));
        assertEquals(94, StoragePopupLayout.outerWidth(8));
        assertEquals(24, StoragePopupLayout.outerHeight(2));
        for (var kind : StoragePopupLayout.Kind.values()) {
            var layout = StoragePopupLayout.at(kind, 0, 0);
            assertTrue(layout.columns() <= 8);
            assertTrue(layout.rows() <= 4);
        }
    }

    @Test void controlsAreNonOverlappingAndGridHasExclusiveEdges() {
        var layout = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 10, 20);
        assertFalse(layout.hitClose(12, 38));
        assertTrue(layout.hitClose(12, 22));
        assertTrue(layout.hitDrag(12, 38));
        assertTrue(layout.hitDrag(12, 30));
        assertFalse(layout.hitDrag(12, 22)); // Close always takes priority.
        assertNull(layout.cellAt(20, 22));
        assertEquals(new StoragePopupLayout.Cell(0, 0), layout.cellAt(21, 22));
        assertNull(layout.cellAt(21 + 7 * 10, 22));
        assertNull(layout.cellAt(21, 22 + 4 * 10));
        assertNull(layout.cellAt(21, 21));
    }

    @Test void entireLeftStripDragsExceptCloseAndGridNeverDoes() {
        var layout = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 10, 20);
        assertFalse(layout.hitDrag(11, 21));
        assertTrue(layout.hitClose(11, 21));
        assertTrue(layout.hitDrag(10, 20));
        assertTrue(layout.hitDrag(19, 20));
        assertTrue(layout.hitDrag(19, 63));
        assertFalse(layout.hitDrag(20, 20));
        var grid = layout.gridBounds();
        assertFalse(layout.hitDrag(grid.x(), grid.y()));
        assertTrue(layout.visibleCellAt(grid.x(), grid.y(), 81, 44) != null);
        assertEquals(8, layout.closeBounds().width());
    }

    @Test void tenPixelCellsMapBoundariesAndClipNegativeViewportCoordinates() {
        var layout = StoragePopupLayout.at(StoragePopupLayout.Kind.WORLD, 0, 0);
        var grid = layout.visibleGrid(91, 44);
        assertEquals(new StoragePopupLayout.Cell(0, 0), layout.visibleCellAt(grid.x(), grid.y(), 91, 44));
        assertEquals(new StoragePopupLayout.Cell(7, 3), layout.visibleCellAt(grid.x() + 7 * 10, grid.y() + 3 * 10, 91, 44));
        assertNull(layout.visibleCellAt(grid.x() + grid.width(), grid.y(), 91, 44));
        assertNull(layout.visibleCellAt(grid.x(), grid.y() + grid.height(), 91, 44));
        assertNull(layout.visibleCellAt(8, 2, 91, 44));
        assertNull(layout.visibleCellAt(-1, 4, 152, 72));
        var clipped = layout.visibleGrid(32, 24);
        assertEquals(new StoragePopupLayout.Cell(0, 0), layout.visibleCellAt(clipped.x(), clipped.y(), 32, 24));
        assertNull(layout.visibleCellAt(clipped.x() + clipped.width(), clipped.y(), 32, 24));
    }

    @Test void anchorsFitOrPinToOriginWhenViewportIsTooSmall() {
        assertArrayEquals(new int[]{200 - StoragePopupLayout.outerWidth(7), 100 - StoragePopupLayout.outerHeight(4)},
                StoragePopupLayout.clampDragAnchor(999, 999, 200, 100, 7, 4));
        assertArrayEquals(new int[]{0, 0}, StoragePopupLayout.clampDragAnchor(-5, -5, 100, 40, 7, 4));
        assertArrayEquals(new int[]{0, 0}, StoragePopupLayout.clampDragAnchor(20, 20, 1, 1, 8, 4));
    }

    @Test void clippedGridNeverExposesBorderOrInvisibleCells() {
        for (var kind : new StoragePopupLayout.Kind[]{StoragePopupLayout.Kind.BAG, StoragePopupLayout.Kind.BELT}) {
            var layout = StoragePopupLayout.at(kind, 0, 0);
            int fullWidth = layout.outerBounds().width(), fullHeight = layout.outerBounds().height();
            var grid = layout.visibleGrid(fullWidth, fullHeight);
            assertEquals(new StoragePopupLayout.Cell(0, 0), layout.visibleCellAt(grid.x(), grid.y(), fullWidth, fullHeight));
            assertNull(layout.visibleCellAt(8, 3, fullWidth, fullHeight));
            assertNull(layout.visibleCellAt(10, 1, fullWidth, fullHeight));
            assertEquals(layout.gridBounds(), layout.visibleGrid(fullWidth, fullHeight));
            var outline = layout.outlineBounds();
            var outer = layout.outerBounds();
            assertTrue(outer.x() <= outline.x() && outer.y() <= outline.y());
            assertTrue(outline.x() + outline.width() <= outer.x() + outer.width());
            assertTrue(outline.y() + outline.height() <= outer.y() + outer.height());
            var last = layout.gridBounds();
            assertEquals(new StoragePopupLayout.Cell(layout.columns() - 1, layout.rows() - 1),
                    layout.visibleCellAt(last.x() + last.width() - 1, last.y() + last.height() - 1, fullWidth, fullHeight));
            assertNull(layout.visibleCellAt(last.x() + last.width(), last.y(), fullWidth, fullHeight));
            assertNull(layout.visibleCellAt(last.x(), last.y() + last.height(), fullWidth, fullHeight));
            var clipped = layout.visibleGrid(42, 26);
            assertEquals(new StoragePopupLayout.Cell(0, 0), layout.visibleCellAt(clipped.x(), clipped.y(), 42, 26));
            assertNull(layout.visibleCellAt(clipped.x() + clipped.width(), clipped.y(), 42, 26));
            assertNull(layout.visibleCellAt(clipped.x(), clipped.y() + clipped.height(), 42, 26));
            assertTrue(layout.hitClose(2, 2));
            assertFalse(layout.hitGrip(2, 10));
            assertTrue(layout.hitDrag(2, 10));
            assertNull(layout.visibleCellAt(2, 10, 42, 26));
        }
    }

    @Test void ranksFollowOrderedEntriesAfterMove() {
        var entries = new java.util.ArrayList<>(java.util.List.of("a", "b", "c"));
        assertEquals(1, StoragePopupLayout.rank(entries.size(), entries.indexOf("b")));
        entries.remove("a"); entries.add("a");
        assertEquals(2, StoragePopupLayout.rank(entries.size(), entries.indexOf("a")));
        assertEquals(0, StoragePopupLayout.rank(entries.size(), entries.indexOf("b")));
        assertEquals(2, StoragePopupLayout.rank(1, 0));
    }

    @Test void popupBoundsOccludeOnlyOverlappingPixels() {
        var lower = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 10, 10).outerBounds();
        var upper = new StoragePopupLayout.Rect(50, 20, 30, 25);
        assertTrue(lower.intersects(upper));
        assertFalse(lower.intersects(new StoragePopupLayout.Rect(97, 10, 10, 10)));
        assertFalse(lower.intersects(new StoragePopupLayout.Rect(20, 56, 5, 5)));
        assertTrue(new StoragePopupLayout.Rect(52, 22, 1, 1).intersects(upper));
    }
}
