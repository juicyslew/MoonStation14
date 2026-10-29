package com.juicyslew.moonstation14.ms14.actions.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionLayoutTest {
    @Test void smallAndScaledViewportsKeepRowsReachableAndClampScroll() {
        for (int[] viewport : new int[][]{{320,240},{160,120},{90,70}}) {
            var layout = BodyActionLayout.calculate(viewport[0], viewport[1], 3, 99);
            assertTrue(layout.visibleRows() >= 1);
            assertEquals(Math.max(0,3-layout.visibleRows()), layout.scrollOffset());
            int last = layout.rowAt(layout.left()+1, layout.top()+BodyActionLayout.HEADER_HEIGHT+1, 3);
            assertEquals(layout.scrollOffset(), last);
            var first = BodyActionLayout.calculate(viewport[0], viewport[1], 3, 0);
            assertEquals(0, first.scrollOffset());
            assertEquals(0, first.rowAt(first.left()+1, first.top()+BodyActionLayout.HEADER_HEIGHT+1, 3));
        }
        var one = BodyActionLayout.calculate(320,240,3,0);
        assertEquals(-1, one.rowAt(one.left()-1,one.top()+40,3));
    }
    @Test void cooldownProgressStartsAtFullAndEndsAtZero() {
        assertEquals(1f, BodyActionLayout.cooldownFraction(10,10));
        assertEquals(.5f, BodyActionLayout.cooldownFraction(5,10));
        assertEquals(0f, BodyActionLayout.cooldownFraction(0,10));
    }
    @Test void actionBarHitTestingMatchesVerticalButtonsAndConsumesGaps() {
        assertEquals(0, BodyActionLayout.actionBarSlot(6, 50, 6, 50));
        assertEquals(0, BodyActionLayout.actionBarSlot(33, 77, 6, 50));
        assertEquals(-1, BodyActionLayout.actionBarSlot(34, 60, 6, 50));
        assertEquals(-1, BodyActionLayout.actionBarSlot(6, 78, 6, 50));
        assertEquals(1, BodyActionLayout.actionBarSlot(10, 80, 6, 50));
        assertEquals(2, BodyActionLayout.actionBarSlot(10, 110, 6, 50));
        assertEquals(-1, BodyActionLayout.actionBarSlot(6, 140, 6, 50));
    }
    @Test void halfScaleInverseMappingKeepsNonzeroOriginAndButtonStrideAligned() {
        int originX = 6, originY = 8;
        assertEquals(28, (int)BodyActionLayout.toLogical(originX + 14, originX));
        assertEquals(30, (int)BodyActionLayout.toLogical(originY + 15, originY));
        assertEquals(0, BodyActionLayout.actionBarSlot(
                (int)BodyActionLayout.toLogical(originX, originX),
                (int)BodyActionLayout.toLogical(originY, originY), 0, 0));
        assertEquals(1, BodyActionLayout.actionBarSlot(
                (int)BodyActionLayout.toLogical(originX + 1, originX),
                (int)BodyActionLayout.toLogical(originY + 15, originY), 0, 0));
        assertEquals(-1, BodyActionLayout.actionBarSlot(
                (int)BodyActionLayout.toLogical(originX + 1, originX),
                (int)BodyActionLayout.toLogical(originY + 14, originY), 0, 0));
    }
    @Test void cooldownWipeAndCompletionBlinkAreBounded() {
        assertEquals(1f, BodyActionLayout.cooldownWipeProgress(10, 10));
        assertEquals(.5f, BodyActionLayout.cooldownWipeProgress(5, 10));
        assertEquals(0f, BodyActionLayout.cooldownWipeProgress(0, 10));
        assertEquals(1f, BodyActionLayout.cooldownBlinkProgress(0));
        assertEquals(.5f, BodyActionLayout.cooldownBlinkProgress(1));
        assertEquals(0f, BodyActionLayout.cooldownBlinkProgress(2));
        assertEquals("1.3 s", BodyActionLayout.cooldownSeconds(26));
    }
    @Test void actionBarPointerPolicyUsesSharedHostAdmissionAndGrabState() {
        assertTrue(BodyActionLayout.pointerHostAllowed(true, true, false)); // gameplay or StoragePopupScreen
        assertFalse(BodyActionLayout.pointerHostAllowed(false, true, false));
        assertTrue(BodyActionLayout.pointerHostAllowed(true,
                com.juicyslew.moonstation14.ms14.ui.client.GameplayHudPolicy.hudPaintsWorkspace(
                        new net.minecraft.client.gui.screens.ChatScreen("")), false)); // shared HUD workspace host
        assertFalse(BodyActionLayout.pointerHostAllowed(true, false, false)); // unrelated screen
        assertFalse(BodyActionLayout.pointerHostAllowed(true, true, true)); // grabbed cursor
    }
    @Test void enlargedIconAndOverlaysFitTheUnchangedFourteenPhysicalPixelButton() {
        int button = BodyActionLayout.ACTION_BAR_BUTTON, inset = BodyActionLayout.ACTION_BAR_ICON_INSET,
                icon = BodyActionLayout.ACTION_BAR_ICON;
        assertEquals(button, inset + icon + inset);
        assertEquals(14, BodyActionLayout.toScreenSize(button));
        assertEquals(12, BodyActionLayout.toScreenSize(icon));
        assertTrue(BodyActionLayout.toScreenSize(icon) < BodyActionLayout.toScreenSize(button));
        assertEquals(0, BodyActionLayout.actionBarSlot(27, 27, 0, 0));
        assertEquals(-1, BodyActionLayout.actionBarSlot(28, 27, 0, 0));
    }
}
