package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrownFlightOriginPoseTest {
    @Test void orientationOccursAtPhysicalEntityCenterWithoutOrbitingTheOrigin() {
        for (var velocity : new org.joml.Vector3f[]{new org.joml.Vector3f(1,2,3),new org.joml.Vector3f(0,-1,0),new org.joml.Vector3f(1,0,0)}) {
            var q=ThrownSpritePose.rotation(45,velocity);
            PoseStack pose=new PoseStack();
            LooseItemRenderer.applyFlightOrientation(pose,q,.08f);
            var origin=pose.last().pose().transformPosition(new org.joml.Vector3f());
            assertEquals(0,origin.x,1e-6); assertEquals(.04,origin.y,1e-6); assertEquals(0,origin.z,1e-6);
            var forward=new org.joml.Vector3f((float)Math.sqrt(.5f),(float)Math.sqrt(.5f),0);
            assertTrue(pose.last().pose().transformDirection(forward).normalize().distance(new org.joml.Vector3f(velocity).normalize())<1e-5);
        }
    }
}
