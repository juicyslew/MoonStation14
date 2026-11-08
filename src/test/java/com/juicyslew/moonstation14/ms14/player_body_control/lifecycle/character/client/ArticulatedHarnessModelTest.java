package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

import static org.junit.jupiter.api.Assertions.*;

class ArticulatedHarnessModelTest {
    @Test void analyticArmPoseAppliesForBothHandsAndBodyShapes() {
        for (boolean slim : new boolean[]{false, true}) {
            var rig = new ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(baked(slim), slim);
            for (HumanoidArm arm : HumanoidArm.values()) {
                var shoulder = rig.armPart(arm);
                var grip = new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(
                        (arm == HumanoidArm.LEFT ? -.08 : .08), .60, .03);
                var target = new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(
                        shoulder.x / 16.0 + (arm == HumanoidArm.LEFT ? -.1 : .1), shoulder.y / 16.0 + .3, 0);
                var pose = com.juicyslew.moonstation14.ms14.combat.client.ArticulatedArmPose.solveAndApply(rig, arm, target, grip,
                        new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(0, 1, 0), -1);
                var stack = new com.mojang.blaze3d.vertex.PoseStack();
                rig.translateToHand(arm, stack);
                var actualGrip = stack.last().pose().transformPosition(new org.joml.Vector3f(
                        (float) grip.x(), (float) grip.y(), (float) grip.z()));
                assertEquals(pose.hand().x(), actualGrip.x, 1e-5);
                assertEquals(pose.hand().y(), actualGrip.y, 1e-5);
                assertEquals(pose.hand().z(), actualGrip.z, 1e-5);
                assertEquals(.25, Math.sqrt(Math.pow(pose.elbow().x() - (shoulder.x + rig.slimHandOffset(arm)) / 16.0, 2)
                        + Math.pow(pose.elbow().y() - shoulder.y / 16.0, 2) + Math.pow(pose.elbow().z() - shoulder.z / 16.0, 2)), 1e-5);
                assertNotNull(pose.elbow());
                var far = com.juicyslew.moonstation14.ms14.combat.client.ArticulatedArmPose.solveAndApply(rig, arm,
                        new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(20, 20, 20), grip,
                        new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(0, 1, 0), -1);
                assertFalse(far.reached());
                assertTrue(Float.isFinite(rig.armPart(arm).xRot + rig.forearm(arm).yRot));
            }
        }
    }

    @Test void neutralAndBentHandFramesPreserveNativeShoulderChainWideAndSlim() {
        for (boolean slim : new boolean[]{false, true}) {
            ModelPart vanillaRoot = baked(slim);
            ModelPart rigRoot = baked(slim);
            var vanilla = new PlayerModel<net.minecraft.world.entity.LivingEntity>(vanillaRoot, slim);
            var rig = new ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(rigRoot, slim);
            for (HumanoidArm arm : HumanoidArm.values()) {
                var expected = frame(vanilla, arm);
                var actual = frame(rig, arm);
                assertMatrixEquals(expected, actual);
                rig.setForearmRotation(arm, .65f, -.2f, .15f);
                assertFalse(expected.equals(frame(rig, arm)));
                rig.resetForearms();
                assertMatrixEquals(expected, frame(rig, arm));
            }
        }
    }

    @Test void frameResetRestoresAimTouchedPartsAndDoesNotAccumulateWideAndSlim() {
        for (boolean slim : new boolean[]{false, true}) {
            var nativeModel = new PlayerModel<net.minecraft.world.entity.LivingEntity>(baked(slim), slim);
            var rig = new ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(baked(slim), slim);
            for (int frame = 0; frame < 2; frame++) {
                rig.body.setPos(1, 2, 3); rig.body.setRotation(.1f, .2f, .3f);
                rig.head.setPos(4, 5, 6); rig.head.setRotation(.4f, .5f, .6f);
                rig.hat.setPos(7, 8, 9); rig.hat.setRotation(.7f, .8f, .9f);
                rig.leftArm.setPos(10, 11, 12); rig.leftArm.setRotation(1, 1.1f, 1.2f);
                rig.rightArm.setPos(13, 14, 15); rig.rightArm.setRotation(1.3f, 1.4f, 1.5f);
                rig.setForearmRotation(HumanoidArm.LEFT, 1, 2, 3);
                rig.setForearmRotation(HumanoidArm.RIGHT, 4, 5, 6);
                rig.resetPoseForFrame();
                assertPoseEquals(nativeModel.body, rig.body);
                assertPoseEquals(nativeModel.head, rig.head);
                assertPoseEquals(nativeModel.hat, rig.hat);
                assertPoseEquals(nativeModel.leftArm, rig.leftArm);
                assertPoseEquals(nativeModel.rightArm, rig.rightArm);
                for (HumanoidArm arm : HumanoidArm.values()) {
                    var forearm = rig.forearm(arm);
                    assertEquals(0, forearm.xRot); assertEquals(0, forearm.yRot); assertEquals(0, forearm.zRot);
                }
            }
        }
    }

