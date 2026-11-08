package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandInfoLayoutTest {
    @Test
    void scaledTextKeepsSymmetricPanelInsetsAtEitherScale() {
        for (float scale : new float[]{1f, 0.5f}) {
            for (int glyphWidth : new int[]{0, 12, 80}) {
                int leftOrigin = HandInfoLayout.textX(20, 96, glyphWidth, scale, false);
                int rightOrigin = HandInfoLayout.textX(120, 96, glyphWidth, scale, true);
                int renderedWidth = Math.round(glyphWidth * scale);
                assertEquals(3, leftOrigin - 20);
                assertEquals(3, 216 - (rightOrigin + renderedWidth));
            }
        }
    }

    @Test
    void standardGuiKeepsLabelsBesideBarAndBottomAnchored() {
        var bar = HandBarLayout.calculate(320, 240);
        var info = HandInfoLayout.calculate(320, 240, bar);
        assertNotNull(info);
        assertEquals(96, info.left().width());
        assertEquals(info.left().width(), info.right().width());
        assertEquals(12, info.left().height());
        assertEquals(bar.leftHand().y() - 14, info.left().y());
        assertEquals(info.left().y(), info.right().y());
        assertEquals(90, info.textBudget());
        assertEquals(bar.leftHand().x() + bar.leftHand().width() - info.left().width(), info.left().x());
        assertEquals(bar.rightHand().x(), info.right().x());
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
    void ammoRowExpandsOnlyItsPanelAndDefaultsStayCompact() {
        var bar = HandBarLayout.calculate(320, 240);
        var compact = HandInfoLayout.calculate(320, 240, bar);
        assertEquals(12, compact.left().height());
        assertEquals(12, compact.right().height());

        var row = HandInfoLayout.calculate(320, 240, bar, false, true);
        assertEquals(12, row.left().height());
        assertEquals(27, row.right().height());
        assertEquals(bar.leftHand().y() - 29, row.right().y());
        assertEquals(row.right().y() + 15, row.left().y());
        assertTrue(row.right().y() + row.right().height() < bar.rightHand().y());

        var offhandOnly = HandInfoLayout.calculate(320, 240, bar, true, false);
        assertEquals(27, offhandOnly.left().height());
        assertEquals(12, offhandOnly.right().height());
        var narrow = HandInfoLayout.calculate(120, 240, HandBarLayout.calculate(120, 240), false, true);
        assertNotNull(narrow);
        assertTrue(narrow.right().x() + narrow.right().width() <= 120);
        assertTrue(narrow.right().y() >= 0);
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
        var narrowInfo = HandInfoLayout.calculate(120, 240, narrowBar);
        assertNotNull(narrowInfo);
        assertTrue(narrowInfo.left().x() >= 0);
        assertTrue(narrowInfo.right().x() + narrowInfo.right().width() <= 120);
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
