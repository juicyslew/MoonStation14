package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AttachmentPoseGeometryTest {
    @Test void rootFrameRoundTripsAtWorldOriginAndLargeCoordinates() {
        for (var mode : AttachmentPoseGeometry.PoseMode.values()) for (double scale : new double[]{.5, 1, 2}) for (float yaw : new float[]{0, 90, 180}) {
            for (boolean faceUp : new boolean[]{false, true}) for (Vec3 origin : new Vec3[]{Vec3.ZERO, new Vec3(12_000_000, 64, -59)}) {
                Vec3 local = new Vec3(.17, -.41, .23);
                Vec3 world = AttachmentPoseGeometry.bodyToWorld(local, origin, 1, scale, yaw, mode, faceUp);
                Vec3 captured = AttachmentPoseGeometry.captureLocalPoint(world, origin, 1, scale, yaw, mode, faceUp);
                assertEquals(local.x, captured.x, 1e-5);
                assertEquals(local.y, captured.y, 1e-5);
                assertEquals(local.z, captured.z, 1e-5);
            }
        }
    }

    @Test void modesTransitionAndDirectionsUseSelectedFrames() {
        Vec3 origin = new Vec3(0,0,0), local = new Vec3(.2,-.3,.1);
        Vec3 standing = AttachmentPoseGeometry.bodyToWorld(local, origin, 1, 1, 90, AttachmentPoseGeometry.PoseMode.STANDING, false);
        Vec3 prone = AttachmentPoseGeometry.bodyToWorld(local, origin, 1, 1, 90, AttachmentPoseGeometry.PoseMode.PRONE, false);
        assertNotEquals(standing, prone);
        assertEquals(prone.x, AttachmentPoseGeometry.bodyToWorld(
                AttachmentPoseGeometry.captureLocalPoint(standing, origin, 1, 1, 90, AttachmentPoseGeometry.PoseMode.STANDING, false),
                origin, 1, 1, 90, AttachmentPoseGeometry.PoseMode.PRONE, false).x, 1e-6);
        assertEquals(standing.x, AttachmentPoseGeometry.bodyToWorld(
                AttachmentPoseGeometry.captureLocalPoint(prone, origin, 1, 1, 90, AttachmentPoseGeometry.PoseMode.PRONE, false),
                origin, 1, 1, 90, AttachmentPoseGeometry.PoseMode.STANDING, false).x, 1e-6);
        Vec3 incoming = new Vec3(.2,.7,-.4).normalize();
        Vec3 localDirection = AttachmentPoseGeometry.captureLocalDirection(incoming, 1, 1, 0, AttachmentPoseGeometry.PoseMode.PRONE, true);
        var turnedVector = AttachmentPoseGeometry.relativeRootMatrix(1,1,0,AttachmentPoseGeometry.PoseMode.PRONE,true)
                .transformDirection(new org.joml.Vector3f((float)localDirection.x,(float)localDirection.y,(float)localDirection.z));
        Vec3 turned = new Vec3(turnedVector.x, turnedVector.y, turnedVector.z);
        assertEquals(incoming.x, turned.x, 1e-5); assertEquals(incoming.y, turned.y, 1e-5); assertEquals(incoming.z, turned.z, 1e-5);
    }

    @Test void contactIntersectionIsClampedToTheBodySurface() {
        var box = new net.minecraft.world.phys.AABB(0,0,0,1,2,1);
        assertEquals(new Vec3(0,1,.5), LooseItemEntity.contactOnSurface(new Vec3(-.3,1,.5), box));
        Vec3 inside = new Vec3(.4,1.2,.8);
        assertEquals(inside, LooseItemEntity.contactOnSurface(inside, box));
    }
}