    @Test void neutralModeUsesUnsplitNativeCubesForSkinAndSleeves() {
        for (boolean slim : new boolean[]{false, true}) {
            ModelPart root = baked(slim);
            var nativeArms = new PlayerModel<net.minecraft.world.entity.LivingEntity>(root, slim);
            var rig = new ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(root, slim);
            for (HumanoidArm arm : HumanoidArm.values()) {
                ModelPart skin = rig.armPart(arm), skinFull = skin.getChild("neutral_full");
                ModelPart sleeve = arm == HumanoidArm.LEFT ? rig.leftSleeve : rig.rightSleeve;
                ModelPart sleeveFull = sleeve.getChild("neutral_full");
                ModelPart vanillaSkin = arm == HumanoidArm.LEFT ? nativeArms.leftArm : nativeArms.rightArm;
                ModelPart vanillaSleeve = arm == HumanoidArm.LEFT ? nativeArms.leftSleeve : nativeArms.rightSleeve;
                assertRenderEquals(render(vanillaSkin), render(skin));
                assertRenderEquals(render(vanillaSleeve), render(sleeve));
                var upperGeometry=skin.getChild(ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD);
                var sleeveUpper=sleeve.getChild(ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD);
                assertTrue(upperGeometry.skipDraw); assertFalse(upperGeometry.visible); assertTrue(skinFull.visible); assertFalse(rig.forearm(arm).visible);
                assertTrue(sleeveUpper.skipDraw); assertFalse(sleeveUpper.visible); assertTrue(sleeveFull.visible);
                rig.setForearmRotation(arm, .2f, 0, 0); rig.syncArmMeshVisibility();
                assertFalse(upperGeometry.skipDraw); assertTrue(upperGeometry.visible); assertFalse(skinFull.visible); assertTrue(rig.forearm(arm).visible);
                assertFalse(sleeveUpper.skipDraw); assertTrue(sleeveUpper.visible); assertFalse(sleeveFull.visible);
                assertFalse(render(rig.armPart(arm)).equals(render(vanillaSkin)));
                rig.resetForearms(); rig.syncArmMeshVisibility();
                assertTrue(upperGeometry.skipDraw); assertFalse(upperGeometry.visible); assertTrue(skinFull.visible); assertFalse(rig.forearm(arm).visible);
                assertTrue(sleeveUpper.skipDraw); assertFalse(sleeveUpper.visible); assertTrue(sleeveFull.visible);
                assertRenderEquals(render(vanillaSkin), render(rig.armPart(arm)));
                assertRenderEquals(render(vanillaSleeve), render(sleeve));
            }
        }
    }

