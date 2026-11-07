package com.juicyslew.moonstation14.ms14.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProneGunContactPoseTest {
    @Test void nativeWieldableGunContactsUseResolvedFramesAndActualForearms() throws Exception {
        String[] guns={"weapon_rifle_lecter","weapon_shotgun_kammerer","weapon_sniper_mosin","weapon_energy_shotgun"};
        var raw=BotanyCatalogCandidateTest.mainResourcesAll("loose_object_profile");
        var resolved=PrototypeResolver.resolve(raw,ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        var bindings=GunMountTransformTest.resourceJson("assets/moonstation14/gun_presentation_bindings.json").getAsJsonObject("items");
        for(String gun:guns) {
        var item=bindings.getAsJsonObject(gun);
        var binding=new GunPresentationBindings.Binding(GunMountTransformTest.vec(item,"bore"),GunMountTransformTest.vec(item,"up"),
                GunMountTransformTest.vec(item,"grip"),GunMountTransformTest.vec(item,"support"),GunMountTransformTest.vec(item,"muzzle"));
        float scale=LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,resolved.get(ResourceLocation.fromNamespaceAndPath("moonstation14",gun))).getOrThrow().modelScale();
        var display=GunMountTransformTest.resolveModel("moonstation14:item/"+gun).getAsJsonObject("display");
        for(boolean slim:new boolean[]{false,true}) for(HumanoidArm active:HumanoidArm.values()) {
            HumanoidArm support=active==HumanoidArm.LEFT?HumanoidArm.RIGHT:HumanoidArm.LEFT;
            var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
            var model=new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(root,slim);
            ProneGunTwoBonePose.applyTorso(model,(float)com.juicyslew.moonstation14.ms14.standing.GunProneAimGeometry.TORSO_RADIANS,0);
            PoseStack frame=new PoseStack();
            com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.applyItemGripTransform(
                    frame,active==HumanoidArm.LEFT,com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
            GunMountTransformTest.decodeTransform(display.getAsJsonObject(active==HumanoidArm.LEFT?"thirdperson_lefthand":"thirdperson_righthand"))
                    .apply(active==HumanoidArm.LEFT,frame);
            frame.translate(-.5,-.5,-.5);
            Matrix4f nativeT=new Matrix4f(frame.last().pose());
            var targets=new GunMountTransform.AimTargets(new GunArmAimMath.Vec(0,-1,0),new GunArmAimMath.Vec(0,0,1));
            var palm=GunMountTransform.handContactPoint(model,active);
            var result=ProneGunTwoBonePose.solveContacts(model,active,support,binding,nativeT,scale,targets);
            var armTargets=GunMountTransform.articulatedArmSpaceTargets(model,active,targets.bore(),targets.up());
            String context="gun="+gun+" slim="+slim+" active="+active+" scale="+scale+" primary="+result.primary()+" support="+result.support()+" fraction="+result.supportFraction()+" gap="+result.supportGap();
            assertTrue(result.primary().floorContact(),context); assertTrue(result.support().floorContact(),context);
            assertEquals(0,result.primary().elbow().z(),1e-6,context); assertEquals(0,result.support().elbow().z(),1e-6,context);
            assertTrue(result.primary().reached(),context); assertTrue(result.support().reached(),context);
            assertTrue(result.supportFraction()>=.5,context+" support fraction="+result.supportFraction());
            var primaryShoulder=model.armPart(active); var supportShoulder=model.armPart(support);
            assertTrue(Math.hypot(result.primary().elbow().x()-(primaryShoulder.x+model.slimHandOffset(active))/16.0,
                    result.primary().elbow().y()-primaryShoulder.y/16.0)>1e-5,context+" primary elbow must bend");
            assertTrue(Math.hypot(result.support().elbow().x()-(supportShoulder.x+model.slimHandOffset(support))/16.0,
                    result.support().elbow().y()-supportShoulder.y/16.0)>1e-5,context+" support elbow must bend");
            assertTrue(result.supportFraction()>0,context+" support fraction");
            PoseStack hand=new PoseStack(); model.translateToHand(active,hand);
            Vector3f grip=new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z());
            PoseStack correction=new PoseStack(); GunMountTransform.applyCorrection(correction,nativeT,binding,scale,armTargets.bore(),armTargets.up(),palm);
            Matrix4f corrected=new Matrix4f(correction.last().pose()).mul(nativeT);
             Vector3f actual=hand.last().pose().transformPosition(corrected.transformPosition(grip));
             double faceFrontY=(model.head.y-4.0)/16.0;
             Vector3f muzzle=hand.last().pose().transformPosition(corrected.transformPosition(new Vector3f((float)binding.muzzle().x(),(float)binding.muzzle().y(),(float)binding.muzzle().z())));
             assertTrue(muzzle.y<faceFrontY-.125,context+" muzzle not clear of face plane: "+muzzle);
             Vector3f physicalPalm=hand.last().pose().transformPosition(new Vector3f((float)palm.x(),(float)palm.y(),(float)palm.z()));
             assertEquals(0,physicalPalm.distance(actual),1e-4,context);
            assertEquals(result.primary().hand().x(),actual.x,1e-4,context); assertEquals(result.primary().hand().y(),actual.y,1e-4,context);
            assertEquals(result.primary().hand().z(),actual.z,1e-4,context);
            Vector3f bore=hand.last().pose().transformDirection(corrected.transformDirection(new Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z()))).normalize();
            assertEquals(0,bore.x,1e-4,context); assertEquals(-1,bore.y,1e-4,context); assertEquals(0,bore.z,1e-4,context);
            var seg=GunMountTransform.supportWeaponSegment(binding,nativeT,model,active,support,scale,armTargets,palm);
            PoseStack supportHand=new PoseStack(); model.translateToHand(support,supportHand);
             var supportPalm=GunMountTransform.handContactPoint(model,support);
             Vector3f supportActual=supportHand.last().pose().transformPosition(new Vector3f((float)supportPalm.x(),(float)supportPalm.y(),(float)supportPalm.z()));
             assertTrue(physicalPalm.distance(supportActual)>=(model.palmWidthPixels()+.5)/16.0,context+" overlapping palms: "+physicalPalm+" / "+supportActual);
             assertTrue(supportActual.y<=faceFrontY+.05,context+" support palm behind face plane: "+supportActual);
            Vector3f selected=new Vector3f((float)result.support().hand().x(),(float)result.support().hand().y(),(float)result.support().hand().z());
            assertEquals(0,supportActual.distance(selected),1e-4,context);
            Vector3f segmentPoint=new Vector3f((float)seg.grip().x(),(float)seg.grip().y(),(float)seg.grip().z())
                    .lerp(new Vector3f((float)seg.foregrip().x(),(float)seg.foregrip().y(),(float)seg.foregrip().z()),(float)result.supportFraction());
            assertTrue(supportActual.distance(segmentPoint)<1e-4,context+" actual="+supportActual+" segment="+segmentPoint);
        }
        }
    }
}
