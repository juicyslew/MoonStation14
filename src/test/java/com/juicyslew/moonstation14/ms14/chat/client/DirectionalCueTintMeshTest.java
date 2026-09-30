package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload.DistanceTier;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload.Mode;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectionalCueTintMeshTest {
    private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100f);
    private record Vertex(double x, double y, double alpha) { }

    @Test void viewerAttenuatesButNeverClipsLitCellsInsidePanel() {
        LocalSpeechReviewOverlay.clear();
        try {
            int width = 854, height = 480;
            var point = new DirectionalCueGeometry.Point(838, 390, false, 0, false, false);
            var panel = LocalSpeechReviewOverlay.layout(width, height, false);
            assertTrue(point.x() >= panel.left() && point.x() <= panel.right());
            assertTrue(point.y() >= panel.top() && point.y() <= panel.bottom());
            assertTrue(litPanelVertices(width, height, point,
                    DirectionalCueOverlay.panel(width, height, false)) > 0);
            assertTrue(maxPanelAlpha(width, height, point, DirectionalCueOverlay.panel(width, height, false))
                    < maxPanelAlpha(width, height, point, null));
            LocalSpeechReviewOverlay.toggle();
            assertNull(DirectionalCueOverlay.panel(width, height, false));
            assertTrue(litPanelVertices(width, height, point,
                    DirectionalCueOverlay.panel(width, height, false)) > 0);
            LocalSpeechReviewOverlay.toggle();
            assertTrue(litPanelVertices(width, height, point,
                    DirectionalCueOverlay.panel(width, height, true)) > 0);
        } finally { LocalSpeechReviewOverlay.clear(); }
    }

    private static double maxPanelAlpha(int width, int height, DirectionalCueGeometry.Point point,
                                        LocalSpeechReviewOverlay.Layout visiblePanel) {
        var panel = LocalSpeechReviewOverlay.layout(width, height, false);
        List<Vertex> vertices = new ArrayList<>();
        DirectionalCueTintMesh.emit(width, height, point, 1, 1, Mode.SAY, false, visiblePanel,
                (x, y, alpha, style) -> vertices.add(new Vertex(x, y, alpha)));
        return vertices.stream().filter(v -> v.x() >= panel.left() && v.x() <= panel.right()
                && v.y() >= panel.top() && v.y() <= panel.bottom()).mapToDouble(Vertex::alpha).max().orElse(0);
    }

    private static long litPanelVertices(int width, int height, DirectionalCueGeometry.Point point,
                                         LocalSpeechReviewOverlay.Layout reservedPanel) {
        var panel = LocalSpeechReviewOverlay.layout(width, height, false);
        List<Vertex> vertices = new ArrayList<>();
        DirectionalCueTintMesh.emit(width, height, point, 1, 1, Mode.SAY, false, reservedPanel,
                (x, y, alpha, style) -> vertices.add(new Vertex(x, y, alpha)));
        return vertices.stream().filter(v -> v.x() > panel.left() + 16 && v.x() < panel.right() - 16
                && v.y() > panel.top() + 16 && v.y() < panel.bottom() - 16 && v.alpha() > 0).count();
    }

    @Test void occludedCenteredFrontOrdinaryAndMentionEmitVisibleSubduedHalos() {
        var panel = LocalSpeechReviewOverlay.layout(854, 480, false);
        DirectionalCueOverlay.clear();
        try {
            for (boolean mention : List.of(false, true)) {
                DirectionalCueOverlay.clear();
                DirectionalCueOverlay.enqueueClear(1, UUID.randomUUID(), new Vec3(0, 0, -5), mention,
                        false, Mode.SAY, 4000, 1000);
                var points = DirectionalCueOverlay.selectedPoints(PROJECTION, new Quaternionf(), 854, 480, 2000);
                assertEquals(1, points.size());
                assertTrue(points.getFirst().centeredFront());
                List<Vertex> vertices = new ArrayList<>();
                List<DirectionalCueTintMesh.Style> styles = new ArrayList<>();
                DirectionalCueTintMesh.emit(854, 480, points.getFirst(), .78, 1, Mode.SAY, mention, panel,
                        (x, y, alpha, style) -> {
                            vertices.add(new Vertex(x, y, alpha));
                            styles.add(style);
                        });
                assertTrue(vertices.stream().anyMatch(v -> v.alpha() > 0), "centered cue must paint pixels");
                assertTrue(vertices.stream().mapToDouble(Vertex::alpha).max().orElseThrow() < .16);
                var style = styles.getFirst();
                if (mention) assertTrue(style.red() > style.blue());
                else assertTrue(style.blue() > style.red());
            }
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test void lifetimeFadeReachesZeroWithoutChangingDistanceProfile() {
        var panel = new LocalSpeechReviewOverlay.Layout(-200, -200, -100, -100, 1);
        var edge = new DirectionalCueGeometry.Point(16, 240, false, 0, false, false);
        var centered = new DirectionalCueGeometry.Point(427, 226, true, 0, true, false);
        long received = 1000, lifetime = 4000;
        double half = DirectionalCueOverlay.lifetimeFade(received, lifetime, 3000);
        double nearExpiry = DirectionalCueOverlay.lifetimeFade(received, lifetime, 4999);
        double expired = DirectionalCueOverlay.lifetimeFade(received, lifetime, 5000);
        assertEquals(1, half);
        assertTrue(nearExpiry > 0 && nearExpiry < .002);
        assertEquals(0, expired);
        for (var point : List.of(edge, centered)) {
            double fullPeak = maxEmittedAlpha(point, .7, half, panel);
            double fadingPeak = maxEmittedAlpha(point, .7, nearExpiry, panel);
            assertTrue(fullPeak > 0);
            assertEquals(fullPeak * nearExpiry, fadingPeak, 1e-12);
            assertEquals(0, maxEmittedAlpha(point, .7, expired, panel));
            assertTrue(maxEmittedAlpha(point, 1, half, panel) > fullPeak);
        }
    }

    private static double maxEmittedAlpha(DirectionalCueGeometry.Point point, double intensity, double fade,
                                          LocalSpeechReviewOverlay.Layout panel) {
        List<Vertex> vertices = new ArrayList<>();
        DirectionalCueTintMesh.emit(854, 480, point, intensity, fade, Mode.SAY, false, panel,
                (x, y, alpha, style) -> vertices.add(new Vertex(x, y, alpha)));
        return vertices.stream().mapToDouble(Vertex::alpha).max().orElse(0);
    }

    @Test void emittedEdgeQuadsKeepGuiWindingAndFadeInwardOnEverySide() {
        int width = 854, height = 480;
        var panel = new LocalSpeechReviewOverlay.Layout(-200, -200, -100, -100, 1);
        var points = List.of(
                new DirectionalCueGeometry.Point(16, height / 2.0, false, 0, false, false),
                new DirectionalCueGeometry.Point(width - 16, height / 2.0, false, 0, false, false),
                new DirectionalCueGeometry.Point(width / 2.0, 0, false, 0, false, false),
                new DirectionalCueGeometry.Point(width / 2.0, height, false, 0, false, true));
        for (int side = 0; side < points.size(); side++) {
            List<Vertex> vertices = new ArrayList<>();
            DirectionalCueTintMesh.emit(width, height, points.get(side), 1, 1, Mode.SAY, false, panel,
                    (x, y, alpha, style) -> vertices.add(new Vertex(x, y, alpha)));
            assertTrue(vertices.size() >= 4 * 100, "missing cells on edge " + side);
            assertEquals(0, vertices.size() % 4);
            int fading = 0;
            for (int i = 0; i < vertices.size(); i += 4) {
                Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2), d = vertices.get(i + 3);
                assertEquals(a.x(), b.x());
                assertEquals(c.x(), d.x());
                assertEquals(a.y(), d.y());
                assertEquals(b.y(), c.y());
                assertTrue(a.x() < c.x() && a.y() < b.y(), "backface or degenerate quad on edge " + side);
                for (Vertex vertex : List.of(a, b, c, d)) {
                    assertTrue(vertex.x() >= 0 && vertex.x() <= width);
                    assertTrue(vertex.y() >= 0 && vertex.y() <= height);
                }
                double edgeAlpha = switch (side) {
                    case 0 -> a.alpha() + b.alpha();
                    case 1 -> c.alpha() + d.alpha();
                    case 2 -> a.alpha() + d.alpha();
                    default -> b.alpha() + c.alpha();
                };
                double innerAlpha = a.alpha() + b.alpha() + c.alpha() + d.alpha() - edgeAlpha;
                if (edgeAlpha > 0) {
                    if (side != 3) assertTrue(innerAlpha < edgeAlpha, "inverted falloff on edge " + side);
                    fading++;
                }
            }
            assertTrue(fading > 40, "missing lit cells on edge " + side);
        }
    }

    @Test void physicalTopAndBottomPeaksEmitNonzeroEvenWithHudAndPanel() {
        for (int[] size : new int[][]{{320, 180}, {854, 480}}) {
            int width = size[0], height = size[1];
            var panel = LocalSpeechReviewOverlay.layout(width, height, true);
            for (boolean top : List.of(true, false)) {
                var point = new DirectionalCueGeometry.Point(width / 2.0, top ? 0 : height,
                        false, 0, false, !top);
                List<Vertex> vertices = new ArrayList<>();
                DirectionalCueTintMesh.emit(width, height, point, 1, 1, Mode.WHISPER, false, panel,
                        (x, y, alpha, style) -> vertices.add(new Vertex(x, y, alpha)));
                double edgePeak = vertices.stream().filter(v -> v.x() == width / 2.0
                        && v.y() == (top ? 0 : height)).mapToDouble(Vertex::alpha).max().orElse(0);
                assertTrue(edgePeak > 0,
                        "physical edge missing for " + width + "x" + height);
                assertEquals(edgePeak, vertices.stream().mapToDouble(Vertex::alpha).max().orElse(0), 1e-12,
                        "vertical peak moved off physical edge for " + width + "x" + height);
                assertTrue(vertices.stream().allMatch(v -> v.x() >= 0 && v.x() <= width
                        && v.y() >= 0 && v.y() <= height));
            }
        }
    }

    @Test void diagonalSideCuesChoosePhysicalEdgeInNarrowAndWideWindows() {
        for (int[] size : new int[][]{{320, 180}, {854, 480}}) {
            int width = size[0], height = size[1];
            for (Mode mode : List.of(Mode.SAY, Mode.WHISPER)) for (boolean mention : List.of(false, true)) {
                for (boolean right : List.of(false, true)) {
                    double edge = right ? width : 0;
                    var point = new DirectionalCueGeometry.Point(right ? width - 16 : 16,
                            height - 60, false, 0, false, false);
                    List<Vertex> vertices = new ArrayList<>();
                    List<DirectionalCueTintMesh.Style> styles = new ArrayList<>();
                    DirectionalCueTintMesh.emit(width, height, point, 1, 1, mode, mention, null,
                            (x, y, alpha, style) -> {
                                vertices.add(new Vertex(x, y, alpha));
                                styles.add(style);
                            });
                    String context = width + "x" + height + " " + mode + " mention=" + mention + " right=" + right;
                    double edgePeak = vertices.stream().filter(v -> v.x() == edge && v.y() == point.y())
                            .mapToDouble(Vertex::alpha).max().orElse(0);
                    assertTrue(edgePeak > 0, "missing side-edge peak: " + context);
                    assertEquals(edgePeak, vertices.stream().mapToDouble(Vertex::alpha).max().orElse(0), 1e-12,
                            "peak moved away from side edge: " + context);
                    assertTrue(vertices.stream().filter(v -> v.y() == height).mapToDouble(Vertex::alpha)
                            .max().orElse(0) < edgePeak, "diagonal side cue peaks on bottom: " + context);
                    assertTrue(vertices.stream().allMatch(v -> v.x() >= 0 && v.x() <= width
                            && v.y() >= 0 && v.y() <= height), "out-of-bounds vertex: " + context);
                    var style = styles.getFirst();
                    if (mention) assertTrue(style.red() > style.blue(), context);
                    else assertTrue(style.blue() > style.red(), context);
                    if (mode == Mode.WHISPER && !mention)
                        assertTrue(style.span() < DirectionalCueTintMesh.style(Mode.SAY, false, 1).span(), context);
                }
            }
        }
    }

    @Test void emittedQuadsStayWithinGuiAtCorners() {
        for (double x : List.of(16.0, 838.0)) for (double y : List.of(18.0, 418.0)) {
            List<Vertex> vertices = new ArrayList<>();
            DirectionalCueTintMesh.emit(854, 480,
                     new DirectionalCueGeometry.Point(x, y, false, 0, false, false), 1, 1,
                    Mode.SAY, false, new LocalSpeechReviewOverlay.Layout(-200, -200, -100, -100, 1),
                    (vx, vy, alpha, style) -> vertices.add(new Vertex(vx, vy, alpha)));
            assertFalse(vertices.isEmpty());
            for (Vertex vertex : vertices) {
                assertTrue(vertex.x() >= 0 && vertex.x() <= 854);
                assertTrue(vertex.y() >= 0 && vertex.y() <= 480);
            }
        }
    }

    @Test void alphaFeathersInwardAndLaterallyWithoutOpaqueBars() {
        var style = DirectionalCueTintMesh.style(Mode.SAY, false, 1);
        assertTrue(style.depth() > 70 && style.span() > 100);
        assertTrue(DirectionalCueTintMesh.alpha(style, 0, 0) < .65);
        assertTrue(DirectionalCueTintMesh.alpha(style, 0, 0)
                > DirectionalCueTintMesh.alpha(style, style.depth() / 2, 0));
        assertTrue(DirectionalCueTintMesh.alpha(style, 0, 0)
                > DirectionalCueTintMesh.alpha(style, 0, style.span() / 2));
        assertEquals(0, DirectionalCueTintMesh.alpha(style, style.depth(), 0));
        assertEquals(0, DirectionalCueTintMesh.alpha(style, 0, style.span()));
    }

    @Test void nearIsStrongerAndDeeperWhisperNarrowerButDeeperMentionAmber() {
        var far = DirectionalCueTintMesh.style(Mode.SAY, false, .08);
        var near = DirectionalCueTintMesh.style(Mode.SAY, false, 1);
        var whisper = DirectionalCueTintMesh.style(Mode.WHISPER, false, 1);
        var mention = DirectionalCueTintMesh.style(Mode.SAY, true, 1);
        assertTrue(near.depth() > far.depth());
        assertTrue(near.peak() > far.peak());
        assertTrue(whisper.depth() > near.depth());
        assertTrue(whisper.span() < near.span());
        assertTrue(whisper.peak() > near.peak());
        assertTrue(DirectionalCueTintMesh.alpha(whisper, 0, whisper.span() / 2)
                / whisper.peak() < DirectionalCueTintMesh.alpha(near, 0, near.span() / 2) / near.peak());
        assertTrue(mention.red() > mention.green() && mention.green() > mention.blue());
        assertEquals(near.peak(), mention.peak());
        assertTrue(mention.green() >= 200);
    }

    @Test void independentSmoothRangeCurvesReachMinimumOnlyAtTheirFarEdge() {
        for (Mode mode : List.of(Mode.SAY, Mode.SHOUT, Mode.WHISPER, Mode.W_MUFFLED)) {
            double[] distances = mode == Mode.SAY || mode == Mode.SHOUT
                    ? new double[]{0, 1, 2, 3, 5, 10, 14, 15}
                    : new double[]{0, 1, 2, 2.99, 3, 3.01, 4.5, 5.9, 6};
            double previous = Double.POSITIVE_INFINITY;
            for (double distance : distances) {
                double value = DirectionalCueOverlay.distanceIntensity(mode, distance);
                assertTrue(value < previous, mode + " at " + distance);
                assertTrue(value > .08 || distance == distances[distances.length - 1]);
                previous = value;
            }
            assertEquals(1, DirectionalCueOverlay.distanceIntensity(mode, 0));
            assertEquals(.08, previous, 1e-12);
            assertEquals(1, DirectionalCueOverlay.distanceIntensity(mode, -100));
            assertEquals(.08, DirectionalCueOverlay.distanceIntensity(mode, Double.POSITIVE_INFINITY));
            assertEquals(1, DirectionalCueOverlay.distanceIntensity(mode, Double.NEGATIVE_INFINITY));
            assertEquals(.08, DirectionalCueOverlay.distanceIntensity(mode, Double.NaN));
        }
        assertEquals(DirectionalCueOverlay.distanceIntensity(3),
                DirectionalCueOverlay.distanceIntensity(Mode.SHOUT, 3));
        for (double distance : new double[]{0, 1, 2, 2.99, 3, 3.01, 4.5, 5.9, 6})
            assertEquals(DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, distance),
                    DirectionalCueOverlay.distanceIntensity(Mode.W_MUFFLED, distance));
        double before = DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 2.99);
        double after = DirectionalCueOverlay.distanceIntensity(Mode.W_MUFFLED, 3.01);
        assertTrue(before - after < .01, "tracked whisper must not jump across the 3m boundary");
        double leftSlope = before - DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 3);
        double rightSlope = DirectionalCueOverlay.distanceIntensity(Mode.W_MUFFLED, 3) - after;
        assertEquals(leftSlope, rightSlope, .0001);
        assertTrue(DirectionalCueOverlay.distanceIntensity(Mode.SAY, 2)
                - DirectionalCueOverlay.distanceIntensity(Mode.SAY, 3) < .1);
        assertTrue(DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 2) > .7);
        assertTrue(DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 5.9) < .09);
        assertNotEquals(DirectionalCueOverlay.distanceIntensity(Mode.SAY, 5),
                DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 2));
        assertNotEquals(DirectionalCueOverlay.distanceIntensity(Mode.SAY, 10),
                DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 4));
        assertTrue(DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 2)
                > DirectionalCueOverlay.distanceIntensity(Mode.SAY, 5),
                "near whisper should be stronger than equally normalized speech");
        assertTrue(DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 4)
                < DirectionalCueOverlay.distanceIntensity(Mode.SAY, 10),
                "far whisper should fade more than equally normalized speech");
    }

    @Test void closeRearWhisperHasBrightEdgeAndLongNarrowInwardSpikeButFarStaysSubtle() {
        int width = 854, height = 480;
        var rear = new DirectionalCueGeometry.Point(width / 2.0, height, false, 2, false, true);
        double close = DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 1);
        double far = DirectionalCueOverlay.distanceIntensity(Mode.WHISPER, 6);
        var closeStyle = DirectionalCueTintMesh.style(Mode.WHISPER, false, close);
        var farStyle = DirectionalCueTintMesh.style(Mode.WHISPER, false, far);
        var sayStyle = DirectionalCueTintMesh.style(Mode.SAY, false, close);
        assertTrue(closeStyle.peak() < .95);
        assertTrue(closeStyle.peak() > sayStyle.peak());
        assertTrue(farStyle.peak() < .16);
        assertTrue(closeStyle.lateralPower() >= 5);
        assertTrue(closeStyle.span() < sayStyle.span());
        assertTrue(DirectionalCueTintMesh.alpha(closeStyle, 65, 0) > .3);
        assertTrue(DirectionalCueTintMesh.alpha(closeStyle, 0, closeStyle.span() / 2)
                < DirectionalCueTintMesh.alpha(sayStyle, 0, sayStyle.span() / 2));

        List<Vertex> nearVertices = new ArrayList<>(), farVertices = new ArrayList<>();
        DirectionalCueTintMesh.emit(width, height, rear, close, 1, Mode.WHISPER, false, null,
                (x, y, alpha, style) -> nearVertices.add(new Vertex(x, y, alpha)));
        DirectionalCueTintMesh.emit(width, height, rear, far, 1, Mode.WHISPER, false, null,
                (x, y, alpha, style) -> farVertices.add(new Vertex(x, y, alpha)));
        double edge = centerAlpha(nearVertices, width / 2.0, height);
        double farEdge = centerAlpha(farVertices, width / 2.0, height);
        assertTrue(edge >= .7 && edge < .95, "close physical rear edge should be bright but bounded");
        assertTrue(farEdge < .25 && farEdge < edge / 2, "far whisper should remain subdued");
        assertEquals(closeStyle.peak() * .86, edge, 1e-12);
        Vertex inward = nearVertices.stream().filter(v -> v.x() == width / 2.0
                        && v.y() <= height - 18 && v.y() >= height - 24)
                .findFirst().orElseThrow();
        assertEquals(DirectionalCueTintMesh.alpha(closeStyle, height - inward.y(), 0), inward.alpha(), 1e-12,
                "strip attenuation must not extend into the inward plume");
        assertTrue(inward.alpha() > .6);
        assertTrue(nearVertices.stream().anyMatch(v -> v.x() == width / 2.0
                && v.y() <= height - 55 && v.y() >= height - 80 && v.alpha() > .3));
        assertEquals(edge, nearVertices.stream().mapToDouble(Vertex::alpha).max().orElseThrow(), 1e-12);
        assertTrue(nearVertices.stream().allMatch(v -> v.alpha() >= 0 && v.alpha() < .85));

        // Existing panel tint remains attenuated but does not erase the rear spike.
        List<Vertex> panelVertices = new ArrayList<>();
        DirectionalCueTintMesh.emit(width, height, rear, close, 1, Mode.WHISPER, false,
                LocalSpeechReviewOverlay.layout(width, height, true),
                (x, y, alpha, style) -> panelVertices.add(new Vertex(x, y, alpha)));
        assertTrue(panelVertices.stream().anyMatch(v -> v.x() == width / 2.0
                && v.y() < height - 18 && v.alpha() > 0));
    }

    private static double centerAlpha(List<Vertex> vertices, double x, double y) {
        return vertices.stream().filter(v -> v.x() == x && v.y() == y)
                .mapToDouble(Vertex::alpha).max().orElseThrow();
    }

    @Test void everyWhisperRemainsThinAndSharpEvenWhenFaint() {
        for (double strength : new double[]{.08, .2, .5, .8, 1}) {
            var say = DirectionalCueTintMesh.style(Mode.SAY, false, strength);
            var clear = DirectionalCueTintMesh.style(Mode.WHISPER, false, strength);
            var muffled = DirectionalCueTintMesh.style(Mode.W_MUFFLED, false, strength);
            assertEquals(clear, muffled);
            assertTrue(clear.span() < say.span() / 2, "whisper must be less than half SAY width at " + strength);
            assertTrue(clear.lateralPower() > say.lateralPower());
            assertTrue(DirectionalCueTintMesh.alpha(clear, 0, clear.span() / 2) / clear.peak()
                    < DirectionalCueTintMesh.alpha(say, 0, say.span() / 2) / say.peak());
        }
        var far = DirectionalCueTintMesh.style(Mode.WHISPER, false, .08);
        var near = DirectionalCueTintMesh.style(Mode.WHISPER, false, 1);
        assertTrue(near.depth() > far.depth());
        assertTrue(near.span() > far.span());
        assertTrue(near.peak() > far.peak() * 4);

        for (int[] size : new int[][]{{320, 180}, {854, 480}}) {
            int width = size[0], height = size[1];
            var point = new DirectionalCueGeometry.Point(width / 2.0, height, false, 0, false, true);
            var panel = new LocalSpeechReviewOverlay.Layout(-200, -200, -100, -100, 1);
            for (Mode mode : List.of(Mode.WHISPER, Mode.W_MUFFLED)) for (boolean mention : List.of(false, true)) {
                List<Vertex> vertices = new ArrayList<>();
                DirectionalCueTintMesh.emit(width, height, point, 1, 1, mode, mention, panel,
                        (x, y, alpha, style) -> vertices.add(new Vertex(x, y, alpha)));
                var peak = DirectionalCueTintMesh.style(mode, mention, 1).peak() * .86;
                double minX = vertices.stream().filter(v -> v.y() == height && v.alpha() >= peak / 2)
                        .mapToDouble(Vertex::x).min().orElseThrow();
                double maxX = vertices.stream().filter(v -> v.y() == height && v.alpha() >= peak / 2)
                        .mapToDouble(Vertex::x).max().orElseThrow();
                assertTrue(maxX - minX < DirectionalCueTintMesh.style(Mode.SAY, false, 1).span(),
                        "near emitted whisper must remain thin at " + width + "x" + height + " " + mode);
            }
        }
    }

    @Test void mentionOnlyChangesRgbIncludingCenterHalo() {
        var edge = new DirectionalCueGeometry.Point(16, 240, false, 0, false, false);
        var halo = new DirectionalCueGeometry.Point(427, 226, true, 0, true, false);
        var panel = LocalSpeechReviewOverlay.layout(854, 480, true);
        for (Mode mode : Mode.values()) for (double strength : new double[]{.08, .5, .95}) {
            var normal = DirectionalCueTintMesh.style(mode, false, strength);
            var mentioned = DirectionalCueTintMesh.style(mode, true, strength);
            assertEquals(normal.depth(), mentioned.depth());
            assertEquals(normal.span(), mentioned.span());
            assertEquals(normal.peak(), mentioned.peak());
            assertEquals(normal.lateralPower(), mentioned.lateralPower());
            assertTrue(mentioned.red() > mentioned.blue());
            assertNotEquals(normal.red(), mentioned.red());
            for (var point : List.of(edge, halo)) {
                List<Vertex> genericVertices = new ArrayList<>(), mentionVertices = new ArrayList<>();
                DirectionalCueTintMesh.emit(854, 480, point, strength, .7, mode, false, panel,
                        (x, y, alpha, style) -> genericVertices.add(new Vertex(x, y, alpha)));
                DirectionalCueTintMesh.emit(854, 480, point, strength, .7, mode, true, panel,
                        (x, y, alpha, style) -> mentionVertices.add(new Vertex(x, y, alpha)));
                assertFalse(genericVertices.isEmpty());
                assertEquals(genericVertices, mentionVertices, mode + " " + point);
            }
        }
    }

    @Test void uuidBoundMuffledTierFallbackAndFiniteLifetime() {
        assertTrue(DirectionalCueOverlay.fallbackIntensity(DistanceTier.NEAR)
                > DirectionalCueOverlay.fallbackIntensity(DistanceTier.FAR));
        assertEquals(DirectionalCueOverlay.distanceIntensity(Mode.W_MUFFLED, 3.75),
                DirectionalCueOverlay.fallbackIntensity(DistanceTier.NEAR));
        assertEquals(DirectionalCueOverlay.distanceIntensity(Mode.W_MUFFLED, 5.25),
                DirectionalCueOverlay.fallbackIntensity(DistanceTier.FAR));
        assertTrue(DirectionalCueOverlay.fallbackIntensity(DistanceTier.NEAR) < .70);
        assertTrue(DirectionalCueOverlay.fallbackIntensity(DistanceTier.FAR) < .25);
        DirectionalCueOverlay.clear();
        try {
            UUID uuid = UUID.randomUUID();
            DirectionalCueOverlay.enqueueMuffled(1, uuid, 4, 1, DistanceTier.NEAR, 1, 1000);
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 4999));
            assertEquals(0, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 5000));
            DirectionalCueOverlay.enqueueMuffled(1, uuid, 4, 1, DistanceTier.FAR, Long.MAX_VALUE, 6000);
            assertEquals(1, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 15999));
            assertEquals(0, DirectionalCueOverlay.selected(PROJECTION, new Quaternionf(), 854, 480, 16000));
            DirectionalCueOverlay.enqueueClear(2, UUID.randomUUID(), new Vec3(9, 0, -5), false,
                    false, Mode.WHISPER, 5000, 17000);
            assertEquals(1, DirectionalCueOverlay.queued());
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test void onlyMatchingUuidFollowsMovingSpeakerAndRearRemainsBottom() {
        UUID bound = UUID.randomUUID();
        Vec3 before = new Vec3(5, 0, -4), after = new Vec3(-6, 0, -4);
        assertEquals(before, DirectionalCueOverlay.matchingPosition(bound, bound, before));
        assertEquals(after, DirectionalCueOverlay.matchingPosition(bound, bound, after));
        assertNull(DirectionalCueOverlay.matchingPosition(bound, UUID.randomUUID(), after));
        var rear = DirectionalCueGeometry.project(new Vec3(0, 0, 5), new Quaternionf(),
                PROJECTION, 854, 480, false);
        var above = DirectionalCueGeometry.project(new Vec3(0, 1, 0), new Quaternionf(),
                PROJECTION, 854, 480, true);
        assertTrue(rear.rear());
        assertTrue(rear.y() > above.y());
    }
}