    @Test void supportedAndResetGeometryKeepsHandChainAndSkinUvsWideAndSlim() {
        for(boolean slim:new boolean[]{false,true}) {
            var rig=new ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(baked(slim),slim);
            for(HumanoidArm arm:HumanoidArm.values()) {
                var skin=rig.armPart(arm); var upper=skin.getChild(ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD);
                rig.setFrameMode(true);
                assertEquals(2,upper.y); assertEquals(6,rig.forearm(arm).y); assertEquals(.375,rig.upperLength(arm),1e-6);
                assertEquals(12,rig.palmEndpointPixels());
                var lowerGeometry=rig.forearm(arm).getChild(ArticulatedHarnessModel.LOWER_GEOMETRY_CHILD);
                assertEquals(8f/6f,lowerGeometry.yScale,1e-6);
                assertEquals(1,rig.forearm(arm).xScale,1e-6); assertEquals(1,rig.forearm(arm).yScale,1e-6); assertEquals(1,rig.forearm(arm).zScale,1e-6);
                var sleeve=arm==HumanoidArm.LEFT?rig.leftSleeve:rig.rightSleeve;
                rig.syncArmMeshVisibility();
                assertEquals(2,sleeve.getChild(ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD).y);
                assertEquals(6,sleeve.getChild("forearm").y);
                var neutral=new com.mojang.blaze3d.vertex.PoseStack(); rig.translateToHand(arm,neutral);
                var palm=neutral.last().pose().transformPosition(new org.joml.Vector3f(arm==HumanoidArm.LEFT?-.0625f:.0625f,.625f,0));
                assertEquals((rig.armPart(arm).y+12)/16.0,palm.y,1e-5);
                rig.setForearmRotation(arm,.3f,.1f,0);
                var target=new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(.2,.5,.1);
                var grip=new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(0,.6,.02);
                var solved=com.juicyslew.moonstation14.ms14.combat.client.ArticulatedArmPose.solveAndApply(rig,arm,target,grip,
                        new com.juicyslew.moonstation14.ms14.combat.client.GunArmAimMath.Vec(0,1,0),-1);
                var frame=new com.mojang.blaze3d.vertex.PoseStack(); rig.translateToHand(arm,frame);
                var basis=frame.last().pose();
                var c0=new org.joml.Vector3f(basis.m00(),basis.m01(),basis.m02());
                var c1=new org.joml.Vector3f(basis.m10(),basis.m11(),basis.m12());
                var c2=new org.joml.Vector3f(basis.m20(),basis.m21(),basis.m22());
                assertEquals(1,c0.length(),1e-5); assertEquals(1,c1.length(),1e-5); assertEquals(1,c2.length(),1e-5);
                assertEquals(0,c0.dot(c1),1e-5); assertEquals(0,c0.dot(c2),1e-5); assertEquals(0,c1.dot(c2),1e-5);
                assertEquals(1,new org.joml.Matrix3f(basis).determinant(),1e-5);
                var hand=frame.last().pose().transformPosition(new org.joml.Vector3f((float)grip.x(),(float)grip.y(),(float)grip.z()));
                assertEquals(solved.hand().x(),hand.x,1e-5); assertEquals(solved.hand().y(),hand.y,1e-5); assertEquals(solved.hand().z(),hand.z,1e-5);
                rig.resetPoseForFrame(); rig.syncArmMeshVisibility();
                assertEquals(10,rig.palmEndpointPixels()); assertEquals(1,lowerGeometry.yScale,1e-6);
                assertEquals(0,upper.y); assertEquals(4,rig.forearm(arm).y); assertEquals(0,skin.getChild("neutral_full").y);
                var nativeRig=new PlayerModel<net.minecraft.world.entity.LivingEntity>(baked(slim),slim);
                assertMatrixEquals(frame(nativeRig,arm),frame(rig,arm));
                assertEquals(0,sleeve.getChild(ArticulatedHarnessModel.UPPER_GEOMETRY_CHILD).y);
                assertEquals(4,sleeve.getChild("forearm").y);
                assertRenderEquals(render(nativeRig.leftArm),render(rig.leftArm));
                assertRenderEquals(render(nativeRig.leftSleeve),render(rig.leftSleeve));
            }
        }
    }

    private static void assertPoseEquals(ModelPart expected, ModelPart actual) {
        assertEquals(expected.x, actual.x); assertEquals(expected.y, actual.y); assertEquals(expected.z, actual.z);
        assertEquals(expected.xRot, actual.xRot); assertEquals(expected.yRot, actual.yRot); assertEquals(expected.zRot, actual.zRot);
    }

    private static RenderCapture render(ModelPart part) {
        var capture = new RenderCapture();
        part.render(new com.mojang.blaze3d.vertex.PoseStack(), capture, 0x00F000F0, 0);
        return capture;
    }

    private static void assertRenderEquals(RenderCapture expected, RenderCapture actual) {
        assertEquals(expected.x, actual.x); assertEquals(expected.y, actual.y); assertEquals(expected.z, actual.z);
        assertEquals(expected.u, actual.u); assertEquals(expected.v, actual.v);
    }

    private static final class RenderCapture implements com.mojang.blaze3d.vertex.VertexConsumer {
        final java.util.List<Float> x = new java.util.ArrayList<>(), y = new java.util.ArrayList<>(), z = new java.util.ArrayList<>();
        final java.util.List<Float> u = new java.util.ArrayList<>(), v = new java.util.ArrayList<>();
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
            this.x.add(x); this.y.add(y); this.z.add(z); return this;
        }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
            this.u.add(u); this.v.add(v); return this;
        }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private static ModelPart baked(boolean slim) {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
    }
    private static Matrix4f frame(PlayerModel<?> model, HumanoidArm arm) {
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        model.translateToHand(arm, pose);
        return new Matrix4f(pose.last().pose());
    }
    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        assertArrayEquals(expected.get(new float[16]), actual.get(new float[16]), 1.0e-6f);
    }
}
