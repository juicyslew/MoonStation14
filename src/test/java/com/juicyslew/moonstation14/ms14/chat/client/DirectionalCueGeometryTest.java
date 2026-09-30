package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DirectionalCueGeometryTest {
    private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100f);

    @Test void allYawQuadrantsAndPitchRemainFinite() {
        for (int yaw = 0; yaw < 360; yaw += 45) for (int pitch : new int[]{-75, 0, 75}) {
            Quaternionf camera = new Quaternionf().rotationYXZ((float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0);
            for (int sector = 0; sector < 16; sector++) {
                var p = DirectionalCueGeometry.project(DirectionalCueGeometry.bearing(sector, LocalSpeechPayload.BAND_LEVEL),
                        camera, PROJECTION, 854, 480, false);
                assertNotNull(p);
                assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()));
                assertTrue(p.x() >= 16 && p.x() <= 838 && p.y() >= 0 && p.y() <= 480);
            }
        }
    }

    @Test void bearingOrientationAndVerticalOnlyIgnorePlaceholderSector() {
        assertEquals(1, DirectionalCueGeometry.bearing(0, 1).z, 1e-8);
        assertEquals(1, DirectionalCueGeometry.bearing(4, 1).x, 1e-8);
        assertEquals(-1, DirectionalCueGeometry.bearing(8, 1).z, 1e-8);
        assertEquals(-1, DirectionalCueGeometry.bearing(12, 1).x, 1e-8);
        assertNull(DirectionalCueGeometry.bearing(2, LocalSpeechPayload.BAND_UP));
        var up = DirectionalCueGeometry.project(DirectionalCueGeometry.bearing(0, LocalSpeechPayload.BAND_UP),
                new Quaternionf(), PROJECTION, 854, 480, true);
        var down = DirectionalCueGeometry.project(DirectionalCueGeometry.bearing(0, LocalSpeechPayload.BAND_DOWN),
                new Quaternionf(), PROJECTION, 854, 480, true);
        assertEquals(0, up.y());
        assertEquals(480, down.y());
    }

    @Test void visibleBoundaryNarrowAspectBehindAndInvalidProjection() {
        assertTrue(DirectionalCueGeometry.project(new Vec3(0, 0, -5), new Quaternionf(), PROJECTION,
                854, 480, false).comfortablyVisible());
        assertFalse(DirectionalCueGeometry.project(new Vec3(5, 0, -5), new Quaternionf(), PROJECTION,
                854, 480, false).comfortablyVisible());
        assertFalse(DirectionalCueGeometry.project(new Vec3(0, 0, 5), new Quaternionf(), PROJECTION,
                854, 480, false).comfortablyVisible());
        var narrow = DirectionalCueGeometry.project(new Vec3(2, 0, -4), new Quaternionf(),
                new Matrix4f().perspective((float) Math.toRadians(30), 1, .05f, 100), 320, 180, false);
        assertNotNull(narrow);
        assertTrue(narrow.x() >= 16 && narrow.x() <= 304);
        assertNull(DirectionalCueGeometry.project(new Vec3(Double.NaN, 0, 1), new Quaternionf(), PROJECTION,
                854, 480, false));
        assertNull(DirectionalCueGeometry.project(new Vec3(0, 0, -1), new Quaternionf(),
                new Matrix4f().m00(Float.NaN), 854, 480, false));
    }

    @Test void minecraftYaw180FrontCenterAndRearAreDistinctAtAnyDistance() {
        Quaternionf camera = new Quaternionf().rotationYXZ((float) Math.PI, 0, 0);
        var front = DirectionalCueGeometry.project(new Vec3(0, 0, 5), camera, PROJECTION, 854, 480, false);
        var far = DirectionalCueGeometry.project(new Vec3(0, 0, 50), camera, PROJECTION, 854, 480, false);
        var rear = DirectionalCueGeometry.project(new Vec3(0, 0, -5), camera, PROJECTION, 854, 480, false);
        assertTrue(front.centeredFront());
        assertEquals(front.x(), far.x());
        assertEquals(front.y(), far.y());
        assertEquals(854 / 2.0, front.x());
        assertTrue(front.y() < 480 / 2.0);
        assertFalse(front.rear());
        assertTrue(rear.rear());
        assertFalse(rear.centeredFront());
        assertEquals(480, rear.y());
    }

    @Test void queueIsBoundedCoalescesAndPromotesMentions() {
        DirectionalCueOverlay.clear();
        try {
            for (int i = 1; i <= 18; i++) DirectionalCueOverlay.enqueueClear(i, new Vec3(10, 0, 5), false, 1000 + i);
            assertEquals(12, DirectionalCueOverlay.queued());
            DirectionalCueOverlay.enqueueClear(18, new Vec3(10, 0, 5), false, 1100);
            assertEquals(12, DirectionalCueOverlay.queued());
            DirectionalCueOverlay.enqueueClear(2, new Vec3(10, 0, 5), true, 1100);
            assertTrue(DirectionalCueOverlay.selectedMention(PROJECTION, new Quaternionf(), 854, 480, 1100));
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 1100));
            DirectionalCueOverlay.enqueueMuffled(0, LocalSpeechPayload.BAND_UP, 1101);
            DirectionalCueOverlay.enqueueMuffled(0, LocalSpeechPayload.BAND_UP, 1102);
            assertEquals(12, DirectionalCueOverlay.queued());
            assertEquals(0, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 5000));
        } finally { DirectionalCueOverlay.clear(); }
        assertEquals(0, DirectionalCueOverlay.queued());
    }

    @Test void onePhaseOnly() {
        assertTrue(DirectionalCueOverlay.renderInPhase(null, false));
        assertFalse(DirectionalCueOverlay.renderInPhase(null, true));
        assertFalse(DirectionalCueOverlay.renderInPhase(new net.minecraft.client.gui.screens.ChatScreen(""), false));
        assertTrue(DirectionalCueOverlay.renderInPhase(new net.minecraft.client.gui.screens.ChatScreen(""), true));
    }

    @Test void bothSpeechEventPhasesDrawPanelBeforeCueExactlyOnce() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path relative = Path.of("src/main/java/com/juicyslew/moonstation14/MoonStation14Client.java");
        while (root != null && !Files.exists(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "cannot locate project source root");
        String source = Files.readString(root.resolve(relative));
        for (String method : new String[]{"renderSpeechCues(RenderGuiEvent.Post event)",
                "renderSpeechCuesOnChat(ScreenEvent.Render.Post event)"}) {
            int start = source.indexOf(method);
            assertTrue(start >= 0);
            int end = source.indexOf('}', start);
            String body = source.substring(start, end);
            assertTrue(body.indexOf("LocalSpeechReviewOverlay.render(event)") >= 0);
            assertTrue(body.indexOf("DirectionalCueOverlay.render(event)")
                    > body.indexOf("LocalSpeechReviewOverlay.render(event)"));
        }
        assertEquals(2, source.split("LocalSpeechReviewOverlay.render\\(event\\)", -1).length - 1);
        assertEquals(2, source.split("DirectionalCueOverlay.render\\(event\\)", -1).length - 1);
    }

    @Test void visibleOrdinaryAndMentionSuppressButBlockedMentionAndAnonymousBearingRemain() {
        DirectionalCueOverlay.clear();
        try {
            DirectionalCueOverlay.enqueueClear(1, new Vec3(0, 0, -5), false, 1000);
            assertEquals(0, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 1000));
            DirectionalCueOverlay.enqueueClear(1, new Vec3(0, 0, -5), true, true, 1001);
            assertEquals(0, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 1001));
            DirectionalCueOverlay.enqueueClear(1, new Vec3(0, 0, -5), true, false, 1002);
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 1002));
            DirectionalCueOverlay.enqueueMuffled(0, LocalSpeechPayload.BAND_LEVEL, 1003);
            assertEquals(2, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 1003));
            DirectionalCueOverlay.enqueueClear(1, new Vec3(0, 0, -5), false, 1900);
            assertTrue(DirectionalCueOverlay.selectedMention(PROJECTION, new Quaternionf(), 854, 480, 1900));
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test void viewportBoundsSuppressReceiptVisibleMentionsEvenNearEdges() {
        assertTrue(DirectionalCueGeometry.inViewport(new Vec3(0, 0, -5), new Quaternionf(), PROJECTION));
        assertTrue(DirectionalCueGeometry.inViewport(new Vec3(4.5, 0, -5), new Quaternionf(), PROJECTION));
        assertFalse(DirectionalCueGeometry.inViewport(new Vec3(8, 0, -5), new Quaternionf(), PROJECTION));
        DirectionalCueOverlay.clear();
        try {
            DirectionalCueOverlay.enqueueClear(7, new Vec3(4.5, 0, -5), true, true, 2000);
            assertEquals(0, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 2000));
            DirectionalCueOverlay.enqueueClear(8, new Vec3(8, 0, -5), true, true, 2001);
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 2001));
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test void bodyViewportCountsVisibleTorsoWhenHeadIsAboveViewport() {
        Vec3 feet = new Vec3(0, 0, -4);
        boolean foundClosePitch = false;
        for (int pitch = -89; pitch <= 89; pitch++) {
            Quaternionf camera = new Quaternionf().rotationYXZ(0, (float) Math.toRadians(pitch), 0);
            if (!DirectionalCueGeometry.inViewport(new Vec3(0, 1.8, -4), camera, PROJECTION)
                    && DirectionalCueGeometry.bodyInViewport(feet, 1.9, Vec3.ZERO, camera, PROJECTION)) {
                foundClosePitch = true;
                break;
            }
        }
        assertTrue(foundClosePitch, "torso should remain visible with the head just out of frame");
        assertFalse(DirectionalCueGeometry.bodyInViewport(new Vec3(0, 0, 4), 1.9, Vec3.ZERO,
                new Quaternionf(), PROJECTION));
        assertFalse(DirectionalCueGeometry.bodyInViewport(new Vec3(100, 0, -4), 1.9, Vec3.ZERO,
                new Quaternionf(), PROJECTION));
    }

    @Test void receiptVisiblePolicyUsesVerifiedTrackedBodyAndKeepsOccludedCue() {
        assertTrue(DirectionalCueOverlay.suppressVisibleClear(false, true, true, true, false));
        assertFalse(DirectionalCueOverlay.suppressVisibleClear(false, true, true, false, true));
        assertFalse(DirectionalCueOverlay.suppressVisibleClear(false, false, true, true, true));
        assertFalse(DirectionalCueOverlay.suppressVisibleClear(true, true, true, true, true));
        assertFalse(DirectionalCueOverlay.suppressVisibleClear(false, true, false, false, false));
    }

    @Test void occludedFrontIsEligibleWithoutRaytraceAndSameDirectionMarksCoalesce() {
        DirectionalCueOverlay.clear();
        try {
            DirectionalCueOverlay.enqueueClear(1, new Vec3(0, 0, -5), false, 999);
            assertEquals(0, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 999));
            // A blocked bubble must replace a recent visible one even within its cooldown.
            DirectionalCueOverlay.enqueueClear(1, new Vec3(0, 0, -5), false, false, 1000);
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 1000));
            DirectionalCueOverlay.enqueueClear(2, new Vec3(0, 0, -50), false, false, 1001);
            DirectionalCueOverlay.enqueueClear(3, new Vec3(0, 0, -10), true, false, 1002);
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 1002));
            assertTrue(DirectionalCueOverlay.selectedMention(PROJECTION, new Quaternionf(), 854, 480, 1002));
            assertTrue(DirectionalCueOverlay.selectedPoints(PROJECTION, new Quaternionf(), 854, 480, 1002)
                    .getFirst().centeredFront());
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test void threeDistinctMarksStaySeparatedAndNarrowTopCueRemainsSelected() {
        DirectionalCueOverlay.clear();
        try {
            DirectionalCueOverlay.enqueueMuffled(4, LocalSpeechPayload.BAND_LEVEL, 1000);
            DirectionalCueOverlay.enqueueMuffled(8, LocalSpeechPayload.BAND_LEVEL, 1001);
            DirectionalCueOverlay.enqueueMuffled(12, LocalSpeechPayload.BAND_LEVEL, 1002);
            var points = DirectionalCueOverlay.selectedPoints(PROJECTION, new Quaternionf(), 854, 480, 1002);
            assertEquals(3, points.size());
            for (int i = 0; i < points.size(); i++) for (int j = i + 1; j < points.size(); j++)
                assertTrue(Math.hypot(points.get(i).x() - points.get(j).x(),
                        points.get(i).y() - points.get(j).y()) >= 23);
        } finally { DirectionalCueOverlay.clear(); }
        DirectionalCueOverlay.enqueueMuffled(0, LocalSpeechPayload.BAND_UP, 2000);
        try {
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 360, 180, 2000));
            assertEquals(0, DirectionalCueOverlay.selectedPoints(PROJECTION, new Quaternionf(), 360, 180, 2000)
                    .getFirst().y());
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test void panelNeverSuppressesOrMovesCueInsideIt() {
        LocalSpeechReviewOverlay.clear();
        DirectionalCueOverlay.clear();
        try {
            DirectionalCueOverlay.enqueueClear(1, new Vec3(5, -1, 1), true, 2000);
            var point = DirectionalCueOverlay.selectedPoints(PROJECTION, new Quaternionf(), 320, 180, 2000).getFirst();
            var panel = LocalSpeechReviewOverlay.layout(320, 180, false);
            assertTrue(point.x() >= panel.left() && point.x() <= panel.right());
            assertTrue(point.y() >= panel.top() && point.y() <= panel.bottom());
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 320, 180, 2000));
            LocalSpeechReviewOverlay.toggle();
            assertEquals(point, DirectionalCueOverlay.selectedPoints(PROJECTION, new Quaternionf(), 320, 180, 2000).getFirst());
            LocalSpeechReviewOverlay.toggle();
            assertEquals(point, DirectionalCueOverlay.selectedPoints(PROJECTION, new Quaternionf(), 320, 180, 2000).getFirst());
        } finally {
            DirectionalCueOverlay.clear();
            LocalSpeechReviewOverlay.clear();
        }
    }
}
