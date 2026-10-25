package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandBarLayoutTest {
    @Test void oneHandLayoutShowsOnlyRealCenteredHandAndNoSecondHitTarget() {
        var layout = HandBarLayout.calculate(320, 240, 1);
        assertNotNull(layout);
        assertEquals(151, layout.leftHand().x());
        assertEquals(0, layout.rightHand().width());
        assertEquals(HandBarLayout.Layout.Tile.FIRST, layout.tileAt(layout.leftHand().x(), layout.leftHand().y()));
        assertNotEquals(HandBarLayout.Layout.Tile.SECOND, layout.tileAt(layout.rightHand().x(), layout.rightHand().y()));
        var labels = HandInfoLayout.calculate(320, 240, layout);
        assertNotNull(labels);
        assertNull(labels.right());
    }

    @Test void placesBarBottomCenterAndExposesTilesForHitTesting() {
        var layout = HandBarLayout.calculate(320, 240);
        assertNotNull(layout);
        int handGap = layout.rightHand().x() - layout.leftHand().x() - layout.leftHand().width();
        assertEquals(3, handGap);
        assertEquals(18, layout.leftHand().width());
        assertEquals(15, layout.back().width());
        assertEquals(2, 240 - bottom(layout));
        assertTrue(layout.leftHand().y() >= 0);
        assertTrue(Math.abs(layout.leftHand().x() + layout.rightHand().x()
                + layout.rightHand().width() - 320) <= 1);
        assertEquals(3, layout.leftHand().x() - layout.belt().x() - layout.belt().width());
        assertFalse(overlaps(layout.leftHand(), layout.rightHand()));
        assertFalse(overlaps(layout.rightHand(), layout.back()));
        assertEquals(3, layout.rightHand().x() - layout.leftHand().x() - layout.leftHand().width());
        assertEquals(3, layout.pocket1().x() - layout.rightHand().x() - layout.rightHand().width());
        assertEquals(3, layout.pocket2().x() - layout.pocket1().x() - layout.pocket1().width());
        assertEquals(3, layout.suitstorage().x() - layout.pocket2().x() - layout.pocket2().width());
        assertFalse(overlaps(layout.back(), layout.belt()));
        assertSame(layout.back(), layout.hitTest(layout.back().x(), layout.back().y()));
        assertEquals(HandBarLayout.Layout.Tile.FIRST,
                layout.tileAt(layout.leftHand().x(), layout.leftHand().y()));
        assertNull(layout.hitTest(0, 0));
    }

    @Test void adaptsToNarrowGuiWidthAndStaysInsideBounds() {
        var layout = HandBarLayout.calculate(90, 240);
        assertNotNull(layout);
        assertTrue(layout.leftHand().width() <= 18);
        assertTrue(layout.leftHand().x() >= 0);
        assertTrue(layout.belt().x() + layout.belt().width() <= 90);
        assertTrue(layout.leftHand().y() >= 0);
        assertTrue(240 - bottom(layout) <= 3);
        assertFalse(overlaps(layout.rightHand(), layout.back()));
        assertFalse(overlaps(layout.back(), layout.belt()));
    }

    @Test void layoutsNeverOverlapAcrossSupportedWidthsAndHeights() {
        for (int width : new int[]{90, 100, 180, 320}) {
            for (int height : new int[]{120, 240}) {
                var layout = HandBarLayout.calculate(width, height);
                assertNotNull(layout, width + "x" + height);
                var tiles = new HandBarLayout.Rect[]{layout.leftHand(), layout.rightHand(), layout.back(), layout.belt()};
                for (int i = 0; i < tiles.length; i++) {
                    for (int j = i + 1; j < tiles.length; j++)
                        assertFalse(overlaps(tiles[i], tiles[j]), width + "x" + height + ": " + i + "," + j);
                }
                assertTrue(layout.belt().x() + layout.belt().width() <= layout.leftHand().x() - 2);
                assertTrue(Math.abs(height - bottom(layout)) <= 2);
                for (var tile : tiles) {
                    assertEquals(tile, layout.hitTest(tile.x(), tile.y()));
                    assertNotNull(layout.tileAt(tile.x(), tile.y()));
                    assertNull(layout.tileAt(tile.x() + tile.width(), tile.y()));
                }
            }
        }
    }

    private static int bottom(HandBarLayout.Layout layout) {
        return Math.max(Math.max(layout.leftHand().y() + layout.leftHand().height(),
                        layout.rightHand().y() + layout.rightHand().height()),
                Math.max(layout.back().y() + layout.back().height(), layout.belt().y() + layout.belt().height()));
    }

    private static boolean overlaps(HandBarLayout.Rect a, HandBarLayout.Rect b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }

    @Test void refusesLayoutsTooSmallForLegibleTiles() {
        assertNull(HandBarLayout.calculate(80, 240));
        assertNull(HandBarLayout.calculate(320, 100));
    }
}
