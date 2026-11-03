package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyItemTargetReachTest {
    private static final Vec3 EYE = Vec3.ZERO;

    @Test void attachedItemPolicyHidesOnlyCurrentBodiesOwnAttachmentInOrdinaryTargeting() {
        var body = java.util.UUID.randomUUID();
        var other = java.util.UUID.randomUUID();
        var own = new com.juicyslew.moonstation14.entities.ItemAttachment(body, 1, Vec3.ZERO, Vec3.ZERO,
                com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED);
        var theirs = new com.juicyslew.moonstation14.entities.ItemAttachment(other, 2, Vec3.ZERO, Vec3.ZERO,
                com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.ENSNARED);
        assertFalse(AttachedItemTargeting.eligible(body, java.util.Optional.of(own), false));
        assertFalse(AttachedItemTargeting.eligible(body, java.util.Optional.of(own), true));
        assertTrue(AttachedItemTargeting.eligible(body, java.util.Optional.of(theirs), false));
        assertTrue(AttachedItemTargeting.eligible(body, java.util.Optional.empty(), false));
        assertTrue(AttachedItemTargeting.eligible(other, java.util.Optional.of(own), false));
    }

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

    @Test void boundedRayEndpointClipsOnlyAlongTheOriginalRay() {
        Vec3 from = new Vec3(4, -58.4, 9);
        for (double length : new double[]{8, 16}) {
            Vec3 requested = from.add(0, -length, 0);
            Vec3 clipped = BodyItemTargetProbe.boundedRayEndpoint(from, requested, -64, 320);
            assertEquals(-63.9999, clipped.y, 1e-8);
            assertEquals(from.x, clipped.x);
            assertEquals(from.z, clipped.z);
        }
        Vec3 angled = from.add(3, -16, 4);
        Vec3 clippedAngle = BodyItemTargetProbe.boundedRayEndpoint(from, angled, -64, 320);
        double t = (-63.9999 - from.y) / (angled.y - from.y);
        assertEquals(from.x + 3 * t, clippedAngle.x, 1e-9);
        assertEquals(from.z + 4 * t, clippedAngle.z, 1e-9);

        Vec3 upward = BodyItemTargetProbe.boundedRayEndpoint(from, from.add(0, 500, 0), -64, 320);
        assertEquals(319.9999, upward.y, 1e-8);
        Vec3 interior = from.add(2, 1, 3);
        assertSame(interior, BodyItemTargetProbe.boundedRayEndpoint(from, interior, -64, 320));
        assertNull(BodyItemTargetProbe.boundedRayEndpoint(new Vec3(Double.NaN, 0, 0), interior, -64, 320));
        assertNull(BodyItemTargetProbe.boundedRayEndpoint(from, new Vec3(0, Double.POSITIVE_INFINITY, 0), -64, 320));
    }
}
