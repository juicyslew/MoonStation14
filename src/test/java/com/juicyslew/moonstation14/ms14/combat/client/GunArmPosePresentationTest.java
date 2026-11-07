package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunArmPosePresentationTest {
    @Test void headForwardUsesMinecraftHumanoidHeadFrame() {
        var mesh = net.minecraft.client.model.HumanoidModel.createMesh(
                net.minecraft.client.model.geom.builders.CubeDeformation.NONE, 0);
        var model = new net.minecraft.client.model.HumanoidModel<net.minecraft.world.entity.LivingEntity>(
                net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot());
        assertForward(model, 0, 0);
        assertForward(model, -35, 0);
        assertForward(model, 35, 0);
        assertForward(model, 0, -25);
        assertForward(model, 0, 25);
    }

    @Test void mk58NativeThirdPersonTransformsAndGripAimForwardInBothHands() {
        // Values decoded from weapon_pistol_mk58.json thirdperson_righthand/lefthand
        // transforms (rotation 0,180,0; identical translations/scales).
        for (boolean left : new boolean[]{false, true}) {
            for (float yaw : new float[]{0, -35, 35}) for (float pitch : new float[]{0, -25, 25}) {
                var head = new net.minecraft.client.model.HumanoidModel<net.minecraft.world.entity.LivingEntity>(
                        net.minecraft.client.model.geom.builders.LayerDefinition.create(
                                net.minecraft.client.model.HumanoidModel.createMesh(
                                        net.minecraft.client.model.geom.builders.CubeDeformation.NONE, 0), 64, 64).bakeRoot());
                head.head.yRot = (float)Math.toRadians(yaw);
                head.head.xRot = (float)Math.toRadians(pitch);
                var target = GunArmPosePresentation.headForward(head);
                var stack = new com.mojang.blaze3d.vertex.PoseStack();
                com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.applyItemGripTransform(
                        stack, left, com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
                stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                var nativeBore = stack.last().pose().transformDirection(new org.joml.Vector3f(0, 0, 1));
                var nativeUp = stack.last().pose().transformDirection(new org.joml.Vector3f(0, 1, 0));
                var rotation = GunArmAimMath.aim(new GunArmAimMath.Vec(nativeBore.x, nativeBore.y, nativeBore.z),
                        new GunArmAimMath.Vec(nativeUp.x, nativeUp.y, nativeUp.z), target);
                var aligned = rotate(rotation, new GunArmAimMath.Vec(nativeBore.x, nativeBore.y, nativeBore.z));
                assertTrue(aligned.normalized().dot(target.normalized()) > .9999,
                        "Mk58 bore behind head-forward, left=" + left + " yaw=" + yaw + " pitch=" + pitch);
            }
        }
    }

    private static GunArmAimMath.Vec rotate(GunArmAimMath.Rotation a, GunArmAimMath.Vec v) {
        double cx=Math.cos(a.x()),sx=Math.sin(a.x()),cy=Math.cos(a.y()),sy=Math.sin(a.y()),cz=Math.cos(a.z()),sz=Math.sin(a.z());
        double x1=v.x(), y1=cx*v.y()-sx*v.z(), z1=sx*v.y()+cx*v.z();
        double x2=cy*x1+sy*z1, y2=y1, z2=-sy*x1+cy*z1;
        return new GunArmAimMath.Vec(cz*x2-sz*y2, sz*x2+cz*y2, z2);
    }

    private static void assertForward(net.minecraft.client.model.HumanoidModel<?> model, float yaw, float pitch) {
        model.head.yRot = (float)Math.toRadians(yaw);
        model.head.xRot = (float)Math.toRadians(pitch);
        var target = GunArmPosePresentation.headForward(model);
        assertTrue(target.z() < -0.8, "humanoid forward must remain toward local -Z: " + target);
    }

    @Test void admissionHonorsCombatGunWieldAndMeleePriority() {
        assertTrue(GunArmPosePresentation.admitted(true,true,false,false,false)); // one-handed
        assertTrue(GunArmPosePresentation.admitted(true,true,true,true,false));
        assertFalse(GunArmPosePresentation.admitted(false,true,false,false,false));
        assertFalse(GunArmPosePresentation.admitted(true,false,false,false,false));
        assertFalse(GunArmPosePresentation.admitted(true,true,true,false,false));
        assertFalse(GunArmPosePresentation.admitted(true,true,false,false,true));
        assertTrue(GunArmPosePresentation.admitted(true,true,false,false,false)); // one-handed prone gun also admitted
        assertTrue(GunArmPosePresentation.admitted(true,true,true,true,false)); // validated two-handed prone gun
        assertFalse(GunArmPosePresentation.admitted(true,true,false,false,true)); // melee priority remains
        var shared = new com.juicyslew.moonstation14.ms14.standing.PostureData(true,true,0,0,
                0,0,false,false,false,0f,true);
        assertTrue(GunArmPosePresentation.sharedTorsoAdmitted(shared), "shared torso aim remains active during melee");
        assertFalse(GunArmPosePresentation.admitted(true,true,false,false,true), "melee still suppresses arm aiming");
    }
}
