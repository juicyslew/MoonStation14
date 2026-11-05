package com.juicyslew.moonstation14.ms14.actions.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionLayoutTest {
    @Test void tooltipHoverAndAnchorStayAbsoluteGuiAcrossScalesOffsetsAndViewportEdges() {
        // GUI-scaled viewport sizes correspond to different window GUI scales; pointer inputs
        // and GuiGraphics tooltip anchors are already in that same absolute GUI coordinate space.
        for (int[] viewport : new int[][]{{320, 240}, {160, 120}, {90, 70}, {640, 360}}) {
            int width = viewport[0], height = viewport[1];
            int barLeft = Math.min(6, Math.max(0, width - 1)), barTop = Math.min(8, Math.max(0, height - 1));
            int px = barLeft + BodyActionLayout.logicalToScreen(12);
            int py = barTop + BodyActionLayout.logicalToScreen(12);
            assertTrue(BodyActionLayout.actionBarPointer(px, barLeft) >= 0);
            assertTrue(BodyActionLayout.actionBarPointer(px, barLeft) < 28);
            assertEquals(px, BodyActionLayout.tooltipAnchor(px));
            assertEquals(py, BodyActionLayout.tooltipAnchor(py));

            var window = BodyActionLayout.calculate((int)Math.ceil(BodyActionLayout.screenToLogical(width)),
                    (int)Math.ceil(BodyActionLayout.screenToLogical(height)), 12, 0);
            int rowY = BodyActionLayout.HEADER_HEIGHT;
            int rowGuiX = BodyActionLayout.logicalToScreen(window.left() + 8);
            int rowGuiY = BodyActionLayout.logicalToScreen(window.top() + rowY + 8);
            assertTrue(BodyActionLayout.actionRowHovered(rowGuiX, rowGuiY, window.left(), window.top(),
                    window.width(), rowY), "row hover at " + width + "x" + height);
            assertEquals(rowGuiX, BodyActionLayout.tooltipAnchor(rowGuiX));
            assertEquals(rowGuiY, BodyActionLayout.tooltipAnchor(rowGuiY));
        }
        assertFalse(BodyActionLayout.actionRowHovered(-1, -1, 0, 0, 190, BodyActionLayout.HEADER_HEIGHT));
        // Nonzero HUD origin is honored both for hover-local coordinates and tooltip's absolute anchor.
        int originX = 37, originY = 23;
        int pointerX = originX + BodyActionLayout.logicalToScreen(5);
        int pointerY = originY + BodyActionLayout.logicalToScreen(5);
        assertTrue(BodyActionLayout.actionBarPointer(pointerX, originX) < 28);
        assertEquals(pointerX, BodyActionLayout.tooltipAnchor(pointerX));
        assertEquals(pointerY, BodyActionLayout.tooltipAnchor(pointerY));
    }
    @Test void activeAlternateIconCommunicatesToggleWithoutPressedStyle() {
        var icon = net.minecraft.resources.ResourceLocation.withDefaultNamespace("test");
        var alternate = net.minecraft.resources.ResourceLocation.withDefaultNamespace("test_on");
        var withAlternate = new com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot.Entry(icon, "p", "name", icon,
                java.util.Optional.of(alternate), com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype.Kind.TOGGLE,
                true, true, 0, 0, "", 0);
        var withoutAlternate = new com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot.Entry(icon, "p", "name", icon,
                java.util.Optional.empty(), com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype.Kind.TOGGLE,
                true, true, 0, 0, "", 0);
        assertEquals(alternate, BodyActionPresentation.icon(withAlternate));
        assertFalse(BodyActionPresentation.activePressedStyle(withAlternate));
        assertTrue(BodyActionPresentation.activePressedStyle(withoutAlternate));
    }
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
    @Test void allTwentyAssignedSlotsRetainGeometryAndHitIdentityAcrossHoles() {
        var slots = java.util.stream.IntStream.rangeClosed(1, 20).mapToObj(i -> new BodyActionSlots.Slot(i, null)).toList();
        var rects = BodyActionLayout.barRects(slots, 640, 480, 0, 0);
        assertEquals(20, rects.size());
        assertEquals(2, rects.stream().map(BodyActionLayout.BarRect::x).distinct().count());
        for (var rect : rects) {
            assertTrue(rect.contains(rect.x() + 14, rect.y() + 14));
            assertTrue(rect.x() + 28 <= 640 && rect.y() + BodyActionLayout.ACTION_BAR_CELL_HEIGHT <= 480);
            assertEquals(rect.x(), rect.labelX());
            assertTrue(rect.labelY() >= rect.y() + BodyActionLayout.ACTION_BAR_BUTTON);
            assertTrue(rect.labelY() + 9 <= rect.y() + BodyActionLayout.ACTION_BAR_CELL_HEIGHT);
            assertTrue(rect.labelX() + rect.labelWidth() <= rect.x() + BodyActionLayout.ACTION_BAR_CELL_WIDTH);
            assertFalse(rect.labelY() < rect.y() + BodyActionLayout.ACTION_BAR_ICON_INSET + BodyActionLayout.ACTION_BAR_ICON
                    && rect.labelY() + 9 > rect.y() + BodyActionLayout.ACTION_BAR_ICON_INSET);
        }
        assertEquals(10, rects.get(9).slotNumber()); assertEquals(11, rects.get(10).slotNumber()); assertEquals(20, rects.get(19).slotNumber());
        var hole = BodyActionLayout.barRects(java.util.List.of(slots.get(0), slots.get(10), slots.get(19)), 640, 480, 0, 0);
        assertEquals(rects.get(10).x(), hole.get(1).x()); assertEquals(rects.get(10).y(), hole.get(1).y());
        assertFalse(rects.get(0).contains(28, 10));
        assertTrue(BodyActionLayout.barRects(java.util.List.of(), 640, 480, 0, 0).isEmpty());
        assertEquals(20, BodyActionLayout.barRects(slots, 320, 840, 0, 0).size());
         assertEquals(20, BodyActionLayout.barRects(slots, 320, 180, 0, 0).size());
        assertTrue(BodyActionLayout.barRects(slots, 40, 20, 0, 0).isEmpty());
    }
    @Test void halfScaleInverseMappingKeepsNonzeroOriginAndButtonStrideAligned() {
        int originX = 6, originY = 8;
        assertEquals(28, (int)BodyActionLayout.toLogical(originX + 14, originX));
        assertEquals(42, (int)BodyActionLayout.toLogical(originY + 21, originY));
        var all = java.util.stream.IntStream.rangeClosed(1, 20).mapToObj(i -> new BodyActionSlots.Slot(i, null)).toList();
        var geometry = BodyActionLayout.barRects(all, 640, 480, 0, 0);
        assertEquals(geometry.get(0).x(), (int)BodyActionLayout.toLogical(originX, originX));
         assertEquals(BodyActionLayout.ACTION_BAR_CELL_STRIDE_X,
                 (int)BodyActionLayout.toLogical(originX + 15, originX));
         assertEquals(geometry.get(10).x(), (int)BodyActionLayout.toLogical(originX + 15, originX));
        assertEquals(geometry.get(1).y(), (int)BodyActionLayout.toLogical(originY + 21, originY));
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
        var rect = BodyActionLayout.barRects(java.util.List.of(new BodyActionSlots.Slot(1, null)), 40, 40, 0, 0).get(0);
        assertTrue(rect.contains(27, 27));
        assertFalse(rect.contains(28, 27));
    }
}
