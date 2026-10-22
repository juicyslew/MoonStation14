package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyItemTargetReachTest {
    private static final Vec3 EYE = Vec3.ZERO;

    @Test void physicalBoundsDefineReachRegardlessOfNominationMargin() {
        assertTrue(BodyItemTargetProbe.withinPhysicalReach(EYE,
                new AABB(5, -.1, -.1, 5.2, .1, .1), BodyItemTargetProbe.MAX_REACH));
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(EYE,
                new AABB(5.01, -.1, -.1, 5.21, .1, .1), BodyItemTargetProbe.MAX_REACH));
        // This box's inflated margin can clip a 5-block nomination ray; its physical box cannot.
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(EYE,
                new AABB(5.1, -.1, -.1, 5.3, .1, .1), BodyItemTargetProbe.MAX_REACH));
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(EYE,
                new AABB(1, -.1, -.1, 1.1, .1, .1), Double.NaN));
    }

    @Test void nonFiniteEyeAndNullInputsAreRejected() {
        AABB bounds = new AABB(1, -.1, -.1, 1.1, .1, .1);
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(new Vec3(Double.NaN, 0, 0), bounds, 5));
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(new Vec3(Double.POSITIVE_INFINITY, 0, 0), bounds, 5));
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(new Vec3(Double.NEGATIVE_INFINITY, 0, 0), bounds, 5));
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(null, bounds, 5));
        assertFalse(BodyItemTargetProbe.withinPhysicalReach(EYE, null, 5));
    }
}
