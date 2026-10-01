package com.juicyslew.moonstation14.ms14.explosion.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen observations of the bounded, pure 3D reference fixture; not a gameplay balance test. */
class BlastReferenceBaselineTest {
    private static final BlastPlanner.Pos ORIGIN = new BlastPlanner.Pos(0, 0, 0);
    private static final BlastPlanner.Proposal EX_GRENADE = new BlastPlanner.Proposal(120, 3, 10);

    @Test
    void openVolumeBaselineIsSymmetricAndBounded() {
        Set<BlastPlanner.Pos> sampled = new HashSet<>();
        var plan = BlastPlanner.plan(ORIGIN, EX_GRENADE, p -> {
            sampled.add(p);
            return BlastPlanner.Cell.OPEN;
        });
        assertEquals(BlastPlanner.Status.COMPLETE, plan.status());
        assertEquals(plan.visited(), plan.inspected());
        assertEquals(plan.visited(), sampled.size());
        assertEquals(25, plan.visited());
        assertEquals(25, plan.intensities().size());
        assertEquals(6, plan.expansionIterations());
        assertTrue(plan.visited() <= BlastPlanner.MAX_VISITED);
        assertTrue(plan.expansionIterations() <= BlastPlanner.MAX_EXPANSION_ITERATIONS);
        assertTrue(plan.intensityUsed() <= 120.0 + 1e-9);

        int maxManhattan = plan.intensities().keySet().stream()
                .mapToInt(p -> Math.abs(p.x()) + Math.abs(p.y()) + Math.abs(p.z())).max().orElseThrow();
        int maxX = plan.intensities().keySet().stream().mapToInt(p -> Math.abs(p.x())).max().orElseThrow();
        int maxY = plan.intensities().keySet().stream().mapToInt(p -> Math.abs(p.y())).max().orElseThrow();
        int maxZ = plan.intensities().keySet().stream().mapToInt(p -> Math.abs(p.z())).max().orElseThrow();
        assertEquals(2, maxManhattan);
        assertEquals(2, maxX);
        assertEquals(2, maxY);
        assertEquals(2, maxZ);
        assertEquals(10.0, plan.intensities().get(ORIGIN), 1e-12);
        for (var p : plan.intensities().keySet()) {
            int radius = Math.abs(p.x()) + Math.abs(p.y()) + Math.abs(p.z());
            if (radius == 1) assertEquals(7.5, plan.intensities().get(p), 1e-12);
            if (radius == 2) assertEquals(3.611111111111, plan.intensities().get(p), 1e-9);
        }
        System.out.printf("OPEN status=%s visited=%d inspected=%d affected=%d span=%d extents=%d,%d,%d used=%.12f iterations=%d%n",
                plan.status(), plan.visited(), plan.inspected(), plan.intensities().size(), maxManhattan,
                maxX, maxY, maxZ, plan.intensityUsed(), plan.expansionIterations());
        System.out.println("OPEN center=" + plan.intensities().get(ORIGIN));
        for (int radius = 1; radius <= maxManhattan; radius++) {
            final int r = radius;
            var ring = plan.intensities().entrySet().stream().filter(e ->
                    Math.abs(e.getKey().x()) + Math.abs(e.getKey().y()) + Math.abs(e.getKey().z()) == r)
                    .mapToDouble(java.util.Map.Entry::getValue).toArray();
            if (ring.length > 0) System.out.printf("OPEN ring%d count=%d min=%.12f max=%.12f%n",
                    radius, ring.length, java.util.Arrays.stream(ring).min().orElseThrow(),
                    java.util.Arrays.stream(ring).max().orElseThrow());
        }
        for (var e : plan.intensities().entrySet()) {
            var p = e.getKey();
            assertEquals(e.getValue(), plan.intensities().get(new BlastPlanner.Pos(-p.x(), p.y(), p.z())), 1e-12);
            assertEquals(e.getValue(), plan.intensities().get(new BlastPlanner.Pos(p.x(), -p.y(), p.z())), 1e-12);
            assertEquals(e.getValue(), plan.intensities().get(new BlastPlanner.Pos(p.x(), p.y(), -p.z())), 1e-12);
        }
        assertEquals(plan.intensities().values().stream().mapToDouble(Double::doubleValue).sum(), plan.intensityUsed(), 1e-9);
        assertEquals(plan.visited(), plan.intensities().size());
    }

    @Test
    void equivalentFloorWallCeilingCorridorsShareStructuralTolerance() {
        for (int axis = 0; axis < 3; axis++) {
            final int a = axis;
            var plan = BlastPlanner.planWithTolerance(ORIGIN, EX_GRENADE, p -> {
                if (p.equals(ORIGIN)) return BlastPlanner.Sample.open();
                int coordinate = a == 0 ? p.x() : a == 1 ? p.y() : p.z();
                if (coordinate == 1) return BlastPlanner.Sample.solid(2.0);
                return BlastPlanner.Sample.open();
            });
            assertEquals(BlastPlanner.Status.COMPLETE, plan.status());
            assertEquals(25, plan.visited());
            assertEquals(25, plan.intensities().size());
            assertEquals(5, plan.breached().size());
            assertEquals(120.0, plan.intensityUsed(), 1e-9);
            assertTrue(plan.intensities().get(axis == 0 ? new BlastPlanner.Pos(1,0,0)
                    : axis == 1 ? new BlastPlanner.Pos(0,1,0) : new BlastPlanner.Pos(0,0,1)) >= 2.0);
            assertTrue(plan.breached().contains(axis == 0 ? new BlastPlanner.Pos(1,0,0)
                    : axis == 1 ? new BlastPlanner.Pos(0,1,0) : new BlastPlanner.Pos(0,0,1)));
            System.out.printf("CORRIDOR axis=%d status=%s inspected=%d visited=%d affected=%d used=%.12f breached=%d%n",
                    axis, plan.status(), plan.inspected(), plan.visited(), plan.intensities().size(),
                    plan.intensityUsed(), plan.breached().size());
            if (axis == 0) assertEquals(plan, BlastPlanner.planWithTolerance(ORIGIN, EX_GRENADE, p -> {
                if (p.equals(ORIGIN)) return BlastPlanner.Sample.open();
                if (p.x() == 1) return BlastPlanner.Sample.solid(2.0);
                return BlastPlanner.Sample.open();
            }));
        }
    }

    @Test
    void optionalPlanarSamplerIsExplicitlyOnlyPlannerComparison() {
        var plan = BlastPlanner.plan(ORIGIN, EX_GRENADE, p -> p.y() == 0
                ? BlastPlanner.Cell.OPEN : BlastPlanner.Cell.UNKNOWN);
        System.out.printf("PLANAR status=%s inspected=%d visited=%d affected=%d used=%.12f iterations=%d%n",
                plan.status(), plan.inspected(), plan.visited(), plan.intensities().size(),
                plan.intensityUsed(), plan.expansionIterations());
        assertTrue(plan.inspected() <= BlastPlanner.MAX_VISITED);
        assertEquals(BlastPlanner.Status.COMPLETE, plan.status());
        assertEquals(51, plan.inspected());
        assertEquals(51, plan.visited());
        assertEquals(25, plan.intensities().size());
        assertEquals(7, plan.expansionIterations());
        assertTrue(plan.intensityUsed() <= 120.0 + 1e-9);
        assertTrue(plan.intensities().keySet().stream().allMatch(p -> p.y() == 0));
    }
}
