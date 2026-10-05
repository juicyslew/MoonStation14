package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandInfoLayoutTest {
    @Test
    void standardGuiKeepsLabelsBesideBarAndBottomAnchored() {
        var bar = HandBarLayout.calculate(320, 240);
        var info = HandInfoLayout.calculate(320, 240, bar);
        assertNotNull(info);
        assertEquals(48, info.left().width());
        assertTrue(info.left().x() + info.left().width() <= bar.leftHand().x() + bar.leftHand().width());
        assertTrue(info.right().x() >= bar.rightHand().x());
        assertTrue(info.left().x() + info.left().width() <= info.right().x());
        assertTrue(info.left().y() + info.left().height() <= 240);
        assertTrue(info.left().y() + info.left().height() < bar.leftHand().y());
        assertTrue(info.textBudget() > 0);
        for (var label : new HandBarLayout.Rect[]{info.left(), info.right()}) {
            for (var tile : new HandBarLayout.Rect[]{bar.leftHand(), bar.rightHand(), bar.back(), bar.belt()})
                assertFalse(intersects(label, tile));
            assertTrue(label.x() >= 0 && label.x() + label.width() <= 320);
        }
    }

    @Test
    void centeredGeometryRemainsInsideBoundsAtSupportedWidths() {
        for (int width : new int[]{90, 180, 320, 640}) {
            var bar = HandBarLayout.calculate(width, 180);
            var info = HandInfoLayout.calculate(width, 180, bar);
            assertNotNull(bar);
            assertTrue(Math.abs(bar.leftHand().x() + bar.rightHand().x() + bar.rightHand().width() - width) <= 1);
            assertTrue(bar.back().x() + bar.back().width() + 2 <= bar.belt().x());
            assertTrue(bar.belt().x() + bar.belt().width() <= bar.leftHand().x());
            for (var tile : new HandBarLayout.Rect[]{bar.leftHand(), bar.rightHand(), bar.back(), bar.belt()}) {
                assertTrue(tile.x() >= 0 && tile.x() + tile.width() <= width);
                assertTrue(tile.y() >= 0);
            }
            if (info != null) {
                assertTrue(info.left().x() >= 0 && info.right().x() + info.right().width() <= width);
                assertTrue(info.left().x() + info.left().width() <= info.right().x());
                assertTrue(info.left().y() + info.left().height() < bar.leftHand().y());
                assertTrue(info.right().y() + info.right().height() < bar.rightHand().y());
                for (var label : new HandBarLayout.Rect[]{info.left(), info.right()})
                    for (var tile : new HandBarLayout.Rect[]{bar.leftHand(), bar.rightHand(), bar.back(), bar.belt()})
                        assertFalse(intersects(label, tile));
            }
        }
    }

    @Test
    void narrowGuiHidesLabelsAndLargeScaleRetainsTextBudget() {
        var narrowBar = HandBarLayout.calculate(120, 240);
        assertNull(HandInfoLayout.calculate(120, 240, narrowBar));
        assertNull(HandInfoLayout.calculate(320, 240, null));

        var largeBar = HandBarLayout.calculate(640, 480);
        var large = HandInfoLayout.calculate(640, 480, largeBar);
        assertNotNull(large);
        assertEquals(HandInfoLayout.calculate(320, 240, HandBarLayout.calculate(320, 240)).textBudget(),
                large.textBudget());
    }

    private static boolean intersects(HandBarLayout.Rect a, HandBarLayout.Rect b) {
        return a.x() < b.x() + b.width() && a.x() + a.width() > b.x()
                && a.y() < b.y() + b.height() && a.y() + a.height() > b.y();
    }
}
