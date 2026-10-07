package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyInteractionBoundsTest {
    @Test void standingModelHeadAndFeetRemainAimableOnProneCollisionBox() {
        AABB physical = new AABB(-.3, 0, -.3, .3, .6, .3);
        for (boolean faceUp : new boolean[]{false, true}) {
            AABB bounds = BodyInteractionBounds.downed(physical, Vec3.ZERO, .6, 1.8, 0, faceUp);
            Vec3 head = PronePoseGeometry.transform(Vec3.ZERO, new Vec3(0, 1.8, 0), 1.8, 0, faceUp);
            Vec3 feet = PronePoseGeometry.transform(Vec3.ZERO, Vec3.ZERO, 1.8, 0, faceUp);
            assertTrue(bounds.contains(head), "rendered head center is targetable: bounds=" + bounds + " head=" + head);
            assertTrue(bounds.contains(feet), "rendered feet center is targetable");
            assertFalse(bounds.contains(new Vec3(2.5, .3, 0)), "target bounds do not use render culling circle");
            assertEquals(.6, physical.getYsize(), 1e-9, "physical prone collision remains unchanged");
        }
    }

    @Test void interactionFootprintRotatesWithBodyYawAndStillPreservesPhysicalDimensions() {
        AABB physical = new AABB(-.3, 0, -.3, .3, .6, .3);
        AABB eastWest = BodyInteractionBounds.downed(physical, Vec3.ZERO, .6, 1.8, 90, false);
        Vec3 head = PronePoseGeometry.transform(Vec3.ZERO, new Vec3(0, 1.8, 0), 1.8, 90, false);
        assertTrue(eastWest.contains(head));
        assertFalse(eastWest.contains(new Vec3(0, 0, 1.5)));
        assertEquals(.6, physical.getYsize(), 1e-9);
    }

    @Test void renderedAndTargetGeometryShareEntityCenteredLongitudinalExtentAtQuarterTurns() {
        Vec3 root = new Vec3(12.25, 64, -7.5);
        AABB physical = new AABB(root.x - .3, root.y, root.z - .3,
                root.x + .3, root.y + .6, root.z + .3);
        for (float yaw : new float[]{0, 90, 180, 270}) for (boolean faceUp : new boolean[]{false, true}) {
            AABB target = BodyInteractionBounds.downed(physical, root, .6, 1.8, yaw, faceUp);
            AABB rendered = ProneRenderBounds.calculate(physical, root, .6, 1.8, yaw, faceUp);
            Vec3 head = PronePoseGeometry.transform(root, new Vec3(0, 1.8, 0), 1.8, yaw, faceUp);
            Vec3 feet = PronePoseGeometry.transform(root, Vec3.ZERO, 1.8, yaw, faceUp);
            assertEquals(root.x, (head.x + feet.x) * .5, 1e-9);
            assertEquals(root.z, (head.z + feet.z) * .5, 1e-6);
            assertTrue(target.contains(head));
            assertTrue(target.contains(feet));
            assertTrue(rendered.contains(head));
            assertTrue(rendered.contains(feet));
            assertEquals(physical.getYsize(), .6, 1e-9, "centering must not alter collision height");
        }
    }

    @Test void sharedEyeRayUsesNearestVisiblePointAndRejectsOccludedOrOutOfReachTargets() {
        AABB headAndTorso = BodyInteractionBounds.downed(new AABB(-.3, 0, -.3, .3, .6, .3),
                Vec3.ZERO, .6, 1.8, 0, false);
        Vec3 eye = new Vec3(0, .4, 0);
        Vec3 rayEnd = eye.add(0, 0, 5);
        AABB target = headAndTorso.move(0, 0, 3);
        Vec3 point = BodyInteractionRay.hit(target, eye, rayEnd, 5, 5).orElseThrow();
        assertTrue(eye.distanceTo(point) <= 3);
        assertTrue(BodyInteractionRay.hit(target, eye, rayEnd, 2, 5).isEmpty(), "too-far hit is rejected");
        assertTrue(BodyInteractionRay.hit(target, eye, rayEnd, 5, 1.5).isEmpty(), "blocker before hit rejects target");
    }
}
