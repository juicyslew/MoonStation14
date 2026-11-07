package com.juicyslew.moonstation14.ms14.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.block.model.ItemTransform;

import static org.junit.jupiter.api.Assertions.*;

class GunMountTransformTest {
    private static void close(Vector3f a, Vector3f b) {
        assertEquals(a.x(),b.x(),1e-5); assertEquals(a.y(),b.y(),1e-5); assertEquals(a.z(),b.z(),1e-5);
    }

    @Test void targetGripCorrectionKeepsNativeGripAsScalePivotAndMovesItToDestination() {
        var binding=new GunPresentationBindings.Binding(new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,1),
                new GunArmAimMath.Vec(.2,.3,.1),new GunArmAimMath.Vec(.5,.2,0),new GunArmAimMath.Vec(1,0,0));
        Matrix4f fullT=new Matrix4f().translation(.1f,.2f,.3f);
        var destination=new GunArmAimMath.Vec(-.25,.7,.15);
        for(float scale:new float[]{.7f,1f}) {
            PoseStack correction=new PoseStack();
            GunMountTransform.applyCorrection(correction,fullT,binding,scale,
                    new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,1),destination);
            Matrix4f composed=new Matrix4f(correction.last().pose()).mul(fullT);
            var grip=composed.transformPosition(new Vector3f(.2f,.3f,.1f));
            assertEquals(destination.x(),grip.x,1e-5); assertEquals(destination.y(),grip.y,1e-5); assertEquals(destination.z(),grip.z,1e-5);
            Vector3f bore=composed.transformDirection(new Vector3f(0,1,0)).normalize();
            assertEquals(0,bore.x,1e-5); assertEquals(1,bore.y,1e-5); assertEquals(0,bore.z,1e-5);
        }
    }

    @Test void resourceBindingsUseResolvedNativeThirdPersonTransformsBothHandings() throws Exception {
        JsonObject bindings=resourceJson("assets/moonstation14/gun_presentation_bindings.json").getAsJsonObject("items");
        assertEquals(8,bindings.size());
        for(var entry:bindings.entrySet()) {
            JsonObject item=entry.getValue().getAsJsonObject();
            var binding=new GunPresentationBindings.Binding(vec(item,"bore"),vec(item,"up"),vec(item,"grip"),
                    item.has("support")?vec(item,"support"):null,vec(item,"muzzle"));
            JsonObject root=resolveModel("moonstation14:item/"+entry.getKey());
            JsonObject display=root.getAsJsonObject("display");
            for(boolean left:new boolean[]{false,true}) {
                String key=left?"thirdperson_lefthand":"thirdperson_righthand";
                assertTrue(display.has(key),entry.getKey()+" missing "+key);
                ItemTransform transform=decodeTransform(display.getAsJsonObject(key));
                PoseStack nativeFrame=new PoseStack();
                BodyAppearanceItemRenderer.applyItemGripTransform(nativeFrame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
                transform.apply(left,nativeFrame);
                nativeFrame.translate(-.5,-.5,-.5);
                Matrix4f fullT=new Matrix4f(nativeFrame.last().pose());
                Vector3f grip=new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z());
                Vector3f pivot=fullT.transformPosition(new Vector3f(grip));
                PoseStack correction=new PoseStack();
                GunMountTransform.applyCorrection(correction,fullT,binding,1,
                        new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,-1));
                Matrix4f composed=new Matrix4f(correction.last().pose()).mul(fullT);
                close(pivot,composed.transformPosition(new Vector3f(grip)));
                Vector3f bore=composed.transformDirection(new Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z())).normalize();
                Vector3f up=composed.transformDirection(new Vector3f((float)binding.up().x(),(float)binding.up().y(),(float)binding.up().z())).normalize();
                close(new Vector3f(0,1,0),bore); close(new Vector3f(0,0,-1),up);
            }
        }
    }

    @Test void sourceSidecarsMatchEveryAuthoredBindingAnchor() throws Exception {
        JsonObject bindings=resourceJson("assets/moonstation14/gun_presentation_bindings.json").getAsJsonObject("items");
        JsonObject reference=resourceJson("guns-sprint/artist-anchor-reference.json");
        JsonObject referenceItems=reference.getAsJsonObject("items");
        for(var entry:referenceItems.entrySet()) {
            String id=entry.getKey(); JsonObject expected=entry.getValue().getAsJsonObject();
            JsonObject actual=bindings.getAsJsonObject(id);
            for(String anchor:new String[]{"grip","support","muzzle"}) if(expected.has(anchor)) {
                var source=expected.getAsJsonArray(anchor);
                if(expected.get("source").getAsString().equals("energy_svalinn_r3")) comparePixelAnchor(actual,anchor,source);
                else compareBinding(actual,anchor,source);
            }
        }
        for(String id:bindings.keySet()) {
            String obj="assets/moonstation14/models/item/guns/"+id+".obj";
            if(id.equals("weapon_laser_gun")) obj="assets/moonstation14/models/item/guns/weapon_laser_gun_charge4.obj";
            if(id.equals("weapon_energy_shotgun")) obj="assets/moonstation14/models/item/guns/weapon_energy_shotgun_charge4.obj";
            if(id.equals("weapon_laser_svalinn")) obj="assets/moonstation14/models/item/guns/weapon_laser_svalinn_charge4.obj";
            assertAnchorsWithinObj(bindings.getAsJsonObject(id),obj);
        }
    }
    private static void assertAnchorsWithinObj(JsonObject binding,String resource) throws Exception {
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        try(var input=GunMountTransformTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input,resource);
            try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))) {
                for(String line;(line=reader.readLine())!=null;) if(line.startsWith("v ")) {
                    String[] fields=line.trim().split("\\s+");
                    for(int i=0;i<3;i++) { double v=Double.parseDouble(fields[i+1]); min[i]=Math.min(min[i],v); max[i]=Math.max(max[i],v); }
                }
            }
        }
        for(String key:new String[]{"grip","support","muzzle"}) if(binding.has(key)) {
            double[] p=numbers(binding.getAsJsonArray(key));
            for(int i=0;i<3;i++) assertTrue(p[i]>=min[i]-1e-6 && p[i]<=max[i]+1e-6,
                    resource+" "+key+" outside imported OBJ AABB axis "+i);
        }
    }
    private static void comparePixelAnchor(JsonObject binding,String name,com.google.gson.JsonArray source) {
        assertArrayEquals(new double[]{(source.get(0).getAsDouble()+8)/16,source.get(1).getAsDouble()/16,
                        (source.get(2).getAsDouble()+8)/16},numbers(binding.getAsJsonArray(name)),1e-9,
                "source model_spec anchor "+name);
    }
    private static void compareBinding(JsonObject binding,String name,com.google.gson.JsonArray source) {
        assertArrayEquals(numbers(source),numbers(binding.getAsJsonArray(name)),1e-9,"source sidecar anchor "+name);
    }
    private static double[] numbers(com.google.gson.JsonArray a) {
        return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};
    }
    @Test void authoredSupportAnchorsAreReachableFromCrossbowSupportArm() throws Exception {
        java.util.List<org.junit.jupiter.api.function.Executable> checks=new java.util.ArrayList<>();
        JsonObject bindings=resourceJson("assets/moonstation14/gun_presentation_bindings.json").getAsJsonObject("items");
        for(String name:new String[]{"weapon_rifle_lecter","weapon_shotgun_kammerer","weapon_sniper_mosin","weapon_energy_shotgun"}) {
            JsonObject source=bindings.getAsJsonObject(name);
            var binding=new GunPresentationBindings.Binding(vec(source,"bore"),vec(source,"up"),vec(source,"grip"),
                    vec(source,"support"),vec(source,"muzzle"));
            JsonObject root=resolveModel("moonstation14:item/"+name);
            JsonObject display=root.getAsJsonObject("display");
            for(boolean left:new boolean[]{false,true}) for(boolean slim:new boolean[]{false,true}) {
                String context=left?"thirdperson_lefthand":"thirdperson_righthand";
                ItemTransform itemTransform=decodeTransform(display.getAsJsonObject(context));
                var mesh=net.minecraft.client.model.PlayerModel.createMesh(
                        net.minecraft.client.model.geom.builders.CubeDeformation.NONE,slim);
                var body=new net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity>(
                        net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot(),slim);
                net.minecraft.client.model.AnimationUtils.animateCrossbowHold(body.rightArm,body.leftArm,body.head,!left);
                var active=left?net.minecraft.world.entity.HumanoidArm.LEFT:net.minecraft.world.entity.HumanoidArm.RIGHT;
                var supportArm=left?net.minecraft.world.entity.HumanoidArm.RIGHT:net.minecraft.world.entity.HumanoidArm.LEFT;
                var main=left?body.leftArm:body.rightArm;
                PoseStack nativeFrame=new PoseStack();
                BodyAppearanceItemRenderer.applyItemGripTransform(nativeFrame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
                itemTransform.apply(left,nativeFrame); nativeFrame.translate(-.5,-.5,-.5);
                float scale=name.equals("weapon_energy_shotgun")?.7f:1f;
                float natural=left?.3f:-.3f;
                java.util.List<GunMountTransform.SupportContact> sampled=new java.util.ArrayList<>();
                var contact=GunMountTransform.solveSupportContact(natural,left?0f:-.85f,left?.85f:0f,.01f,yaw -> {
                    main.yRot=body.head.yRot+yaw;
                    var targets=GunMountTransform.headAimTargets(body,active);
                    var segment=GunMountTransform.supportWeaponSegment(binding,nativeFrame.last().pose(),body,active,
                            supportArm,scale,targets);
                    sampled.add(GunMountTransform.contactOnSegment(yaw,segment));
                    return segment;
                });
                double bestFraction=sampled.stream().filter(GunMountTransform.SupportContact::reached)
                        .mapToDouble(GunMountTransform.SupportContact::fraction).max().orElse(-1);
                double endpointGap=Double.NaN;
                if(contact.reached()) {
                    main.yRot=body.head.yRot+contact.mainYaw();
                    var rotation=GunMountTransform.supportArmRotation(contactPointShoulder(body,supportArm),contact.point(),supportArm);
                    endpointGap=GunMountTransform.supportReachGap(body,supportArm,rotation,contact.point());
                    var forward=rotate(new GunArmAimMath.Rotation(main.xRot,main.yRot,main.zRot),new GunArmAimMath.Vec(0,1,0));
                    checks.add(() -> assertTrue(forward.dot(new GunArmAimMath.Vec(0,0,-1))>0,
                            "main arm points backward at yaw "+contact.mainYaw()+" for "+name));
                    checks.add(() -> assertTrue(Math.abs(main.zRot)<.2,"excessive main-arm roll for "+name));
                }
                String diagnostic=name+" "+(left?"left":"right")+" "+(slim?"slim":"regular")+
                        " yaw="+contact.mainYaw()+" fraction="+contact.fraction()+" segmentGap="+contact.gap()+" endpointGap="+endpointGap;
                System.out.println("support-contact: "+diagnostic);
                final boolean reached=contact.reached(); final double finalGap=endpointGap;
                checks.add(() -> assertTrue(reached && contact.fraction()>=.1 && contact.fraction()<=1 && finalGap<=.03
                        && Math.abs(contact.fraction()-bestFraction)<1e-6,diagnostic+" bestFraction="+bestFraction));
            }
        }
        assertAll(checks);
    }

    private static GunArmAimMath.Vec contactPointShoulder(net.minecraft.client.model.HumanoidModel<?> body,
                                                           net.minecraft.world.entity.HumanoidArm arm) {
        PoseStack frame=new PoseStack();
        if(body instanceof net.minecraft.client.model.PlayerModel<?> player) player.translateToHand(arm,frame);
        else (arm==net.minecraft.world.entity.HumanoidArm.LEFT?body.leftArm:body.rightArm).translateAndRotate(frame);
        Vector3f point=frame.last().pose().transformPosition(new Vector3f());
        return new GunArmAimMath.Vec(point.x,point.y,point.z);
    }

    static GunArmAimMath.Vec vec(JsonObject object,String key) {
        var a=object.getAsJsonArray(key);
        return new GunArmAimMath.Vec(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
    }
    static JsonObject resolveModel(String id) throws Exception {
        String[] parts=id.split(":",2);
        JsonObject model=resourceJson("assets/"+parts[0]+"/models/"+parts[1]+".json");
        if(model.has("display")) return model;
        if(model.has("parent")) return resolveModel(model.get("parent").getAsString());
        throw new AssertionError("No inherited display transform for "+id);
    }
    static JsonObject resourceJson(String path) throws Exception {
        try(var input=GunMountTransformTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input,path);
            try(var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
    static ItemTransform decodeTransform(JsonObject o) {
        return new ItemTransform(vector(o,"rotation",0,0,0),vector(o,"translation",0,0,0),vector(o,"scale",1,1,1));
    }
    private static Vector3f vector(JsonObject o,String key,float x,float y,float z) {
        if(!o.has(key)) return new Vector3f(x,y,z);
        var a=o.getAsJsonArray(key);
        float factor=key.equals("translation")?1/16f:1f;
        return new Vector3f(a.get(0).getAsFloat()*factor,a.get(1).getAsFloat()*factor,a.get(2).getAsFloat()*factor);
    }

    @Test void activeAimUsesHeadAxesConvertedIntoArmSpace() {
        GunPresentationBindings.Binding[] frames = new GunPresentationBindings.Binding[] {
                binding(0,1,0), binding(0,1,0), binding(-1,0,0), binding(-1,0,0),
                binding(-1,0,0), binding(1,0,0), binding(1,0,0), binding(1,0,0)
        };
        var headForward=rotate(new GunArmAimMath.Rotation(.38f,-.57f,0),new GunArmAimMath.Vec(0,0,-1));
        var headUp=rotate(new GunArmAimMath.Rotation(.38f,-.57f,0),new GunArmAimMath.Vec(0,-1,0));
        var armRotation=new GunArmAimMath.Rotation(-1.13f,.21f,-.08f);
        var targets=GunMountTransform.armSpaceTargets(headForward,headUp,armRotation);
        var expectedBore=rotate(armRotation,targets.bore()).normalized();
        var expectedUp=rotate(armRotation,targets.up()).normalized();
        close(new Vector3f((float)headForward.x(),(float)headForward.y(),(float)headForward.z()),
                new Vector3f((float)expectedBore.x(),(float)expectedBore.y(),(float)expectedBore.z()));
        close(new Vector3f((float)headUp.x(),(float)headUp.y(),(float)headUp.z()),
                new Vector3f((float)expectedUp.x(),(float)expectedUp.y(),(float)expectedUp.z()));
        for(var binding:frames) for(boolean left:new boolean[]{false,true}) {
            PoseStack frame=new PoseStack();
            BodyAppearanceItemRenderer.applyItemGripTransform(frame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
            frame.mulPose(Axis.YP.rotationDegrees(left?17:-23)); frame.translate(-.5,-.5,-.5);
            Matrix4f fullT=new Matrix4f(frame.last().pose());
            Vector3f grip=new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z());
            Vector3f pivot=fullT.transformPosition(new Vector3f(grip));
            PoseStack correction=new PoseStack();
            GunMountTransform.applyCorrection(correction,fullT,binding,1,targets.bore(),targets.up());
            Matrix4f local=new Matrix4f(correction.last().pose()).mul(fullT);
            close(pivot,local.transformPosition(new Vector3f(grip)));
            Vector3f bore=local.transformDirection(new Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z())).normalize();
            Vector3f up=local.transformDirection(new Vector3f((float)binding.up().x(),(float)binding.up().y(),(float)binding.up().z())).normalize();
            close(new Vector3f((float)targets.bore().x(),(float)targets.bore().y(),(float)targets.bore().z()).normalize(),bore);
            close(new Vector3f((float)targets.up().x(),(float)targets.up().y(),(float)targets.up().z()).normalize(),up);
            PoseStack mainPose=new PoseStack();
            mainPose.mulPose(Axis.ZP.rotation(armRotation.z())); mainPose.mulPose(Axis.YP.rotation(armRotation.y()));
            mainPose.mulPose(Axis.XP.rotation(armRotation.x()));
            Matrix4f inModel=new Matrix4f(mainPose.last().pose()).mul(local);
            Vector3f headBore=inModel.transformDirection(new Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z())).normalize();
            Vector3f headUpActual=inModel.transformDirection(new Vector3f((float)binding.up().x(),(float)binding.up().y(),(float)binding.up().z())).normalize();
            close(new Vector3f((float)headForward.x(),(float)headForward.y(),(float)headForward.z()),headBore);
            close(new Vector3f((float)headUp.x(),(float)headUp.y(),(float)headUp.z()),headUpActual);
        }
    }

    @Test void proneWorldAimComposesRootRollGroundingDisplayAndMountCorrection() throws Exception {
        JsonObject bindings=resourceJson("assets/moonstation14/gun_presentation_bindings.json").getAsJsonObject("items");
        for(var entry:bindings.entrySet()) {
            JsonObject o=entry.getValue().getAsJsonObject();
            var binding=new GunPresentationBindings.Binding(vec(o,"bore"),vec(o,"up"),vec(o,"grip"),
                    o.has("support")?vec(o,"support"):null,vec(o,"muzzle"));
            JsonObject display=resolveModel("moonstation14:item/"+entry.getKey()).getAsJsonObject("display");
            for(boolean left:new boolean[]{false,true}) for(boolean faceUp:new boolean[]{false,true})
                for(float yaw:new float[]{-1.1f,.6f}) for(float pitch:new float[]{-.35f,.42f}) {
                    PoseStack nativeFrame=new PoseStack();
                    BodyAppearanceItemRenderer.applyItemGripTransform(nativeFrame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
                    decodeTransform(display.getAsJsonObject(left?"thirdperson_lefthand":"thirdperson_righthand")).apply(left,nativeFrame);
                    nativeFrame.translate(-.5,-.5,-.5);
                    var worldLook=new net.minecraft.world.phys.Vec3(Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),-Math.cos(yaw)*Math.cos(pitch));
                    float facing=37;
                    var localLook=com.juicyslew.moonstation14.ms14.standing.PronePoseMath.worldDirectionToModel(worldLook,facing,faceUp);
                    PoseStack head=new PoseStack(); head.mulPose(com.mojang.math.Axis.XP.rotation(.2f));
                    Vector3f headUp=head.last().pose().transformDirection(new Vector3f(0,-1,0));
                    var armAngles=new GunArmAimMath.Rotation(-1.12f,left?.24f:-.24f,.03f);
                    var target=GunMountTransform.armSpaceTargets(new GunArmAimMath.Vec(localLook.x,localLook.y,localLook.z),
                            new GunArmAimMath.Vec(headUp.x,headUp.y,headUp.z),armAngles);
                    PoseStack correction=new PoseStack();
                    GunMountTransform.applyCorrection(correction,nativeFrame.last().pose(),binding,1,target.bore(),target.up());
                    PoseStack fullRoot=new PoseStack();
                    fullRoot.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-facing));
                    com.juicyslew.moonstation14.ms14.standing.PronePoseMath.applyRenderPose(fullRoot,0,1,faceUp);
                    fullRoot.scale(-1,-1,1);
                    PoseStack arm=new PoseStack(); arm.mulPose(Axis.ZP.rotation(armAngles.z()));
                    arm.mulPose(Axis.YP.rotation(armAngles.y())); arm.mulPose(Axis.XP.rotation(armAngles.x()));
                    Matrix4f world=new Matrix4f(fullRoot.last().pose()).mul(arm.last().pose())
                            .mul(correction.last().pose()).mul(nativeFrame.last().pose());
                    Vector3f actual=world.transformDirection(new Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z())).normalize();
                    close(new Vector3f((float)worldLook.x,(float)worldLook.y,(float)worldLook.z).normalize(),actual);
                    Vector3f actualUp=world.transformDirection(new Vector3f((float)binding.up().x(),(float)binding.up().y(),(float)binding.up().z())).normalize();
                    Vector3f localBore=new Vector3f((float)localLook.x,(float)localLook.y,(float)localLook.z).normalize();
                    Vector3f projectedHeadUp=new Vector3f(headUp).fma(-headUp.dot(localBore),localBore).normalize();
                    Vector3f expectedUp=new Matrix4f(fullRoot.last().pose()).transformDirection(projectedHeadUp).normalize();
                    close(expectedUp,actualUp);
                    Vector3f grip=new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z());
                    Vector3f before=nativeFrame.last().pose().transformPosition(new Vector3f(grip));
                    Vector3f after=new Matrix4f(correction.last().pose()).mul(nativeFrame.last().pose()).transformPosition(new Vector3f(grip));
                    close(before,after);
                }
        }
    }

    @Test void proneLimbAndSupportContactsFollowWorldAimForBothRootRolls() throws Exception {
        JsonObject bindings=resourceJson("assets/moonstation14/gun_presentation_bindings.json").getAsJsonObject("items");
        for(boolean faceUp:new boolean[]{false,true}) for(boolean left:new boolean[]{false,true})
            for(boolean slim:new boolean[]{false,true}) {
                var arm=left?net.minecraft.world.entity.HumanoidArm.LEFT:net.minecraft.world.entity.HumanoidArm.RIGHT;
                var look=new net.minecraft.world.phys.Vec3(.31,-.22,-.91).normalize();
                var mesh=net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,slim);
                var body=new net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity>(
                        net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot(),slim);
                var supportArm=left?net.minecraft.world.entity.HumanoidArm.RIGHT:net.minecraft.world.entity.HumanoidArm.LEFT;
                PoseStack shoulderRoot=new PoseStack(); shoulderRoot.mulPose(Axis.YP.rotationDegrees(180-41));
                com.juicyslew.moonstation14.ms14.standing.PronePoseMath.applyRenderPose(shoulderRoot,0,1,faceUp); shoulderRoot.scale(-1,-1,1);
                PoseStack mainShoulder=new PoseStack(),supportShoulder=new PoseStack();
                body.translateToHand(arm,mainShoulder); body.translateToHand(supportArm,supportShoulder);
                Vector3f toward=new Matrix4f(shoulderRoot.last().pose()).transformDirection(
                        supportShoulder.last().pose().getTranslation(new Vector3f()).sub(mainShoulder.last().pose().getTranslation(new Vector3f())));
                float sign=GunMountTransform.inwardWorldYawSign(look,new net.minecraft.world.phys.Vec3(toward.x,toward.y,toward.z));
                var rotation=GunMountTransform.proneArmRotation(look,41,faceUp,arm,sign*.25f);
                var handCenter=new GunArmAimMath.Vec(left?1.0/16:-1.0/16,10.0/16,0);
                var localHand=rotate(rotation,handCenter);
                PoseStack root=new PoseStack(); root.mulPose(Axis.YP.rotationDegrees(180-41));
                com.juicyslew.moonstation14.ms14.standing.PronePoseMath.applyRenderPose(root,0,1,faceUp);
                root.scale(-1,-1,1);
                PoseStack limb=new PoseStack(); limb.mulPose(Axis.ZP.rotation(rotation.z()));
                limb.mulPose(Axis.YP.rotation(rotation.y())); limb.mulPose(Axis.XP.rotation(rotation.x()));
                Vector3f worldHand=new Matrix4f(root.last().pose()).transformDirection(new Vector3f(
                        (float)localHand.x(),(float)localHand.y(),(float)localHand.z())).normalize();
                float yawOffset=sign*.25f;
                Vector3f desired=com.mojang.math.Axis.YP.rotation(yawOffset).transform(new Vector3f((float)look.x,(float)look.y,(float)look.z)).normalize();
                assertTrue(worldHand.dot(desired)>.9999,"prone main hand direction left="+left+" faceUp="+faceUp);
                assertTrue(desired.dot(new Vector3f((float)look.x,(float)look.y,(float)look.z))>0,
                        "prone main arm must remain forward of the aim plane");
            }
        java.util.List<org.junit.jupiter.api.function.Executable> checks=new java.util.ArrayList<>();
        for(String name:new String[]{"weapon_rifle_lecter","weapon_shotgun_kammerer","weapon_sniper_mosin","weapon_energy_shotgun"})
            proneSupportContacts(bindings,name,checks);
        assertAll(checks);
    }

    private static void proneSupportContacts(JsonObject bindings,String name,
                                              java.util.List<org.junit.jupiter.api.function.Executable> checks) throws Exception {
        JsonObject source=bindings.getAsJsonObject(name);
        var binding=new GunPresentationBindings.Binding(vec(source,"bore"),vec(source,"up"),vec(source,"grip"),vec(source,"support"),vec(source,"muzzle"));
        JsonObject display=resolveModel("moonstation14:item/"+name).getAsJsonObject("display");
        for(boolean faceUp:new boolean[]{false,true}) for(boolean left:new boolean[]{false,true}) for(boolean slim:new boolean[]{false,true}) {
            var active=left?net.minecraft.world.entity.HumanoidArm.LEFT:net.minecraft.world.entity.HumanoidArm.RIGHT;
            var supportArm=left?net.minecraft.world.entity.HumanoidArm.RIGHT:net.minecraft.world.entity.HumanoidArm.LEFT;
            var mesh=net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,slim);
            var body=new net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity>(
                    net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot(),slim);
            var main=left?body.leftArm:body.rightArm;
            var look=new net.minecraft.world.phys.Vec3(.31,-.22,-.91).normalize();
            float facing=41;
            PoseStack nativeFrame=new PoseStack();
            BodyAppearanceItemRenderer.applyItemGripTransform(nativeFrame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
            decodeTransform(display.getAsJsonObject(left?"thirdperson_lefthand":"thirdperson_righthand")).apply(left,nativeFrame);
            nativeFrame.translate(-.5,-.5,-.5);
            float scale=name.equals("weapon_energy_shotgun")?.7f:1f;
            var localLook=com.juicyslew.moonstation14.ms14.standing.PronePoseMath.worldDirectionToModel(look,facing,faceUp);
            var targetUp=new net.minecraft.world.phys.Vec3(0,-1,0);
            PoseStack root=new PoseStack(); root.mulPose(Axis.YP.rotationDegrees(180-facing));
            com.juicyslew.moonstation14.ms14.standing.PronePoseMath.applyRenderPose(root,0,1,faceUp); root.scale(-1,-1,1);
            PoseStack mainShoulder=new PoseStack(),supportShoulder=new PoseStack();
            body.translateToHand(active,mainShoulder); body.translateToHand(supportArm,supportShoulder);
            Vector3f toward=new Matrix4f(root.last().pose()).transformDirection(
                    supportShoulder.last().pose().getTranslation(new Vector3f()).sub(mainShoulder.last().pose().getTranslation(new Vector3f())));
            float sign=GunMountTransform.inwardWorldYawSign(look,new net.minecraft.world.phys.Vec3(toward.x,toward.y,toward.z));
            var contact=GunMountTransform.solveSupportContact(.15f,0f,1.1f,.01f,offset -> {
                var rotation=GunMountTransform.proneArmRotation(look,facing,faceUp,active,sign*offset);
                main.xRot=rotation.x(); main.yRot=rotation.y(); main.zRot=rotation.z();
                var targets=GunMountTransform.armSpaceTargets(new GunArmAimMath.Vec(localLook.x,localLook.y,localLook.z),
                        new GunArmAimMath.Vec(targetUp.x,targetUp.y,targetUp.z),rotation);
                return GunMountTransform.supportWeaponSegment(binding,nativeFrame.last().pose(),body,active,supportArm,scale,targets);
            });
            double endpointGap=Double.NaN;
            double forwardDot=Double.NaN;
            if(contact.reached()) {
                var finalRotation=GunMountTransform.proneArmRotation(look,facing,faceUp,active,sign*contact.mainYaw());
                main.xRot=finalRotation.x(); main.yRot=finalRotation.y(); main.zRot=finalRotation.z();
                var handCenter=new GunArmAimMath.Vec(left?1.0/16:-1.0/16,10.0/16,0);
                var handLocal=rotate(finalRotation,handCenter);
                Vector3f handWorld=new Matrix4f(root.last().pose()).transformDirection(new Vector3f(
                        (float)handLocal.x(),(float)handLocal.y(),(float)handLocal.z())).normalize();
                forwardDot=handWorld.dot((float)look.x,(float)look.y,(float)look.z);
                var shoulder=contactPointShoulder(body,supportArm);
                var supportRotation=GunMountTransform.supportArmRotation(shoulder,contact.point(),supportArm);
                endpointGap=GunMountTransform.supportReachGap(body,supportArm,supportRotation,contact.point());
            }
            String diagnostic=name+" "+(left?"left":"right")+" "+(faceUp?"back":"stomach")+" "+(slim?"slim":"regular")+
                    " worldYawSign="+sign+" yaw="+contact.mainYaw()+" point="+contact.point()+" fraction="+contact.fraction()+
                    " segmentGap="+contact.gap()+" endpointGap="+endpointGap+" forwardDot="+forwardDot;
            System.out.println("prone-support: "+diagnostic);
            final double gap=endpointGap, forward=forwardDot; final boolean reached=contact.reached();
            checks.add(() -> assertTrue(reached && contact.fraction()>=.1 && gap<=.03 && forward>.4
                    && contact.mainYaw()<=1.1+1e-5,diagnostic));
        }
    }

    @Test void supportArmPointsAtAuthoredContactWithoutCopyingMainEuler() {
        var shoulder=new GunArmAimMath.Vec(.24,1.3,.12);
        var contact=new GunArmAimMath.Vec(-.2,.8,.7);
        var rotation=GunMountTransform.supportArmRotation(shoulder,contact);
        var aimed=rotate(rotation,new GunArmAimMath.Vec(0,1,0)).normalized();
        var wanted=contact.add(shoulder.scale(-1)).normalized();
        assertEquals(1,wanted.dot(aimed),1e-5);
        var main=new GunArmAimMath.Rotation(-1.1f,.2f,-.1f);
        assertTrue(Math.abs(rotation.x()-main.x())+Math.abs(rotation.y()-main.y())+Math.abs(rotation.z()-main.z())>.1);
    }

    @Test void supportSolverChoosesFarthestReachableAuthoredSegmentPoint() {
        var segment=new GunMountTransform.WeaponSegment(new GunArmAimMath.Vec(0,0,0),
                new GunArmAimMath.Vec(0,2,0),new GunArmAimMath.Vec(0,0,0),1);
        var contact=GunMountTransform.contactOnSegment(.4f,segment);
        assertTrue(contact.reached());
        assertEquals(.5,contact.fraction(),1e-6);
        assertEquals(0,contact.gap(),1e-6);
    }

    private static GunArmAimMath.Vec rotate(GunArmAimMath.Rotation a,GunArmAimMath.Vec v) {
        double cx=Math.cos(a.x()),sx=Math.sin(a.x()),cy=Math.cos(a.y()),sy=Math.sin(a.y()),cz=Math.cos(a.z()),sz=Math.sin(a.z());
        double x1=v.x(),y1=cx*v.y()-sx*v.z(),z1=sx*v.y()+cx*v.z();
        double x2=cy*x1+sy*z1,y2=y1,z2=-sy*x1+cy*z1;
        return new GunArmAimMath.Vec(cz*x2-sz*y2,sz*x2+cz*y2,z2);
    }

    @Test void correctionKeepsGripPivotAndAlignsFrameAfterHandAndDisplayTransforms() {
        // The eight source frames represented in gun_presentation_bindings.json.
        GunPresentationBindings.Binding[] frames = new GunPresentationBindings.Binding[] {
                binding(0,1,0), binding(0,1,0), binding(-1,0,0), binding(-1,0,0),
                binding(-1,0,0), binding(1,0,0), binding(1,0,0), binding(1,0,0)
        };
        for (var binding : frames) for (boolean left : new boolean[]{false,true}) {
            PoseStack nativeFrame=new PoseStack();
            BodyAppearanceItemRenderer.applyItemGripTransform(nativeFrame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
            nativeFrame.mulPose(Axis.YP.rotationDegrees(left?17:-23)); // baked handed display orientation
            nativeFrame.translate(-.5,-.5,-.5);
            Matrix4f fullT=new Matrix4f(nativeFrame.last().pose());
            Vector3f grip=new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z());
            Vector3f expected=fullT.transformPosition(new Vector3f(grip));
            PoseStack correction=new PoseStack();
            GunMountTransform.applyCorrection(correction,fullT,binding,1,
                    new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,-1));
            Matrix4f actual=new Matrix4f(correction.last().pose()).mul(fullT);
            close(expected,actual.transformPosition(new Vector3f(grip)));
            Vector3f bore=actual.transformDirection(new Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z())).normalize();
            Vector3f up=actual.transformDirection(new Vector3f((float)binding.up().x(),(float)binding.up().y(),(float)binding.up().z())).normalize();
            close(new Vector3f(0,1,0),bore); close(new Vector3f(0,0,-1),up);
        }
    }

    private static GunPresentationBindings.Binding binding(double ux,double uy,double uz) {
        return new GunPresentationBindings.Binding(new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(ux,uy,uz),
                new GunArmAimMath.Vec(.5,.4,.3),null,new GunArmAimMath.Vec(0,0,1));
    }
}
