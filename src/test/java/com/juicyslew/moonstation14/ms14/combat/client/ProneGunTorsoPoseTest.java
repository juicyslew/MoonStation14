package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel;
import com.juicyslew.moonstation14.ms14.standing.GunProneAimGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProneGunTorsoPoseTest {
    private static ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity> model(boolean slim) {
        var mesh=net.minecraft.client.model.PlayerModel.createMesh(
                net.minecraft.client.model.geom.builders.CubeDeformation.NONE,slim);
        return new ArticulatedHarnessModel<>(net.minecraft.client.model.geom.builders.LayerDefinition
                .create(mesh,64,64).bakeRoot(),slim);
    }
    @Test void hipFixedNeckAndShouldersStayConnectedOnBothRigs() {
        for(boolean slim:new boolean[]{false,true}) {
            var m=model(slim); float tilt=(float)GunProneAimGeometry.TORSO_RADIANS;
            float leftX=m.leftArm.x, rightX=m.rightArm.x;
            float nativeShoulderY=m.leftArm.getInitialPose().y;
            assertEquals(slim?2.5f:2f,nativeShoulderY,1e-6);
            ProneGunTwoBonePose.applyTorso(m,tilt,0);
            assertEquals(-tilt,m.body.xRot,1e-6);
            assertEquals(12-12*Math.cos(tilt),m.body.y,1e-5);
            assertEquals(12*Math.sin(tilt),m.body.z,1e-5);
            assertEquals(m.body.y,m.head.y,1e-6); assertEquals(m.body.z,m.head.z,1e-6);
            var expected=new org.joml.Vector3f(0,nativeShoulderY-2,0);
            new org.joml.Matrix4f().translate(0,12,0).rotateX(-tilt).translate(0,-12,0).transformPosition(expected);
            float shoulderY=expected.y;
            float shoulderZ=expected.z;
            assertEquals(shoulderY,m.leftArm.y,1e-5); assertEquals(shoulderZ,m.leftArm.z,1e-5);
            assertEquals(leftX,m.leftArm.x,1e-6); assertEquals(rightX,m.rightArm.x,1e-6);
            assertEquals(-Math.PI/2,m.head.xRot,1e-6);
            assertEquals(0,m.head.yRot,1e-6); assertEquals(0,m.head.zRot,1e-6);
            assertEquals(12*Math.sin(tilt),m.head.z,1e-5);
        }
    }
}
