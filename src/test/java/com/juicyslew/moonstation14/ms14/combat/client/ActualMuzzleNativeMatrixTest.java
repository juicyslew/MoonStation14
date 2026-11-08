package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActualMuzzleNativeMatrixTest {
    @Test void allAuthoredMuzzlesUseNativeHandModelAndMountMatrices() throws Exception {
        var bindings=GunMountTransformTest.resourceJson("assets/moonstation14/gun_presentation_bindings.json").getAsJsonObject("items");
        var resolved=PrototypeResolver.resolve(BotanyCatalogCandidateTest.mainResourcesAll("loose_object_profile"),
                ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        int cases=0;
        for(var entry:bindings.entrySet()) for(boolean left:new boolean[]{false,true}) for(boolean slim:new boolean[]{false,true}) {
            String id=entry.getKey(); var o=entry.getValue().getAsJsonObject();
            var binding=new GunPresentationBindings.Binding(GunMountTransformTest.vec(o,"bore"),GunMountTransformTest.vec(o,"up"),
                    GunMountTransformTest.vec(o,"grip"),o.has("support")?GunMountTransformTest.vec(o,"support"):null,GunMountTransformTest.vec(o,"muzzle"));
            float scale=LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,resolved.get(ResourceLocation.fromNamespaceAndPath("moonstation14",id))).getOrThrow().modelScale();
            var display=GunMountTransformTest.resolveModel("moonstation14:item/"+id).getAsJsonObject("display");
            PoseStack nativeFrame=new PoseStack(); BodyAppearanceItemRenderer.applyItemGripTransform(nativeFrame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
            GunMountTransformTest.decodeTransform(display.getAsJsonObject(left?"thirdperson_lefthand":"thirdperson_righthand")).apply(left,nativeFrame);
            nativeFrame.translate(-.5,-.5,-.5);
            var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
            var model=new ArticulatedHarnessModel<net.minecraft.world.entity.LivingEntity>(root,slim);
            model.rightArm.xRot=.19f; model.leftArm.xRot=-.11f;
            model.forearm(HumanoidArm.RIGHT).xRot=.27f; model.forearm(HumanoidArm.LEFT).xRot=-.21f;
            HumanoidArm arm=left?HumanoidArm.LEFT:HumanoidArm.RIGHT;
            PoseStack hand=new PoseStack(); model.translateToHand(arm,hand);
            Matrix4f nativeT=new Matrix4f(nativeFrame.last().pose());
            PoseStack mount=new PoseStack(); GunMountTransform.applyCorrection(mount,nativeT,binding,scale,new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,-1));
            Matrix4f rootMatrix=new Matrix4f().translation(31,17,-9).rotateY(.21f);
            Matrix4f world=new Matrix4f(rootMatrix).mul(hand.last().pose()).mul(mount.last().pose()).mul(nativeT);
            Vec3 camera=new Vec3(31,17,-9);
            Matrix4f relative=new Matrix4f().translation((float)-camera.x,(float)-camera.y,(float)-camera.z).mul(world);
            var frame=ActualMuzzleFrame.capture(relative,binding,camera,1);
            Vector3f muzzle=world.transformPosition(new Vector3f((float)binding.muzzle().x(),(float)binding.muzzle().y(),(float)binding.muzzle().z()));
            Vec3 expectedMuzzle=new Vec3(muzzle.x,muzzle.y,muzzle.z);
            assertEquals(expectedMuzzle.x,frame.muzzle().x,1e-5,id);
            assertEquals(expectedMuzzle.y,frame.muzzle().y,1e-5,id);
            assertEquals(expectedMuzzle.z,frame.muzzle().z,1e-5,id);
            assertEquals(1,frame.bore().length(),1e-5); assertEquals(1,frame.up().length(),1e-5);
            Vector3f nativeGrip=nativeT.transformPosition(new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z()));
            Vector3f nativeMuzzle=nativeT.transformPosition(new Vector3f((float)binding.muzzle().x(),(float)binding.muzzle().y(),(float)binding.muzzle().z()));
            Vector3f worldGrip=world.transformPosition(new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z()));
            assertEquals(nativeGrip.distance(nativeMuzzle)*scale,new Vector3f(muzzle).sub(worldGrip).length(),1e-4,id+" left="+left+" slim="+slim); cases++;
        }
        assertEquals(32,cases);
    }
}
