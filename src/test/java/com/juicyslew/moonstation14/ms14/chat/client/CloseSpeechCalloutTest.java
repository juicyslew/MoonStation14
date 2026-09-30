package com.juicyslew.moonstation14.ms14.chat.client;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloseSpeechCalloutTest {
    @Test void independentlyCentersMeasuredStyledLinesWithinNarrowPanels() {
        int panelX = 6;
        int panelWidth = 108;
        // Widths represent Font.width(Component), including bold and mixed-color style runs.
        int[] measuredWidths = {42, 100, 67, 99}; // short name, long rows, including colored mention fragments
        int[] positions = java.util.Arrays.stream(measuredWidths)
                .map(width -> CloseSpeechCallout.centeredTextX(panelX, panelWidth, width)).toArray();

        for (int i = 0; i < measuredWidths.length; i++) {
            assertEquals(panelX + (panelWidth - measuredWidths[i]) / 2, positions[i]);
            assertTrue(positions[i] >= panelX);
            assertTrue(positions[i] + measuredWidths[i] <= panelX + panelWidth);
        }
        assertNotEquals(positions[0], positions[1], "each line is centered independently");
        assertEquals(12, CloseSpeechCallout.centeredTextX(6, 72, 60));
    }

    @Test void rejectsOffscreenAnchorRatherThanRescuingItToAnEdge() {
        assertNull(CloseSpeechCallout.project(new Matrix4f(), 1.1, 0, 0, 100, 100));
        assertNotNull(CloseSpeechCallout.project(new Matrix4f(), 0, 0, 0, 100, 100));
    }

    @Test void perspectiveRejectsRearAnchorsAndKeepsVisibleAnchorsClampable() {
        var perspective = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100f);
        assertNull(CloseSpeechCallout.project(perspective, 0, 0, 2, 854, 480));
        assertNull(CloseSpeechCallout.project(perspective, 100, 0, -2, 854, 480));
        var edge = CloseSpeechCallout.project(perspective, 1.8, 0, -2, 854, 480);
        assertNotNull(edge);
        var clamped = CloseSpeechCallout.layout(edge, 120, 50, 854, 480, 24, java.util.List.of());
        assertNotNull(clamped);
        assertTrue(clamped.x() >= 6 && clamped.x() + clamped.width() <= 848);
    }

    @Test void noPlacementIsReturnedForPackedSmallAndLargeViewports() {
        for (int[] size : new int[][] {{320, 180}, {854, 480}}) {
            var reserved = java.util.List.of(new CloseSpeechCallout.Rect(0, 0, size[0], size[1]));
            assertNull(CloseSpeechCallout.layout(new CloseSpeechCallout.Point(size[0] / 2f, size[1] / 2f),
                    72, 45, size[0], size[1], 24, reserved));
        }
    }

    @Test void perspectiveBodyVisibilityUsesHeadWhenTorsoLeavesViewportAndRejectsBehind() {
        var perspective = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100f);
        // Camera looks steeply upward: head is still visible while a lower torso point has exited below.
        var head = CloseSpeechCallout.project(perspective, 0, 1.2, -2, 854, 480);
        var torso = CloseSpeechCallout.project(perspective, 0, -2.0, -2, 854, 480);
        assertNotNull(head);
        assertNull(torso);
        assertNull(CloseSpeechCallout.projectInFront(perspective, 0, 0, 2, 854, 480));
        assertFalse(CloseSpeechCallout.onScreen(CloseSpeechCallout.projectInFront(
                perspective, 0, 100, -2, 854, 480), 854, 480));
    }

    @Test void headAboveTopClampsPanelAtTopAndAboveHeadIsFirstChoice() {
        var perspective = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100f);
        var head = CloseSpeechCallout.projectInFront(perspective, 0, 1.5, -2, 854, 480);
        assertNotNull(head);
        var rect = CloseSpeechCallout.layout(head, 120, 50, 854, 480, 24, java.util.List.of());
        assertNotNull(rect);
        assertEquals(6, rect.y());
        var visibleHead = CloseSpeechCallout.project(perspective, 0, .1, -2, 854, 480);
        var above = CloseSpeechCallout.layout(visibleHead, 120, 50, 854, 480, 24, java.util.List.of());
        assertNotNull(above);
        assertEquals(Math.round(visibleHead.x() - 60), above.x());
        assertTrue(above.y() < visibleHead.y());
    }

    @Test void nearPlaneHeadFallsBackAboveHighestVisibleBodyAndKeepsPanelInViewport() {
        var perspective = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100f);
        // Head is behind the camera while eye/torso remain in front and visible.
        var head = CloseSpeechCallout.projectInFront(perspective, 0, 1.8, 2, 854, 480);
        var eye = CloseSpeechCallout.project(perspective, 0, .1, -2, 854, 480);
        assertNull(head);
        assertNotNull(eye);
        var fallback = CloseSpeechCallout.chooseAnchor(null, eye, 270);
        var panel = CloseSpeechCallout.layout(fallback, 120, 50, 480, 270, 24, java.util.List.of());
        assertNotNull(panel);
        assertTrue(panel.x() >= 6 && panel.x() + panel.width() <= 474);
        assertTrue(panel.y() >= 6 && panel.y() + panel.height() <= 246);
        assertTrue(panel.y() < eye.y());
        assertNull(CloseSpeechCallout.chooseAnchor(null, null, 270));
    }

    @Test void projectedHeadRemainsPreferredOverLowerVisibleBody() {
        var head = new CloseSpeechCallout.Point(100, 30);
        var stomach = new CloseSpeechCallout.Point(100, 150);
        assertEquals(head, CloseSpeechCallout.chooseAnchor(head, stomach, 270));
    }

    @Test void findsViewportIntersectionBetweenOffscreenSamples() {
        var perspective = new Matrix4f().perspective((float) Math.toRadians(70), 1f, .05f, 100f);
        // At this close distance the visible vertical slice is narrower than the gaps
        // between body samples, although the full rendered silhouette crosses it.
        double feet = -.195, height = .5, z = -.1;
        for (double fraction : new double[] {.08 / height, .55, .9, 1})
            assertFalse(CloseSpeechCallout.onScreen(CloseSpeechCallout.project(perspective,
                    0, feet + height * fraction, z, 100, 100), 100, 100));
        var found = CloseSpeechCallout.intersectSegment(perspective, 0, feet, z,
                0, feet + height, z, 100, 100);
        assertNotNull(found);
        assertTrue(CloseSpeechCallout.onScreen(found.point(), 100, 100));
        var outside = CloseSpeechCallout.intersectSegment(perspective, 0, 2, z, 0, 3, z, 100, 100);
        assertNull(outside);
    }

    @Test void layoutClampsAndReservesInputStripAndAvoidsOverlap() {
        var anchor = new CloseSpeechCallout.Point(99, 99);
        var first = CloseSpeechCallout.layout(anchor, 50, 20, 100, 100, 30, java.util.List.of());
        assertNotNull(first);
        assertTrue(first.x() >= 6 && first.x() + first.width() <= 94);
        assertTrue(first.y() >= 6 && first.y() + first.height() <= 76);
        var second = CloseSpeechCallout.layout(anchor, 50, 20, 100, 100, 30, java.util.List.of(first));
        // A packed viewport may have no non-overlapping slot; omit rather than overlap.
        if (second != null) assertFalse(first.overlaps(second));
    }
}
