package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapturedAttackFramePoseTest {
    @Test void observerRigidWeaponTransformKeepsUniformScaleOnEveryAxis() {
        var rotation=CapturedAttackFramePose.weaponRotation(new Vec3(0,0,1),new Vec3(1,1,1).normalize(),0,
                new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(0,0),
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT,0);
        var matrix=CapturedAttackFramePose.rigidWeaponTransform(rotation,2);
        var x=new Vector3f(1,0,0).mulDirection(matrix); var y=new Vector3f(0,1,0).mulDirection(matrix); var z=new Vector3f(0,0,1).mulDirection(matrix);
        assertEquals(2,x.length(),1e-5); assertEquals(2,y.length(),1e-5); assertEquals(2,z.length(),1e-5);
    }
    @Test void ownerCloseClearanceReplacesBodyPlaneOnlyForClosePhysicalGap() {
        Vec3 source=new Vec3(0,0,0), camera=new Vec3(0,0,-.1), d=new Vec3(0,0,1), f=d;
        double body=source.dot(d)+.3;
        assertEquals(body,CapturedAttackFramePose.ownerClearance(body,.25,source,camera,d,f,.06),1e-9);
        double close=CapturedAttackFramePose.ownerClearance(body,.1,source,camera,d,f,.06);
        assertEquals(-.04,close,1e-9);
        assertEquals(.06,source.subtract(camera).dot(f)+close-source.dot(d),1e-9);
        assertTrue(close<body);
        assertTrue(Double.isNaN(CapturedAttackFramePose.ownerClearance(body,.1,source,camera,d,new Vec3(1,0,0),.06)));
    }
    @Test void closeOwnerWeaponUsesActualCapturedTipAndViewPlaneFitAcrossContactPoses() {
        var model=org.mockito.Mockito.mock(net.minecraft.client.resources.model.BakedModel.class);
        var quad=org.mockito.Mockito.mock(net.minecraft.client.renderer.block.model.BakedQuad.class);
        int[] vertices=new int[32]; float[][] corners={{.1f,.1f,.1f},{.9f,.1f,.1f},{.9f,.9f,.9f},{.1f,.9f,.9f}}; int at=0;
        for(float[] p:corners){vertices[at]=Float.floatToRawIntBits(p[0]);vertices[at+1]=Float.floatToRawIntBits(p[1]);vertices[at+2]=Float.floatToRawIntBits(p[2]);at+=8;}
        org.mockito.Mockito.when(quad.getVertices()).thenReturn(vertices);
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(quad));
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.notNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(model.applyTransform(org.mockito.ArgumentMatchers.eq(net.minecraft.world.item.ItemDisplayContext.NONE),org.mockito.ArgumentMatchers.any(com.mojang.blaze3d.vertex.PoseStack.class),org.mockito.ArgumentMatchers.eq(false))).thenReturn(model);
        var profile=new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        Vec3 camera=Vec3.ZERO, forward=new Vec3(0,0,1), source=new Vec3(0,0,.1);
        for(double pitch:new double[]{-60,0,60}) for(double fan:new double[]{-60,0,60}) for(double gap:new double[]{.1,.2,.3}) {
            Vec3 central=new Vec3(0,Math.sin(Math.toRadians(pitch)),Math.cos(Math.toRadians(pitch))).normalize();
            var basis=com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.headLocalBasis(central,0);
            Vec3 d=basis.forward().scale(Math.cos(Math.toRadians(fan))).add(basis.right().scale(Math.sin(Math.toRadians(fan)))).normalize();
            Vec3 tip=source.add(d.scale(gap));
            var rotation=CapturedAttackFramePose.weaponRotation(central,d,0,new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(0,0),
                    com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE,(float)fan);
            double body=source.dot(d)+.3, physical=tip.dot(d)-body;
            double owner=CapturedAttackFramePose.ownerClearance(body,physical,source,camera,d,forward,.06);
            assertTrue(owner<body);
            var fit=CapturedAttackFramePose.constrainedWeapon(model,profile,1,rotation,d,tip,2,owner);
            assertNotNull(fit,"owner fit pitch="+pitch+" fan="+fan+" gap="+gap);
            var matrix=CapturedAttackFramePose.axialTransform(d,fit.axialScale(),rotation,2);
            var extent=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,profile,1,matrix,new Vector3f((float)d.x,(float)d.y,(float)d.z));
            assertNotNull(extent); assertEquals(tip.dot(d),extent.max()+fit.anchor().dot(d),1e-4);
            assertTrue(extent.min()+fit.anchor().dot(d)>=owner-1e-4); assertTrue(extent.max()+fit.anchor().dot(d)<=tip.dot(d)+1e-4);
            var viewExtent=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,profile,1,matrix,new Vector3f(0,0,1));
            assertNotNull(viewExtent);
            double cameraDepth=viewExtent.max()+fit.anchor().subtract(camera).dot(forward);
            assertTrue(cameraDepth>.06+1e-4,"rendered model must have visible support in front of near plane");
            var observerFit=CapturedAttackFramePose.constrainedWeapon(model,profile,1,rotation,d,tip,2,body);
            if(observerFit!=null) {
                var observerMatrix=CapturedAttackFramePose.axialTransform(d,observerFit.axialScale(),rotation,2);
                var observerExtent=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,profile,1,observerMatrix,new Vector3f((float)d.x,(float)d.y,(float)d.z));
                assertNotNull(observerExtent); assertTrue(observerExtent.min()+observerFit.anchor().dot(d)>=body-1e-4,"observer retains body clearance");
            }
            // All geometry is translated in camera-relative arithmetic; large world origins do not change the measured depth.
            Vec3 offset=new Vec3(8_000_000,0,-9_000_000);
            assertEquals(cameraDepth,viewExtent.max()+fit.anchor().add(offset).subtract(camera.add(offset)).dot(forward),1e-5);
        }
    }
    @Test void constrainedWeaponKeepsActualSupportInsideClearanceAndTipPlanes() {
        var model=org.mockito.Mockito.mock(net.minecraft.client.resources.model.BakedModel.class);
        var quad=org.mockito.Mockito.mock(net.minecraft.client.renderer.block.model.BakedQuad.class);
        int[] vertices=new int[32]; float[][] corners={{.05f,.1f,.05f},{.95f,.1f,.05f},{.95f,.9f,.05f},{.05f,.9f,.95f}}; int at=0;
        for(float[] q:corners){vertices[at]=Float.floatToRawIntBits(q[0]);vertices[at+1]=Float.floatToRawIntBits(q[1]);vertices[at+2]=Float.floatToRawIntBits(q[2]);at+=8;}
        org.mockito.Mockito.when(quad.getVertices()).thenReturn(vertices);
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(quad));
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.notNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(model.applyTransform(org.mockito.ArgumentMatchers.eq(net.minecraft.world.item.ItemDisplayContext.NONE),org.mockito.ArgumentMatchers.any(com.mojang.blaze3d.vertex.PoseStack.class),org.mockito.ArgumentMatchers.eq(false))).thenReturn(model);
        var profile=new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        for(double pitch:new double[]{-90,-60,0,60,90}) for(double bearing:new double[]{-60,0,60}) for(boolean left:new boolean[]{false,true}) {
            Vec3 central=new Vec3(0,Math.sin(Math.toRadians(pitch)),Math.cos(Math.toRadians(pitch))).normalize();
            var basis=com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.headLocalBasis(central,0);
            Vec3 dir=basis.forward().scale(Math.cos(Math.toRadians(bearing))).add(basis.right().scale(Math.sin(Math.toRadians(bearing)))).normalize();
            Vec3 tip=dir.scale(1.4).add(new Vec3(0,1,0)); double clearance=tip.dot(dir)-.6;
            var pose=CapturedAttackFramePose.weaponRotation(central,dir,0,new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(0,0),
                    com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE,left?35:-35);
            var fitted=CapturedAttackFramePose.constrainedWeapon(model,profile,1,pose,dir,tip,2,clearance);
            assertNotNull(fitted); assertTrue(fitted.axialScale()<1);
            var matrix=CapturedAttackFramePose.axialTransform(dir,fitted.axialScale(),pose,2);
            matrix.setTranslation((float)fitted.anchor().x,(float)fitted.anchor().y,(float)fitted.anchor().z);
            var e=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,profile,1,matrix,
                    new Vector3f((float)dir.x,(float)dir.y,(float)dir.z));
            assertEquals(tip.dot(dir),e.max(),1e-4); assertTrue(e.min()>=clearance-1e-4);
        }
        Vec3 dir=new Vec3(0,0,1),tip=new Vec3(0,0,1.4);
        var pose=CapturedAttackFramePose.weaponRotation(dir,dir,0,new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(0,0),
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT,0);
        var full=CapturedAttackFramePose.constrainedWeapon(model,profile,1,pose,dir,tip,2,.1);
        assertNotNull(full); assertEquals(1,full.axialScale());
        assertNull(CapturedAttackFramePose.constrainedWeapon(model,profile,1,pose,dir,tip,2,2));
    }
    @Test void weaponAnchorUsesCapturedDirectionAndPlacesProjectedTipVertexAtContact() {
        var model = org.mockito.Mockito.mock(net.minecraft.client.resources.model.BakedModel.class);
        var quad = org.mockito.Mockito.mock(net.minecraft.client.renderer.block.model.BakedQuad.class);
        int[] vertices = new int[32]; int at=0;
        float[][] corners={{.1f,.2f,.3f},{.9f,.15f,.25f},{.8f,.85f,.4f},{.2f,.75f,.35f}};
        for(float[] p:corners){vertices[at]=Float.floatToRawIntBits(p[0]);vertices[at+1]=Float.floatToRawIntBits(p[1]);vertices[at+2]=Float.floatToRawIntBits(p[2]);at+=8;}
        org.mockito.Mockito.when(quad.getVertices()).thenReturn(vertices);
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(quad));
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.notNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(model.applyTransform(org.mockito.ArgumentMatchers.eq(net.minecraft.world.item.ItemDisplayContext.NONE),org.mockito.ArgumentMatchers.any(com.mojang.blaze3d.vertex.PoseStack.class),org.mockito.ArgumentMatchers.eq(false))).thenReturn(model);
        var profile = new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        for (Vec3 direction : new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(1,1,1).normalize()}) {
            var rotation=CapturedAttackFramePose.weaponRotation(direction,direction,0,
                    new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(45,20),
                    com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT,0);
            Vec3 tip=new Vec3(3,4,5), anchor=CapturedAttackFramePose.weaponAnchor(model,profile,1,rotation,direction,tip);
            assertNotNull(anchor);
            var contact=anchor.add(new Vec3(0,0,0));
            var extent=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,profile,1,
                    new org.joml.Matrix4f().translation((float)anchor.x,(float)anchor.y,(float)anchor.z).rotate(rotation),
                    new org.joml.Vector3f((float)direction.x,(float)direction.y,(float)direction.z));
            assertNotNull(extent);
            assertEquals(tip.x,extent.maxPoint().x,1e-5); assertEquals(tip.y,extent.maxPoint().y,1e-5); assertEquals(tip.z,extent.maxPoint().z,1e-5);
        }
    }

    @Test void scaledWeaponAnchorKeepsActualLeadingQuadVertexAtCapturedTip() {
        var model = org.mockito.Mockito.mock(net.minecraft.client.resources.model.BakedModel.class);
        var quad = org.mockito.Mockito.mock(net.minecraft.client.renderer.block.model.BakedQuad.class);
        int[] vertices = new int[32]; int at = 0;
        float[][] corners={{.1f,.2f,.3f},{.9f,.15f,.25f},{.8f,.85f,.4f},{.2f,.75f,.35f}};
        for (float[] p : corners) { vertices[at]=Float.floatToRawIntBits(p[0]); vertices[at+1]=Float.floatToRawIntBits(p[1]); vertices[at+2]=Float.floatToRawIntBits(p[2]); at+=8; }
        org.mockito.Mockito.when(quad.getVertices()).thenReturn(vertices);
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(quad));
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.notNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(model.applyTransform(org.mockito.ArgumentMatchers.eq(net.minecraft.world.item.ItemDisplayContext.NONE),org.mockito.ArgumentMatchers.any(com.mojang.blaze3d.vertex.PoseStack.class),org.mockito.ArgumentMatchers.eq(false))).thenReturn(model);
        var profile = new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        Vec3 direction = new Vec3(1, .7, 1).normalize(), tip = new Vec3(3,4,5);
        var rotation = CapturedAttackFramePose.weaponRotation(direction,direction, 0,
                new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(45,20),
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, 0);
        for (float scale : new float[]{1,2}) {
            Vec3 anchor = CapturedAttackFramePose.weaponAnchor(model, profile, 1, rotation, direction, tip, scale);
            assertNotNull(anchor);
            var matrix = new org.joml.Matrix4f().translation((float)anchor.x,(float)anchor.y,(float)anchor.z).rotate(rotation).scale(scale);
            var extent = com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,profile,1,matrix,
                    new org.joml.Vector3f((float)direction.x,(float)direction.y,(float)direction.z));
            assertNotNull(extent);
            assertEquals(tip.x,extent.maxPoint().x,1e-5); assertEquals(tip.y,extent.maxPoint().y,1e-5); assertEquals(tip.z,extent.maxPoint().z,1e-5);
        }
    }
    @Test void localPositiveZTracksCapturedDirectionForPitchYawAndRoll() {
        for (double pitch : new double[]{-90, -45, 0, 45, 90}) for (double yaw : new double[]{-170, 0, 73})
            for (boolean left : new boolean[]{false, true}) {
                double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
                Vec3 direction = new Vec3(Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
                var q = CapturedAttackFramePose.rotation(direction, (float) yaw, left);
                Vector3f z = new Vector3f(0, 0, 1).rotate(q);
                assertEquals(direction.x, z.x, 1e-5);
                assertEquals(direction.y, z.y, 1e-5);
                assertEquals(direction.z, z.z, 1e-5);
                Vector3f x = new Vector3f(1, 0, 0).rotate(q), yy = new Vector3f(0, 1, 0).rotate(q);
                assertEquals(1, x.length(), 1e-5); assertEquals(1, yy.length(), 1e-5);
                assertEquals(0, x.dot(yy), 1e-5);
            }
    }

    @Test void wideFanBearingsProduceDifferentFramesWithFrozenYaw() {
        var left = CapturedAttackFramePose.rotation(new Vec3(-1, 0, 1).normalize(), 0, true);
        var right = CapturedAttackFramePose.rotation(new Vec3(1, 0, 1).normalize(), 0, true);
        assertFalse(new Vector3f(0, 0, 1).rotate(left).equals(new Vector3f(0, 0, 1).rotate(right)));
    }

    @Test void armedWideAnchorUsesChestSourceAndKeepsCentralHeadUp() {
        var bounds = new net.minecraft.world.phys.AABB(-.3,0,-.3,.3,1.8,.3);
        var capture = com.juicyslew.moonstation14.ms14.combat.MeleeArcGeometry.captureStrike(
                Vec3.ZERO,bounds,new Vec3(0,1.53,0),new Vec3(0,0,1),0,1.5,120,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE,.6,null,null,r->r.length());
        var profile = new com.juicyslew.moonstation14.ms14.combat.MeleeStrikeProfile(1,.45,.8,.1,1);
        var view = new MeleeArcClient.StrikeView(java.util.UUID.randomUUID(),1,false,capture,profile,
                com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline.sample(profile,1,.2),false);
        Vec3 source = NeutralMeleeStrikeEffectGeometry.source(view);
        Vec3 endpoint = NeutralMeleeStrikeEffectGeometry.selectedEndpoint(view);
        assertEquals(1.08,source.y,1e-9);
        assertEquals(1.08,endpoint.y,1e-9);
        assertEquals(source,NeutralMeleeStrikeEffectGeometry.source(view));
        Vec3 captured = NeutralMeleeStrikeEffectGeometry.capturedDirection(capture,endpoint);
        Vec3 delta = endpoint.subtract(source);
        Vec3 expectedCaptured = new Vec3(delta.x, 0, delta.z).normalize();
        assertEquals(0, captured.y, 1e-9);
        assertEquals(1, captured.length(), 1e-9);
        assertEquals(expectedCaptured.x, captured.x, 1e-9);
        assertEquals(expectedCaptured.z, captured.z, 1e-9);
        var centralBasis = com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.headLocalBasis(capture.fullDirection(), capture.facingYaw());
        assertEquals(0, captured.dot(centralBasis.up()), 1e-9);
        var model=org.mockito.Mockito.mock(net.minecraft.client.resources.model.BakedModel.class);
        var quad=org.mockito.Mockito.mock(net.minecraft.client.renderer.block.model.BakedQuad.class);
        int[] vertices=new int[32]; float[][] corners={{.1f,.2f,.3f},{.9f,.15f,.25f},{.8f,.85f,.4f},{.2f,.75f,.35f}}; int at=0;
        for(float[] p:corners){vertices[at]=Float.floatToRawIntBits(p[0]);vertices[at+1]=Float.floatToRawIntBits(p[1]);vertices[at+2]=Float.floatToRawIntBits(p[2]);at+=8;}
        org.mockito.Mockito.when(quad.getVertices()).thenReturn(vertices);
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(quad));
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.notNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(model.applyTransform(org.mockito.ArgumentMatchers.eq(net.minecraft.world.item.ItemDisplayContext.NONE),org.mockito.ArgumentMatchers.any(com.mojang.blaze3d.vertex.PoseStack.class),org.mockito.ArgumentMatchers.eq(false))).thenReturn(model);
        var loose=new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        var rotation=CapturedAttackFramePose.weaponRotation(capture.fullDirection(),captured,capture.facingYaw(),
                new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(45,20),capture.mode(),0);
        Vec3 anchor=CapturedAttackFramePose.weaponAnchor(model,loose,1,rotation,captured,endpoint,2);
        var extent=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,loose,1,
                new org.joml.Matrix4f().translation((float)anchor.x,(float)anchor.y,(float)anchor.z).rotate(rotation).scale(2),
                new Vector3f((float)captured.x,(float)captured.y,(float)captured.z));
        assertNotNull(extent);
        assertEquals(endpoint.x, extent.maxPoint().x, 1e-6);
        assertEquals(endpoint.y, extent.maxPoint().y, 1e-6);
        assertEquals(endpoint.z, extent.maxPoint().z, 1e-6);
        for(double pitch:new double[]{-90,-45,0,45,90}) {
            Vec3 central=new Vec3(0,Math.sin(Math.toRadians(pitch)),Math.cos(Math.toRadians(pitch))).normalize();
            var basis=com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.headLocalBasis(central,0);
            for (double fanAngle : new double[]{-60,0,60}) {
                Vec3 sampled=basis.forward().scale(Math.cos(Math.toRadians(fanAngle)))
                        .add(basis.right().scale(Math.sin(Math.toRadians(fanAngle)))).normalize();
                var zeroPose=new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(0,0);
                var fanRotation=CapturedAttackFramePose.weaponRotation(central,sampled,0,zeroPose,
                        com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE,0);
                Vector3f transformedUp=new Vector3f(0,1,0).rotate(fanRotation);
                assertEquals(basis.up().x,transformedUp.x,1e-5);
                assertEquals(basis.up().y,transformedUp.y,1e-5);
                assertEquals(basis.up().z,transformedUp.z,1e-5);
            }
        }
    }

    @Test void repeatedLightPoseFromSameCapturedInputIsDeterministic() {
        Vec3 direction = new Vec3(.3, .4, .8).normalize();
        var pose = new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(45, 20);
        var first = CapturedAttackFramePose.weaponRotation(direction, direction, 37, pose,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, 0);
        var repeated = CapturedAttackFramePose.weaponRotation(direction, direction, 37, pose,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, 0);
        assertEquals(first, repeated);
    }

    @Test void wideFanUsesCentralUpBasisAcrossFanBearingsAndPitch() {
        var pose = new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(45, 20);
        for (double pitch : new double[]{0, 45, 90}) {
            Vec3 central = new Vec3(0, Math.sin(Math.toRadians(pitch)), Math.cos(Math.toRadians(pitch))).normalize();
            var basis = com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.headLocalBasis(central, 0);
            Vector3f up = new Vector3f((float)basis.up().x, (float)basis.up().y, (float)basis.up().z);
            for (double side : new double[]{-30, 0, 30}) {
                double angle = Math.toRadians(side);
                Vec3 sampled = new Vec3(Math.sin(angle), central.y, Math.cos(angle) * central.z).normalize();
                var q = CapturedAttackFramePose.weaponRotation(central, sampled, 0, pose,
                        com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE, (float)side);
                Vector3f frameForward = new Vector3f(0, 0, 1).rotate(q);
                assertTrue(Float.isFinite(frameForward.dot(up)));
            }
        }
    }

    @Test void weaponBladeAxisPreservesForwardProjectionAndWideRotationOnlyBanksIt() {
        var pose = new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(45, 20);
        Vec3 aim = new Vec3(0, 0, 1);
        for (double pitch : new double[]{-65, 0, 48}) for (double yaw : new double[]{-130, 0, 72})
            for (boolean left : new boolean[]{false, true}) {
                double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
                Vec3 direction = new Vec3(Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
                var light = CapturedAttackFramePose.weaponRotation(aim, direction, (float)yaw, pose,
                        com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, 2);
                Vector3f blade = new Vector3f((float)Math.cos(Math.toRadians(45)), (float)Math.sin(Math.toRadians(45)), 0).rotate(light);
                assertEquals(Math.cos(Math.toRadians(20)), blade.dot(new Vector3f((float)direction.x, (float)direction.y, (float)direction.z)), 1e-5);
                var straight = CapturedAttackFramePose.weaponRotation(aim, direction, (float)yaw,
                        new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.WeaponPose(45, 0),
                        com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, 2);
                Vector3f straightBlade = new Vector3f((float)Math.cos(Math.toRadians(45)), (float)Math.sin(Math.toRadians(45)), 0).rotate(straight);
                assertEquals(1, straightBlade.dot(new Vector3f((float)direction.x, (float)direction.y, (float)direction.z)), 1e-5);
            }
        var light = CapturedAttackFramePose.weaponRotation(aim, aim, 0, pose,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, 2);
        var wide = CapturedAttackFramePose.weaponRotation(aim, aim, 0, pose,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE, -135);
        Vector3f blade = new Vector3f((float)Math.sqrt(.5), (float)Math.sqrt(.5), 0).rotate(light);
        Vector3f wideBlade = new Vector3f((float)Math.sqrt(.5), (float)Math.sqrt(.5), 0).rotate(wide);
        assertEquals(Math.cos(Math.toRadians(20)), blade.z, 1e-5);
        assertEquals(blade.z, wideBlade.z, 1e-5);
        assertFalse(blade.equals(wideBlade));
        var lightWithWideAngle = CapturedAttackFramePose.weaponRotation(aim, aim, 0, pose,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT, -135);
        assertEquals(blade.z, new Vector3f((float)Math.sqrt(.5), (float)Math.sqrt(.5), 0).rotate(lightWithWideAngle).z, 1e-5);
    }
}
