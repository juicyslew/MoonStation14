package com.juicyslew.moonstation14.ms14.mobs;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcFlightControllerTest {
    @Test void durationUsesResolvedNominalTicksAndBoundsRandomVariation() {
        assertEquals(400, NpcFlightController.duration(400, .5));
        assertEquals(300, NpcFlightController.duration(400, 0));
        assertEquals(500, NpcFlightController.duration(400, Math.nextDown(1d)));
    }

    @Test void phaseExpiresAtDurationBoundary() {
        var state = NpcFlightController.State.grounded(100, 20);
        assertFalse(NpcFlightController.expired(state, 119));
        assertTrue(NpcFlightController.expired(state, 120));
    }

    @Test void verticalWishIsSignedClampedAndEasesNearTarget() {
        assertEquals(.4, NpcFlightController.verticalWish(0, 10, .4));
        assertEquals(-.4, NpcFlightController.verticalWish(10, 0, .4));
        assertEquals(.14, NpcFlightController.verticalWish(0, .4, .4), 1e-12);
        assertEquals(-.14, NpcFlightController.verticalWish(.4, 0, .4), 1e-12);
    }

    @Test void temporalVerticalEasingIsBoundedAndPreservesDescentSign() {
        assertEquals(.12, NpcFlightController.easeVerticalWish(0, .8, .12), 1e-12);
        assertEquals(-.12, NpcFlightController.easeVerticalWish(0, -.8, .12), 1e-12);
        assertEquals(-.28, NpcFlightController.easeVerticalWish(-.2, -.8, .08), 1e-12);
        assertTrue(NpcFlightController.phaseLanding(NpcFlightController.Phase.LANDING));
        assertFalse(NpcFlightController.phaseLanding(NpcFlightController.Phase.AIR_TRAVEL));
    }

    @Test void committedFlightManeuversProtectRoutingButAirTravelCanSelectTasks() {
        assertTrue(NpcFlightController.protectsCommittedManeuver(NpcFlightController.Phase.TAKEOFF));
        assertTrue(NpcFlightController.protectsCommittedManeuver(NpcFlightController.Phase.LAND_APPROACH));
        assertTrue(NpcFlightController.protectsCommittedManeuver(NpcFlightController.Phase.LANDING));
        assertFalse(NpcFlightController.protectsCommittedManeuver(NpcFlightController.Phase.AIR_TRAVEL));
        assertFalse(NpcFlightController.protectsCommittedManeuver(NpcFlightController.Phase.GROUND_WANDER));
    }

    @Test void reachedUsesThreeDimensionalDistance() {
        assertFalse(NpcFlightController.reached(Vec3.ZERO, new Vec3(0, 1, 0)));
        assertTrue(NpcFlightController.reached(Vec3.ZERO, new Vec3(.4, .4, 0)));
    }

    @Test void takeoffAndLandingApproachPredicatesUseActualClearanceAndStableAnchor() {
        assertFalse(NpcFlightController.takeoffCleared(2.5, 4));
        assertTrue(NpcFlightController.takeoffCleared(3.7, 4));
        assertEquals(2.5, NpcFlightController.anchoredAltitude(4, 20, 0));
        assertEquals(5.5, NpcFlightController.anchoredAltitude(4, -20, 1));
        assertTrue(NpcFlightController.nearLandingApproach(Vec3.ZERO, new Vec3(.2, 0, 0)));
        assertFalse(NpcFlightController.nearLandingApproach(Vec3.ZERO, new Vec3(1, 0, 0)));
    }

    @Test void lookAheadIsDistanceBoundedAndZeroLengthIsFinite() {
        Vec3 ahead = NpcFlightController.lookAhead(Vec3.ZERO, new Vec3(10, 0, 0), 3);
        assertEquals(new Vec3(3, 0, 0), ahead);
        assertEquals(3, ahead.distanceTo(Vec3.ZERO), 1e-12);
        Vec3 zero = NpcFlightController.lookAhead(Vec3.ZERO, Vec3.ZERO, 3);
        assertEquals(Vec3.ZERO, zero);
        assertTrue(Double.isFinite(zero.x) && Double.isFinite(zero.y) && Double.isFinite(zero.z));
    }
}
