package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HandReleaseKinematicsTest {
    @Test
    void deliberateSurfacePlacementStartsRelativeToItsSupportWhileAirReleaseInheritsMotion() {
        var worldFloor = new BodyItemTargetProbe.DropPlacement(new Vec3(1, 2, 3), Vec3.ZERO,
                null, new Vec3(1, 2, 3));
        var frameFloor = new BodyItemTargetProbe.DropPlacement(new Vec3(1, 2, 3), Vec3.ZERO,
                java.util.UUID.randomUUID(), new Vec3(4, 5, 6));
        Vec3 own = new Vec3(5.6, 0, 0), carrier = new Vec3(40, 0, 0);
        assertEquals(Vec3.ZERO, HandReleaseKinematics.composePlacement(worldFloor, own, carrier));
        assertEquals(Vec3.ZERO, HandReleaseKinematics.composePlacement(frameFloor, own, carrier));
        var air = new BodyItemTargetProbe.DropPlacement(Vec3.ZERO, Vec3.ZERO);
        assertEquals(HandReleaseKinematics.compose(own, carrier, Vec3.ZERO),
                HandReleaseKinematics.composePlacement(air, own, carrier));
        assertThrows(IllegalArgumentException.class, () -> new BodyItemTargetProbe.DropPlacement(
                Vec3.ZERO, Vec3.ZERO, java.util.UUID.randomUUID(), null));
    }

    @Test
    void accountsForSourceAndDestinationPointVelocityExactlyOnce() {
        // Distinct translational + rotational point velocities model the actual release and
        // first native attachment points; SableFrameVelocity supplies SDK m/s at each point.
        Vec3 ownPerTick = new Vec3(.02, -.01, .03);
        Vec3 sourceCarrierPerSecond = new Vec3(4, 6, -2);
        Vec3 destinationCarrierPerSecond = new Vec3(-3, 2, 5);
        Vec3 intentPerTick = new Vec3(.11, .04, -.07);
        Vec3 initialWorldVelocity = HandReleaseKinematics.compose(ownPerTick,
                sourceCarrierPerSecond, intentPerTick);
        assertEquals(ownPerTick.add(sourceCarrierPerSecond.scale(.05)).add(intentPerTick), initialWorldVelocity);
        // The new item's pre-attachment basis is map/world (zero carrier); first native
        // attachment therefore applies exactly (zero - destination) m/s * .05.
        Vec3 afterAttachment = initialWorldVelocity.add(destinationCarrierPerSecond.scale(-.05));
        assertTrue(afterAttachment.distanceTo(ownPerTick.add(intentPerTick)
                .add(sourceCarrierPerSecond.scale(.05)).add(destinationCarrierPerSecond.scale(-.05))) < 1.0e-12);
    }

    @Test
    void worldReleaseDoesNotInventFrameVelocityAndUnknownMotionFailsClosed() {
        Vec3 ownPerTick = new Vec3(.03, -.02, .01);
        assertEquals(ownPerTick, HandReleaseKinematics.compose(ownPerTick, Vec3.ZERO, Vec3.ZERO));
        assertNull(HandReleaseKinematics.compose(ownPerTick, null, Vec3.ZERO));
        assertNull(HandReleaseKinematics.compose(new Vec3(Double.NaN, 0, 0), Vec3.ZERO, Vec3.ZERO));
    }
}
