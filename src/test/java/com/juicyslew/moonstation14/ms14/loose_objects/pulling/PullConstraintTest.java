package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PullConstraintTest {
    @Test void slackHasNoAttractionAndOutsideCorrectionIsBoundedInward() {
        assertEquals(Vec3.ZERO, PullConstraint.impulse(new Vec3(1, 0, 0), Vec3.ZERO, Vec3.ZERO, 1));
        Vec3 target = new Vec3(100, 0, 0), current = Vec3.ZERO, velocity = Vec3.ZERO;
        Vec3 impulse = PullConstraint.impulse(target, current, velocity, 2);
        assertTrue(impulse.length() > 0);
        assertTrue(impulse.length() <= PullConstraint.MAX_IMPULSE);
        assertNotEquals(target, current);
        assertTrue(impulse.length() < target.distanceTo(current));
        assertTrue(impulse.dot(target.subtract(current)) > 0);
        Vec3 outward = PullConstraint.impulse(target, current, new Vec3(2, 0, 0), 2);
        assertTrue(outward.x < 0);
        assertTrue(outward.length() <= PullConstraint.MAX_IMPULSE);
    }

    @Test void walkingAwayThroughSlackDoesNotAttractUntilRopeIsTaut() {
        Vec3 target = new Vec3(2, 0, 0);
        Vec3 actorAtCapture = Vec3.ZERO;
        double captured = target.distanceTo(actorAtCapture);
        // The actor moves away while the rope is slack: no idle attraction is applied.
        Vec3 actorInsideSlack = new Vec3(0.1, 0, 0);
        assertEquals(Vec3.ZERO, PullConstraint.impulse(target, actorInsideSlack, Vec3.ZERO, captured));
        Vec3 actorOutsideSlack = new Vec3(-1, 0, 0);
        Vec3 correction = PullConstraint.impulse(target, actorOutsideSlack, Vec3.ZERO, captured);
        assertTrue(correction.dot(target.subtract(actorOutsideSlack)) > 0);
        assertTrue(Double.isFinite(correction.x));
    }

    @Test void zeroInvalidAndOutOfRangeLimitsProduceNoCorrection() {
        Vec3 target = new Vec3(1, 0, 0);
        assertEquals(Vec3.ZERO, PullConstraint.impulse(target, Vec3.ZERO, Vec3.ZERO, 0));
        assertEquals(Vec3.ZERO, PullConstraint.impulse(target, Vec3.ZERO, Vec3.ZERO, Double.NaN));
        Vec3 outside = PullConstraint.impulse(target, Vec3.ZERO, Vec3.ZERO, .5);
        assertTrue(outside.length() > 0);
        assertTrue(outside.dot(target) > 0);
        assertTrue(outside.length() <= PullConstraint.MAX_IMPULSE);
        assertEquals(Vec3.ZERO, PullConstraint.impulse(new Vec3(Double.POSITIVE_INFINITY, 0, 0),
                Vec3.ZERO, Vec3.ZERO, 2));
    }
}
