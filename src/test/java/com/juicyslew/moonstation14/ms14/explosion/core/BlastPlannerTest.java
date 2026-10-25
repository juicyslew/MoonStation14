package com.juicyslew.moonstation14.ms14.explosion.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlastPlannerTest {
    private static final BlastPlanner.Pos ORIGIN = new BlastPlanner.Pos(0, 0, 0);
    // Pinned SS14 ExGrenade reference values; UNCALIBRATED as a 3D radius or gameplay tuning.
    private static final BlastPlanner.Proposal REFERENCE = new BlastPlanner.Proposal(120, 3, 10);

    @Test
    void openVolumeReachesFloorWallAndCeilingSymmetricallyUnderOneSharedBudget() {
        var plan = BlastPlanner.plan(ORIGIN, REFERENCE, p -> BlastPlanner.Cell.OPEN);
        assertTrue(plan.successful());
        assertTrue(plan.intensities().containsKey(new BlastPlanner.Pos(1, 0, 0)));
        assertEquals(plan.intensities().get(new BlastPlanner.Pos(1, 0, 0)), plan.intensities().get(new BlastPlanner.Pos(-1, 0, 0)));
        assertEquals(plan.intensities().get(new BlastPlanner.Pos(0, 1, 0)), plan.intensities().get(new BlastPlanner.Pos(0, -1, 0)));
        assertEquals(plan.intensities().get(new BlastPlanner.Pos(0, 0, 1)), plan.intensities().get(new BlastPlanner.Pos(0, 0, -1)));
        assertTrue(plan.intensityUsed() <= 120);
        assertTrue(plan.visited() <= BlastPlanner.MAX_VISITED);
        assertTrue(plan.intensities().containsKey(new BlastPlanner.Pos(2, 0, 0)));
        for (var p : Set.of(new BlastPlanner.Pos(2,0,0), new BlastPlanner.Pos(-2,0,0),
                new BlastPlanner.Pos(0,2,0), new BlastPlanner.Pos(0,-2,0),
                new BlastPlanner.Pos(0,0,2), new BlastPlanner.Pos(0,0,-2)))
            assertEquals(plan.intensities().get(new BlastPlanner.Pos(2,0,0)), plan.intensities().get(p));
    }

    @Test
    void solidSourceIsRejectedAndDiscoveryShellsFollowRoutes() {
        var solid = BlastPlanner.plan(ORIGIN, REFERENCE, p -> BlastPlanner.Cell.SOLID);
        assertEquals(BlastPlanner.Status.INVALID_SOURCE, solid.status());
        assertTrue(solid.intensities().isEmpty());

        // The wall forces the cell behind it to be discovered around a detour, not by Manhattan radius.
        var detour = BlastPlanner.plan(ORIGIN, new BlastPlanner.Proposal(120, 2, 10), p ->
                p.equals(new BlastPlanner.Pos(1, 0, 0)) ? BlastPlanner.Cell.SOLID : BlastPlanner.Cell.OPEN);
        assertNotEquals(BlastPlanner.Status.INVALID_SOURCE, detour.status());
        var wall = new BlastPlanner.Pos(1, 0, 0);
        assertTrue(detour.intensities().get(wall) > 0);
        assertTrue(detour.solids().contains(wall));
        assertTrue(detour.intensityUsed() <= 120);
        var sealed = BlastPlanner.plan(ORIGIN, REFERENCE, p -> p.x() == 1
                ? BlastPlanner.Cell.SOLID : BlastPlanner.Cell.OPEN);
        assertFalse(sealed.intensities().containsKey(new BlastPlanner.Pos(2, 0, 0)));
        assertTrue(sealed.intensityUsed() <= REFERENCE.totalIntensity());
    }

    @Test
    void unknownAndSolidStopPropagationAndSamplerCanRepresentLoadedSeam() {
        var unknown = BlastPlanner.plan(ORIGIN, REFERENCE, p -> p.equals(new BlastPlanner.Pos(1, 0, 0))
                ? BlastPlanner.Cell.UNKNOWN : BlastPlanner.Cell.OPEN);
        assertFalse(unknown.intensities().containsKey(new BlastPlanner.Pos(1, 0, 0)));
        var wall = BlastPlanner.plan(ORIGIN, REFERENCE, p -> p.x() == 1 ? BlastPlanner.Cell.SOLID : BlastPlanner.Cell.OPEN);
        assertTrue(wall.solids().contains(new BlastPlanner.Pos(1, 0, 0)));
        assertTrue(wall.intensities().get(new BlastPlanner.Pos(1, 0, 0)) > 0);
        var seam = BlastPlanner.plan(ORIGIN, REFERENCE, p -> BlastPlanner.Cell.OPEN);
        assertTrue(seam.intensities().containsKey(new BlastPlanner.Pos(0, 0, 1)));
    }

    @Test
    void reachedShellSharesFractionalBudgetAcrossOpenAndSolidSamples() {
        var plan = BlastPlanner.plan(ORIGIN, new BlastPlanner.Proposal(4, 2, 10), p ->
                p.equals(new BlastPlanner.Pos(1, 0, 0)) ? BlastPlanner.Cell.SOLID : BlastPlanner.Cell.OPEN);
        assertEquals(3.0, plan.intensities().get(ORIGIN), 1e-12);
        for (var neighbor : Set.of(new BlastPlanner.Pos(1, 0, 0), new BlastPlanner.Pos(-1, 0, 0),
                new BlastPlanner.Pos(0, 1, 0), new BlastPlanner.Pos(0, -1, 0),
                new BlastPlanner.Pos(0, 0, 1), new BlastPlanner.Pos(0, 0, -1)))
            assertEquals(1.0 / 6.0, plan.intensities().get(neighbor), 1e-12);
        assertTrue(plan.intensityUsed() <= 4);
    }

    @Test
    void cappedReachedShellDoesNotPhantomDebitSharedBudget() {
        var plan = BlastPlanner.plan(ORIGIN, new BlastPlanner.Proposal(2, 2, 0.25),
                p -> BlastPlanner.Cell.OPEN);
        assertTrue(plan.successful());
        assertEquals(2.0, plan.intensityUsed(), 1e-12);
        assertEquals(0.25, plan.intensities().get(ORIGIN), 1e-12);
        for (var neighbor : Set.of(new BlastPlanner.Pos(1, 0, 0), new BlastPlanner.Pos(-1, 0, 0),
                new BlastPlanner.Pos(0, 1, 0), new BlastPlanner.Pos(0, -1, 0),
                new BlastPlanner.Pos(0, 0, 1), new BlastPlanner.Pos(0, 0, -1)))
            assertEquals(0.25, plan.intensities().get(neighbor), 1e-12);
    }

    @Test
    void iterationCapReturnsNoPartialPlanWhenOpenFrontierRemains() {
        var plan = BlastPlanner.plan(ORIGIN, new BlastPlanner.Proposal(1_000_000, 2, 1), p ->
                p.x() >= 0 && p.y() == 0 && p.z() == 0 ? BlastPlanner.Cell.OPEN : BlastPlanner.Cell.UNKNOWN);
        assertEquals(BlastPlanner.Status.ITERATION_CAP, plan.status());
        assertTrue(plan.intensities().isEmpty());
        assertTrue(plan.solids().isEmpty());
        assertTrue(plan.breached().isEmpty());
        assertEquals(BlastPlanner.MAX_EXPANSION_ITERATIONS, plan.expansionIterations());
        assertTrue(plan.inspected() <= BlastPlanner.MAX_VISITED);
    }

    @Test
    void rejectsUnspendableSubEpsilonAndUnderflowingProposals() {
        assertThrows(IllegalArgumentException.class, () -> new BlastPlanner.Proposal(1, 1e-300, 1));
        assertThrows(IllegalArgumentException.class, () -> new BlastPlanner.Proposal(1, 2, 1e-13));
    }

    @Test
    void determinismAndNoDuplicateInflation() {
        var first = BlastPlanner.plan(ORIGIN, REFERENCE, p -> BlastPlanner.Cell.OPEN);
        var second = BlastPlanner.plan(ORIGIN, REFERENCE, p -> BlastPlanner.Cell.OPEN);
        assertEquals(first, second);
        assertTrue(first.intensities().size() <= first.visited());
    }

    @Test
    void toleranceSamplerBreachesOnlyAfterDamageAndThenExpands() {
        var wall = new BlastPlanner.Pos(1, 0, 0);
        var behind = new BlastPlanner.Pos(2, 0, 0);
        var proposal = new BlastPlanner.Proposal(120, 2, 10);
        var plan = BlastPlanner.planWithTolerance(ORIGIN, proposal, p -> p.equals(wall)
                ? BlastPlanner.Sample.solid(0.5) : BlastPlanner.Sample.open());
        assertTrue(plan.intensities().get(wall) >= 0.5);
        assertTrue(plan.breached().contains(wall));
        assertTrue(plan.intensities().containsKey(behind));
        assertTrue(plan.intensityUsed() <= proposal.totalIntensity());
    }

    @Test
    void breachedWallsReleaseOnLaterExpansionAcrossAllAxes() {
        for (int axis = 0; axis < 3; axis++) {
            final int direction = axis;
            var wall = along(direction, 1);
            var behind = along(direction, 2);
            var proposal = new BlastPlanner.Proposal(120, 2, 10);
            var plan = BlastPlanner.planWithTolerance(ORIGIN, proposal, p ->
                    p.equals(ORIGIN) || coordinate(p, direction) >= 2 ? BlastPlanner.Sample.open()
                            : p.equals(wall) ? BlastPlanner.Sample.solid(9) : BlastPlanner.Sample.unknown());
            assertTrue(plan.intensities().get(wall) >= 9);
            assertTrue(plan.breached().contains(wall));
            assertTrue(plan.intensities().containsKey(behind));
            var tooStrong = BlastPlanner.planWithTolerance(ORIGIN, proposal, p ->
                    p.equals(ORIGIN) || coordinate(p, direction) >= 2 ? BlastPlanner.Sample.open()
                            : p.equals(wall) ? BlastPlanner.Sample.solid(11) : BlastPlanner.Sample.unknown());
            assertFalse(tooStrong.intensities().containsKey(behind));
        }
    }

    @Test
    void consecutiveWallsBreachInSequenceAndUnknownNeverPropagates() {
        var proposal = new BlastPlanner.Proposal(120, 2, 10);
        var twoWalls = BlastPlanner.planWithTolerance(ORIGIN, proposal, p -> p.equals(ORIGIN)
                || p.x() >= 3 ? BlastPlanner.Sample.open()
                : p.x() == 1 || p.x() == 2 ? BlastPlanner.Sample.solid(2) : BlastPlanner.Sample.unknown());
        assertTrue(twoWalls.breached().contains(new BlastPlanner.Pos(1, 0, 0)));
        assertTrue(twoWalls.breached().contains(new BlastPlanner.Pos(2, 0, 0)));
        assertTrue(twoWalls.intensities().containsKey(new BlastPlanner.Pos(3, 0, 0)));
        var unknown = BlastPlanner.planWithTolerance(ORIGIN, proposal, p -> p.equals(ORIGIN)
                ? BlastPlanner.Sample.open() : p.x() == 1 ? BlastPlanner.Sample.unknown() : BlastPlanner.Sample.open());
        assertFalse(unknown.intensities().containsKey(new BlastPlanner.Pos(2, 0, 0)));
    }

    private static BlastPlanner.Pos along(int axis, int distance) {
        return axis == 0 ? new BlastPlanner.Pos(distance, 0, 0)
                : axis == 1 ? new BlastPlanner.Pos(0, distance, 0) : new BlastPlanner.Pos(0, 0, distance);
    }
    private static int coordinate(BlastPlanner.Pos p, int axis) {
        return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
    }

    @Test
    void immuneSolidsAndUnknownNeverOpenAndBreachIsImmutableDeterministic() {
        var wall = new BlastPlanner.Pos(1, 0, 0);
        var immune = BlastPlanner.planWithTolerance(ORIGIN, REFERENCE, p -> p.equals(wall)
                ? BlastPlanner.Sample.solid(Double.POSITIVE_INFINITY) : BlastPlanner.Sample.open());
        assertFalse(immune.breached().contains(wall));
        assertFalse(immune.intensities().containsKey(new BlastPlanner.Pos(2, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> immune.breached().add(ORIGIN));
        var first = BlastPlanner.planWithTolerance(ORIGIN, REFERENCE, p -> p.equals(wall)
                ? BlastPlanner.Sample.solid(1) : BlastPlanner.Sample.open());
        var second = BlastPlanner.planWithTolerance(ORIGIN, REFERENCE, p -> p.equals(wall)
                ? BlastPlanner.Sample.solid(1) : BlastPlanner.Sample.open());
        assertEquals(first, second);
    }

    @Test
    void capFailureExposesNoPartialPlanAndSmallBudgetIsSourceOnly() {
        var overflow = BlastPlanner.plan(ORIGIN, new BlastPlanner.Proposal(100_000, 0.01, 10), p -> BlastPlanner.Cell.OPEN);
        assertTrue(overflow.visited() <= BlastPlanner.MAX_VISITED);
        assertNotEquals(BlastPlanner.Status.COMPLETE, overflow.status());
        assertTrue(overflow.intensities().isEmpty());
        // A tiny proposal's budget is fully consumed at the source; no neighbor can receive an effect.
        var tiny = BlastPlanner.plan(ORIGIN, new BlastPlanner.Proposal(1, 3, 10), p -> BlastPlanner.Cell.OPEN);
        assertEquals(Set.of(ORIGIN), tiny.intensities().keySet());
        assertTrue(tiny.successful());
    }

    @Test
    void subStepBudgetReturnsOnlyEpicenterBeforeApplyingCap() {
        int[] samples = {0};
        var plan = BlastPlanner.planWithTolerance(ORIGIN, new BlastPlanner.Proposal(0.1, 1, 0.001), p -> {
            samples[0]++;
            return BlastPlanner.Sample.open();
        });
        assertTrue(plan.successful());
        assertEquals(Set.of(ORIGIN), plan.intensities().keySet());
        assertEquals(0.1, plan.intensities().get(ORIGIN), 0);
        assertEquals(0.1, plan.intensityUsed(), 0);
        assertEquals(1, samples[0]);
        assertEquals(0, plan.expansionIterations());
    }

    @Test
    void singleTileThresholdIsStrictAndFloatingPointConsistent() {
        for (double budget : new double[]{Math.nextDown(0.5), 0.5, Math.nextUp(0.5)}) {
            var plan = BlastPlanner.plan(ORIGIN, new BlastPlanner.Proposal(budget, 1, 0.1), p -> BlastPlanner.Cell.OPEN);
            assertTrue(plan.successful());
            if (budget < 0.5) {
                assertEquals(Set.of(ORIGIN), plan.intensities().keySet());
                assertEquals(budget, plan.intensities().get(ORIGIN), 0);
            } else {
                assertTrue(plan.intensities().size() > 1);
            }
        }
    }
}
