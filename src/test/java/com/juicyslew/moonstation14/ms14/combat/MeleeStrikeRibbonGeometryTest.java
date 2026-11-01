package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.combat.client.MeleeStrikeRibbonGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MeleeStrikeRibbonGeometryTest {
    private static List<Vec3> build(Vec3 origin, Vec3 tip, Vec3 clipped, double yaw, double roll, double width) {
        return MeleeStrikeRibbonGeometry.build(origin, tip, clipped, yaw, roll, width);
    }

    @Test void crescentIsTaperedCurvedAndBoundedByTipPlane() {
        Vec3 tip = new Vec3(0, 0, 2);
        List<Vec3> mesh = build(Vec3.ZERO, tip, new Vec3(0, 0, 4), 0, 0, .2);
        assertEquals(56, mesh.size());
        assertTrue(mesh.stream().allMatch(v -> Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)));
        assertTrue(mesh.stream().mapToDouble(v -> v.subtract(tip).dot(new Vec3(0, 0, 1))).max().orElseThrow() <= 1e-9);
        double[] widths = new double[14];
        for (int i = 0; i < 14; i++) widths[i] = mesh.get(i * 4).distanceTo(mesh.get(i * 4 + 1));
        assertTrue(widths[0] < widths[7] && widths[13] < widths[7]);
        assertTrue(mesh.stream().map(Vec3::x).distinct().count() > 4, "not a rectangular four-corner plate");
    }

    @Test void orientationAndTranslationAreSupportedAndEarlyStrokeKeepsItsTransverseProfile() {
        for (Vec3 tip : List.of(new Vec3(0, 0, 2), new Vec3(0, 2, 0), new Vec3(1, 2, 2))) {
            List<Vec3> mesh = build(Vec3.ZERO, tip, tip.scale(2), 37, .4, .2);
            assertFalse(mesh.isEmpty());
            assertTrue(mesh.stream().allMatch(v -> Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)));
        }
        List<Vec3> early = build(Vec3.ZERO, new Vec3(0, 0, .2), new Vec3(0, 0, 4), 0, 0, .2);
        List<Vec3> late = build(Vec3.ZERO, new Vec3(0, 0, 2), new Vec3(0, 0, 4), 0, 0, .2);
        Vec3 axis = new Vec3(0, 0, 1);
        assertEquals(transverseExtent(late, Vec3.ZERO, axis), transverseExtent(early, Vec3.ZERO, axis), 1e-8);
        assertTrue(axialExtent(early, Vec3.ZERO, axis) < axialExtent(late, Vec3.ZERO, axis));
        Vec3 shift = new Vec3(12, -3, 8);
        List<Vec3> shifted = build(shift, shift.add(0, 0, 2), shift.add(0, 0, 4), 0, 0, .2);
        for (int i = 0; i < late.size(); i++) assertEquals(shift.distanceTo(Vec3.ZERO), shifted.get(i).distanceTo(late.get(i)), 1e-8);
    }

    @Test void everyVertexIsClampedToTravelStationsAcrossDirectionsAndProfiles() {
        Vec3 shift = new Vec3(12, -3, 8);
        for (Vec3 direction : List.of(new Vec3(0, 0, 1), new Vec3(1, 0, 0),
                new Vec3(.70710678, 0, .70710678), new Vec3(0, 1, 0),
                new Vec3(-.70710678, 0, .70710678))) {
            for (double progress : List.of(.01, .1, .2, 1.0)) {
                Vec3 tip = shift.add(direction.scale(2 * progress));
                Vec3 clipped = shift.add(direction.scale(4));
                Vec3 axis = direction.normalize();
                for (double roll : List.of(0.0, Math.PI / 4, Math.PI / 2, Math.PI * 1.25)) {
                    for (boolean counterTilt : List.of(false, true)) {
                        List<Vec3> mesh = MeleeStrikeRibbonGeometry.build(shift, tip, clipped, 27,
                                roll, .2, counterTilt);
                        assertEquals(56, mesh.size());
                        for (Vec3 vertex : mesh) {
                            double station = vertex.subtract(shift).dot(axis);
                            assertTrue(station >= -1e-9, "vertex behind origin station: " + station);
                            assertTrue(station <= 2 * progress + 1e-9, "vertex beyond tip station: " + station);
                        }
                    }
                }
            }
        }
    }

    @Test void degenerateTravelIsEmpty() {
        assertTrue(build(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 2), 0, 0, .2).isEmpty());
    }

    @Test void zeroRoomClippingIsEmptyAndCounterTiltRemainsFinite() {
        Vec3 origin = Vec3.ZERO;
        Vec3 tip = new Vec3(0, 0, .2);
        assertTrue(build(origin, tip, origin, 0, 0, .2).isEmpty());
        List<Vec3> mesh = MeleeStrikeRibbonGeometry.build(origin, tip, new Vec3(0, 0, 2),
                37, .4, .2, true);
        assertFalse(mesh.isEmpty());
        assertTrue(mesh.stream().allMatch(v -> Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)));
    }

    @Test void clippedRadiusHasFloorInterpolationAndMaximumWithoutChangingAxialStations() {
        Vec3 origin = Vec3.ZERO;
        Vec3 axis = new Vec3(0, 0, 1);
        Vec3 shortTip = new Vec3(0, 0, .08);
        Vec3 farTip = new Vec3(0, 0, 1);
        List<Vec3> clipped = build(origin, shortTip, new Vec3(0, 0, .12), 0, 0, .2);
        List<Vec3> full = build(origin, farTip, new Vec3(0, 0, 2), 0, 0, .2);
        assertEquals(.3, transverseExtent(clipped, origin, axis), 1e-8);
        assertEquals(.6, transverseExtent(full, origin, axis), 1e-8);
        for (double clippedLength : List.of(.5, 1.0, 1.5)) {
            List<Vec3> mesh = build(origin, new Vec3(0, 0, 1), new Vec3(0, 0, clippedLength), 0, 0, .2);
            assertEquals(Math.min(.6, Math.max(.3, clippedLength * .75)), transverseExtent(mesh, origin, axis), 1e-8);
        }
        assertTrue(clipped.stream().anyMatch(v -> Math.abs(v.x) > 1e-6));
        for (var entry : List.of(new Object[]{clipped, .08}, new Object[]{full, 1.0})) {
            @SuppressWarnings("unchecked") List<Vec3> mesh = (List<Vec3>) entry[0];
            double tipStation = (double) entry[1];
            assertTrue(mesh.stream().allMatch(v -> {
                double station = v.subtract(origin).dot(axis);
                return station >= -1e-9 && station <= tipStation + 1e-9;
            }));
        }
    }

    @Test void clippedRibbonIsCenteredAtAdvancingTip() {
        for (double tipDistance : List.of(1.0, 2.0)) {
            Vec3 tip = new Vec3(0, 0, tipDistance);
            List<Vec3> mesh = build(Vec3.ZERO, tip, new Vec3(0, 0, 2), 0, 0, .2);
            double radius = Math.min(.2 * 3, 2 * .22);
            for (Vec3 vertex : mesh) {
                double station = vertex.subtract(Vec3.ZERO).dot(new Vec3(0, 0, 1));
                assertTrue(station >= -1e-9);
                assertTrue(station <= tipDistance + 1e-9);
            }
            assertTrue(mesh.stream().mapToDouble(v -> v.z).average().orElseThrow() > tipDistance - 2 * radius);
        }
        List<Vec3> first = build(Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, 2), 0, 0, .2);
        List<Vec3> second = build(Vec3.ZERO, new Vec3(0, 0, 2), new Vec3(0, 0, 2), 0, 0, .2);
        assertTrue(centroid(second).z > centroid(first).z);
        assertTrue(centroid(first).z > 0, "ribbon must not remain centered at origin");
    }

    @Test void yawBasisRotatesCovariantlyIncludingFrozenVerticalYaw() {
        Vec3 tip = new Vec3(0, 0, 2);
        List<Vec3> atZero = build(Vec3.ZERO, tip, new Vec3(0, 0, 4), 0, 0, .2);
        List<Vec3> atNinety = build(Vec3.ZERO, new Vec3(-2, 0, 0), new Vec3(-4, 0, 0), 90, 0, .2);
        for (int i = 0; i < atZero.size(); i++) {
            Vec3 v = atZero.get(i);
            assertEquals(-v.z, atNinety.get(i).x, 1e-8);
            assertEquals(v.y, atNinety.get(i).y, 1e-8);
            assertEquals(v.x, atNinety.get(i).z, 1e-8);
        }
        List<Vec3> verticalZero = build(Vec3.ZERO, new Vec3(0, 2, 0), new Vec3(0, 4, 0), 0, 0, .2);
        List<Vec3> verticalNinety = build(Vec3.ZERO, new Vec3(0, 2, 0), new Vec3(0, 4, 0), 90, 0, .2);
        assertNotEquals(verticalZero.get(0).x, verticalNinety.get(0).x, 1e-8);
        assertTrue(verticalZero.stream().allMatch(v -> Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)));
        assertTrue(verticalNinety.stream().allMatch(v -> Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)));
    }

    @Test void capturedObliqueCloseStrikeSupportsCounterTiltWithoutLosingBounds() {
        Vec3 origin = Vec3.ZERO;
        Vec3 tip = new Vec3(.866, 0, .5);
        Vec3 clipped = tip.scale(2);
        double bestPairedCameraFacing = 0;
        double[] cameraFacingByTilt = new double[2];
        int tiltIndex = 0;
        for (double roll : List.of(35, 125)) {
            boolean counterTilt = roll == 125;
            List<Vec3> mesh = MeleeStrikeRibbonGeometry.build(origin, tip, clipped, -60,
                    Math.toRadians(roll), .2, counterTilt);
            assertEquals(56, mesh.size());
            assertTrue(mesh.stream().allMatch(v -> Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)));
            assertTrue(mesh.stream().anyMatch(v -> Math.abs(v.y) > 1e-4));
            assertTrue(mesh.stream().anyMatch(v -> Math.abs(v.x) > 1e-4));
            Vec3 axis = tip.normalize();
            assertTrue(mesh.stream().allMatch(v -> v.subtract(tip).dot(axis) <= 1e-9), "vertex beyond tip clip plane");
            assertTrue(mesh.stream().allMatch(v -> v.subtract(origin).dot(axis) >= -1e-9), "vertex behind leading station");
            double bestCameraFacing = 0;
            int nondegenerateFaces = 0;
            for (int i = 0; i < mesh.size(); i += 4) {
                Vec3 area = mesh.get(i + 1).subtract(mesh.get(i))
                        .cross(mesh.get(i + 2).subtract(mesh.get(i)));
                // The taper deliberately closes to a point at each arc endpoint. Those
                // zero-area end quads have no defined rendered face normal.
                if (area.lengthSqr() < 1.0e-12) continue;
                nondegenerateFaces++;
                Vec3 normal = area.normalize();
                bestCameraFacing = Math.max(bestCameraFacing, Math.abs(normal.z));
            }
            assertTrue(nondegenerateFaces > 0, "ribbon has no nondegenerate rendered faces");
            cameraFacingByTilt[tiltIndex++] = bestCameraFacing;
            bestPairedCameraFacing = Math.max(bestPairedCameraFacing, bestCameraFacing);
        }
        assertTrue(bestPairedCameraFacing >= .5,
                "paired main/counter-tilt ribbons lack camera-normal coverage: "
                        + java.util.Arrays.toString(cameraFacingByTilt));
    }

    private static Vec3 centroid(List<Vec3> points) {
        return points.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / points.size());
    }

    private static double extent(List<Vec3> points) {
        return points.stream().mapToDouble(Vec3.ZERO::distanceTo).max().orElse(0);
    }

    private static double axialExtent(List<Vec3> points, Vec3 origin, Vec3 axis) {
        double[] stations = points.stream().mapToDouble(v -> v.subtract(origin).dot(axis)).toArray();
        return java.util.Arrays.stream(stations).max().orElse(0) - java.util.Arrays.stream(stations).min().orElse(0);
    }

    private static double transverseExtent(List<Vec3> points, Vec3 origin, Vec3 axis) {
        return points.stream().mapToDouble(v -> {
            Vec3 relative = v.subtract(origin);
            return relative.subtract(axis.scale(relative.dot(axis))).length();
        }).max().orElse(0);
    }
}
