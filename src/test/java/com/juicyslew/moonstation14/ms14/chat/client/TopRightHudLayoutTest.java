package com.juicyslew.moonstation14.ms14.chat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopRightHudLayoutTest {
    @Test
    void chatAndStatusesNeverOverlapEachOtherOrBottomBarAtGuiScales() {
        for (int[] resolution : new int[][] {{320, 240}, {800, 450}, {180, 140}, {1280, 960}}) {
            for (int scale : new int[] {1, 2, 3}) {
                int width = resolution[0] / scale, height = resolution[1] / scale;
                var chat = TopRightHudLayout.chat(width, height);
                assertTrue(chat.left() >= 0 && chat.right() <= width);
                assertTrue(chat.top() >= 0 && chat.bottom() <= height);
                assertTrue(chat.width() <= 155 && chat.height() <= 78);
                int top = TopRightHudLayout.statusTop(width, height);
                int rows = TopRightHudLayout.statusRows(width, height);
                assertTrue(top >= chat.bottom());
                for (int i = 0; i < rows; i++) {
                    var row = TopRightHudLayout.statusRow(width, height, i);
                    assertEquals(chat.left(), row.left());
                    assertEquals(chat.right(), row.right());
                    assertEquals(TopRightHudLayout.ROW_HEIGHT, row.height());
                    assertTrue(row.top() > chat.bottom());
                    assertTrue(row.bottom() <= TopRightHudLayout.statusLimit(height));
                    if (i > 0) assertEquals(TopRightHudLayout.statusRow(width, height, i - 1).bottom(), row.top());
                }
                assertTrue(chat.bottom() <= Math.max(6, height - TopRightHudLayout.BOTTOM_RESERVE));
            }
        }
        assertEquals(3, TopRightHudLayout.statusRows(180, 140));
        assertEquals(new TopRightHudLayout.Rect(159, 6, 314, 84), TopRightHudLayout.chat(320, 240));
        assertEquals(new TopRightHudLayout.Rect(19, 6, 174, 71), TopRightHudLayout.chat(180, 140));
        assertEquals(0.78f, TopRightHudLayout.STATUS_FONT_SCALE);
    }

    @Test
    void examineStartsBelowSevenReservedRowsAndFitsRemainingBottomSpace() {
        for (int[] resolution : new int[][] {{320, 240}, {800, 450}, {1280, 960}}) {
            int width = resolution[0], height = resolution[1];
            int top = TopRightHudLayout.examineTop(width, height);
            for (int i = 0; i < TopRightHudLayout.EXAMINE_RESERVED_STATUS_ROWS; i++)
                assertTrue(TopRightHudLayout.statusRow(width, height, i).bottom() < top);
            assertTrue(TopRightHudLayout.examineAvailableHeight(width, height) > 0);
            assertEquals(TopRightHudLayout.chat(width, height).right(), TopRightHudLayout.statusRow(width, height, 0).right());
        }
    }
}
