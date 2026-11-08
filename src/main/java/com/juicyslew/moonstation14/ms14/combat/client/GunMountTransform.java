package com.juicyslew.moonstation14.ms14.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/** Shared correction applied before the existing hand grip/display transforms. */
public final class GunMountTransform {
    private GunMountTransform() { }
    public static boolean applyHeldMount(LivingEntity entity, HumanoidArm arm, HumanoidModel<?> bodyModel, PoseStack pose) {
        if (entity==null) return false;
        var snapshot=com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking.clientCache()
                .get(entity.level().dimension().location(),entity.getUUID(),entity.getId()).orElse(null);
        if(snapshot==null) return false;
        String handId=arm==HumanoidArm.LEFT?"left":"right";
        var hand=snapshot.hands().stream().filter(h->h.id().equalsIgnoreCase(handId)).findFirst().orElse(null);
        ItemStack stack=hand==null||hand.item()==null?ItemStack.EMPTY:hand.item().stack();
        if(stack.isEmpty()) return false;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        var binding=id==null?null:GunPresentationBindings.find(id);
        if(binding==null) return false;
        var nativeFrame=nativeFrame(entity,stack,arm);
        float resolvedScale=1f;
        try { resolvedScale=com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack,true).visual().modelScale(); }
        catch(RuntimeException ignored) { }
        final float scale=resolvedScale;
        GunArmAimMath.Vec targetBore=new GunArmAimMath.Vec(0,1,0), targetUp=new GunArmAimMath.Vec(0,0,-1);
        var poseContext=snapshot.poseContext();
        boolean active=poseContext!=null && poseContext.activeHand().equalsIgnoreCase(handId);
        boolean combat=com.juicyslew.moonstation14.ms14.combat.CombatModeClient.modeForPresentation(entity);
        var profile=id==null?null:com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.clientCatalog().get(id);
        boolean melee=entity.swinging || bodyModel!=null && bodyModel.attackTime>0;
        boolean admitted=profile!=null && GunArmPosePresentation.admitted(combat,active,
                profile.requiresWield(),poseContext!=null && poseContext.hasValidatedWield(),melee);
        GunArmAimMath.Vec targetGrip=null;
        var posture=GunProneAimPresentation.effectivePosture(entity);
        if(admitted && active && profile.requiresWield() && poseContext.hasValidatedWield()
                && posture!=null && posture.supportedGunAim()
                && bodyModel instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<?>)
            targetGrip=handContactPoint((com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<?>)bodyModel,arm);
        if(admitted && bodyModel!=null && poseContext!=null) {
            var target=headAimTargets(entity,bodyModel,arm);
            targetBore=target.bore(); targetUp=target.up();
        }
        if(targetGrip==null) applyCorrection(pose,nativeFrame.last().pose(),binding,scale,targetBore,targetUp);
        else applyCorrection(pose,nativeFrame.last().pose(),binding,scale,targetBore,targetUp,targetGrip);
        return true;
    }
    static GunArmAimMath.Vec handContactPoint(HumanoidArm arm) {
        return new GunArmAimMath.Vec(arm==HumanoidArm.LEFT?1.0/16:-1.0/16,10.0/16,0);
    }
    static GunArmAimMath.Vec handContactPoint(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<?> model, HumanoidArm arm) {
        return new GunArmAimMath.Vec(arm==HumanoidArm.LEFT?1.0/16:-1.0/16,model.palmEndpointPixels()/16.0,0);
    }

    public record AimTargets(GunArmAimMath.Vec bore, GunArmAimMath.Vec up) { }
    public static PoseStack nativeFrame(LivingEntity entity, ItemStack stack, HumanoidArm arm) {
        var model=Minecraft.getInstance().getItemRenderer().getModel(stack,entity.level(),entity,0);
        boolean left=arm==HumanoidArm.LEFT;
        var context=left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        PoseStack frame=new PoseStack();
        // Same prefix and native baked transform used by BodyAppearanceItemRenderer.
        BodyAppearanceItemRenderer.applyItemGripTransform(frame,left,BodyAppearanceItemRenderer.GripConvention.ARM_PIVOT);
        model.applyTransform(context,frame,left);
        frame.translate(-.5,-.5,-.5);
        return frame;
    }
    public static AimTargets headAimTargets(HumanoidModel<?> model, HumanoidArm arm) {
        PoseStack head=new PoseStack(); model.head.translateAndRotate(head);
        Vector3f f=head.last().pose().transformDirection(new Vector3f(0,0,-1));
        Vector3f u=head.last().pose().transformDirection(new Vector3f(0,-1,0));
        if (model instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<?>)
            return articulatedArmSpaceTargets(model, arm, new GunArmAimMath.Vec(f.x,f.y,f.z), new GunArmAimMath.Vec(u.x,u.y,u.z));
        var main=arm==HumanoidArm.LEFT?model.leftArm:model.rightArm;
        return armSpaceTargets(new GunArmAimMath.Vec(f.x,f.y,f.z),new GunArmAimMath.Vec(u.x,u.y,u.z),
                new GunArmAimMath.Rotation(main.xRot,main.yRot,main.zRot));
    }
    public static AimTargets headAimTargets(LivingEntity entity, HumanoidModel<?> model, HumanoidArm arm) {
        if(entity==null) return headAimTargets(model,arm);
        var posture=GunProneAimPresentation.effectivePosture(entity);
        if(posture==null || !posture.down()) return headAimTargets(model,arm);
        var localLook=com.juicyslew.moonstation14.ms14.standing.PronePoseMath.worldDirectionToModel(
                entity.getLookAngle(),posture.movementFacingYaw(),posture.faceUp());
        PoseStack head=new PoseStack(); model.head.translateAndRotate(head);
        Vector3f up=head.last().pose().transformDirection(new Vector3f(0,-1,0));
        if (model instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<?>)
            return articulatedArmSpaceTargets(model, arm,
                    new GunArmAimMath.Vec(localLook.x,localLook.y,localLook.z), new GunArmAimMath.Vec(up.x,up.y,up.z));
        var main=arm==HumanoidArm.LEFT?model.leftArm:model.rightArm;
        return armSpaceTargets(new GunArmAimMath.Vec(localLook.x,localLook.y,localLook.z),
                new GunArmAimMath.Vec(up.x,up.y,up.z),new GunArmAimMath.Rotation(main.xRot,main.yRot,main.zRot));
    }

    static AimTargets articulatedArmSpaceTargets(HumanoidModel<?> model, HumanoidArm arm,
                                                           GunArmAimMath.Vec forward, GunArmAimMath.Vec up) {
        PoseStack frame = new PoseStack();
        ((net.minecraft.client.model.PlayerModel<?>) model).translateToHand(arm, frame);
        org.joml.Matrix3f inverse = new org.joml.Matrix3f(frame.last().pose()).invert();
        Vector3f f = inverse.transform(new Vector3f((float) forward.x(), (float) forward.y(), (float) forward.z()));
        Vector3f u = inverse.transform(new Vector3f((float) up.x(), (float) up.y(), (float) up.z()));
        return new AimTargets(new GunArmAimMath.Vec(f.x, f.y, f.z), new GunArmAimMath.Vec(u.x, u.y, u.z));
    }

    /** Prone arm Euler pose that aims its actual native hand center along a world-space direction. */
    public static GunArmAimMath.Rotation proneArmRotation(LivingEntity entity, HumanoidModel<?> model,
                                                            HumanoidArm arm, HumanoidArm supportArm,
                                                            float inwardYaw) {
        var posture=GunProneAimPresentation.effectivePosture(entity);
        if(posture==null || !posture.down()) return new GunArmAimMath.Rotation(-1.2f,0,0);
        PoseStack root=proneRoot(posture.movementFacingYaw(),posture.faceUp());
        Vector3f main=armFrame(model,arm).last().pose().transformPosition(new Vector3f());
        Vector3f support=armFrame(model,supportArm).last().pose().transformPosition(new Vector3f());
        Vector3f towardSupport=root.last().pose().transformDirection(support.sub(main));
        float sign=inwardWorldYawSign(entity.getLookAngle(),new net.minecraft.world.phys.Vec3(towardSupport.x,towardSupport.y,towardSupport.z));
        return proneArmRotation(entity.getLookAngle(),posture.movementFacingYaw(),posture.faceUp(),arm,sign*Math.abs(inwardYaw));
    }
    public static GunArmAimMath.Rotation proneArmRotation(net.minecraft.world.phys.Vec3 worldLook,float facing,
                                                            boolean faceUp,HumanoidArm arm,float inwardYaw) {
        var look=worldLook.normalize();
        float worldYaw=inwardYaw;
        Vector3f worldDirection=com.mojang.math.Axis.YP.rotation(worldYaw)
                .transform(new Vector3f((float)look.x,(float)look.y,(float)look.z));
        var localDirection=com.juicyslew.moonstation14.ms14.standing.PronePoseMath.worldDirectionToModel(
                new net.minecraft.world.phys.Vec3(worldDirection.x,worldDirection.y,worldDirection.z),facing,faceUp);
        var localUp=com.juicyslew.moonstation14.ms14.standing.PronePoseMath.worldDirectionToModel(
                new net.minecraft.world.phys.Vec3(0,1,0),facing,faceUp);
        var handCenter=new GunArmAimMath.Vec(arm==HumanoidArm.LEFT?1.0/16:-1.0/16,10.0/16,0);
        return GunArmAimMath.align(handCenter,new GunArmAimMath.Vec(0,0,1),
                new GunArmAimMath.Vec(localDirection.x,localDirection.y,localDirection.z),
                new GunArmAimMath.Vec(localUp.x,localUp.y,localUp.z));
    }
    public static float inwardWorldYawSign(net.minecraft.world.phys.Vec3 worldLook,net.minecraft.world.phys.Vec3 towardSupport) {
        var up=new net.minecraft.world.phys.Vec3(0,1,0);
        var lateral=towardSupport.subtract(up.scale(towardSupport.dot(up))).normalize();
        var look=worldLook.normalize();
        var positive=com.mojang.math.Axis.YP.rotation(.01f).transform(new Vector3f((float)look.x,(float)look.y,(float)look.z));
        var negative=com.mojang.math.Axis.YP.rotation(-.01f).transform(new Vector3f((float)look.x,(float)look.y,(float)look.z));
        return positive.dot((float)lateral.x,(float)lateral.y,(float)lateral.z)
                > negative.dot((float)lateral.x,(float)lateral.y,(float)lateral.z)?1f:-1f;
    }
    private static PoseStack proneRoot(float facing,boolean faceUp) {
        PoseStack root=new PoseStack(); root.mulPose(Axis.YP.rotationDegrees(180-facing));
        com.juicyslew.moonstation14.ms14.standing.PronePoseMath.applyRenderPose(root,0,1,faceUp);
        root.scale(-1,-1,1); return root;
    }
    /** Converts model-space head axes into the selected arm's local frame (Rz*Ry*Rx). */
    public static AimTargets armSpaceTargets(GunArmAimMath.Vec headForward, GunArmAimMath.Vec headUp,
                                             GunArmAimMath.Rotation arm) {
        return new AimTargets(inverseArm(headForward,arm),inverseArm(headUp,arm));
    }

    public static SupportContact solveSupportPose(LivingEntity entity, HumanoidModel<?> bodyModel,
                                                   HumanoidArm activeArm, HumanoidArm supportArm, ItemStack stack) {
        if(entity==null || bodyModel==null || stack==null || stack.isEmpty()) return null;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        var binding=id==null?null:GunPresentationBindings.find(id);
        if(binding==null || binding.support()==null) return null;
        boolean left=activeArm==HumanoidArm.LEFT;
        var main=left?bodyModel.leftArm:bodyModel.rightArm;
        var posture=GunProneAimPresentation.effectivePosture(entity);
        boolean prone=posture!=null && posture.down();
        float natural=prone?.15f:main.yRot-bodyModel.head.yRot;
        Matrix4f fullT=new Matrix4f(nativeFrame(entity,stack,activeArm).last().pose());
        float scaleValue=1f;
        try { scaleValue=com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack,true).visual().modelScale(); }
        catch(RuntimeException ignored) { }
        final float scale=scaleValue;
        var contact=solveSupportContact(natural,prone?0f:left?0f:-.85f,prone?1.1f:left?.85f:0f,.01f,yaw -> {
            if(prone) {
                var rotation=proneArmRotation(entity,bodyModel,activeArm,supportArm,yaw);
                main.xRot=rotation.x(); main.yRot=rotation.y(); main.zRot=rotation.z();
            } else main.yRot=bodyModel.head.yRot+yaw;
            AimTargets targets=headAimTargets(entity,bodyModel,activeArm);
            return supportWeaponSegment(binding,fullT,bodyModel,activeArm,supportArm,scale,targets);
        });
        if(contact!=null) {
            if(prone) {
                var rotation=proneArmRotation(entity,bodyModel,activeArm,supportArm,contact.mainYaw());
                main.xRot=rotation.x(); main.yRot=rotation.y(); main.zRot=rotation.z();
            } else main.yRot=bodyModel.head.yRot+contact.mainYaw();
        }
        return contact;
    }

    public static WeaponSegment supportWeaponSegment(GunPresentationBindings.Binding binding, Matrix4f fullT,
                                                       HumanoidModel<?> model, HumanoidArm activeArm,
                                                       HumanoidArm supportArm, float scale, AimTargets targets) {
        return supportWeaponSegment(binding,fullT,model,activeArm,supportArm,scale,targets,null);
    }
    public static WeaponSegment supportWeaponSegment(GunPresentationBindings.Binding binding, Matrix4f fullT,
                                                       HumanoidModel<?> model, HumanoidArm activeArm,
                                                       HumanoidArm supportArm, float scale, AimTargets targets,
                                                       GunArmAimMath.Vec targetGrip) {
        PoseStack correction=new PoseStack();
        applyCorrection(correction,fullT,binding,scale,targets.bore(),targets.up(),targetGrip);
        PoseStack weapon=armFrame(model,activeArm);
        weapon.last().pose().mul(correction.last().pose()).mul(fullT);
        Vector3f grip=weapon.last().pose().transformPosition(new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z()));
        Vector3f fore=weapon.last().pose().transformPosition(new Vector3f((float)binding.support().x(),(float)binding.support().y(),(float)binding.support().z()));
        PoseStack supportFrame=armFrame(model,supportArm);
        Vector3f shoulder=supportFrame.last().pose().transformPosition(new Vector3f());
        return new WeaponSegment(new GunArmAimMath.Vec(grip.x,grip.y,grip.z),
                new GunArmAimMath.Vec(fore.x,fore.y,fore.z),new GunArmAimMath.Vec(shoulder.x,shoulder.y,shoulder.z),
                Math.sqrt(101)/16.0);
    }

    /** ModelPart Euler rotation which points its local +Y arm axis at the target. */
    public static GunArmAimMath.Rotation supportArmRotation(GunArmAimMath.Vec shoulder, GunArmAimMath.Vec target) {
        var direction=target.add(shoulder.scale(-1)).normalized();
        return GunArmAimMath.align(new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,1),direction,
                new GunArmAimMath.Vec(0,0,1));
    }
    public static GunArmAimMath.Rotation supportArmRotation(GunArmAimMath.Vec shoulder, GunArmAimMath.Vec target,
                                                              HumanoidArm arm) {
        var direction=target.add(shoulder.scale(-1)).normalized();
        var handCenter=new GunArmAimMath.Vec(arm==HumanoidArm.LEFT?1.0/16:-1.0/16,10.0/16,0);
        return GunArmAimMath.align(handCenter,new GunArmAimMath.Vec(0,0,1),direction,new GunArmAimMath.Vec(0,0,1));
    }
    public record WeaponSegment(GunArmAimMath.Vec grip, GunArmAimMath.Vec foregrip,
                                GunArmAimMath.Vec shoulder, double handLength) { }
    public record SupportContact(float mainYaw, GunArmAimMath.Vec point, double fraction, double gap,
                                 boolean reached) { }

    /** Maximizes progress toward authored foregrip within the bounded yaw range; natural yaw breaks ties. */
    public static SupportContact solveSupportContact(float naturalYaw, float maxYaw, float step,
                                                      java.util.function.Function<Float,WeaponSegment> segmentAtYaw) {
        return solveSupportContact(naturalYaw,Math.min(0,naturalYaw),maxYaw,step,segmentAtYaw);
    }
    public static SupportContact solveSupportContact(float naturalYaw, float minYaw, float maxYaw, float step,
                                                      java.util.function.Function<Float,WeaponSegment> segmentAtYaw) {
        SupportContact best=null, feasible=null;
        int samples=(int)Math.ceil((maxYaw-minYaw)/step)+1;
        for(int i=0;i<samples;i++) {
            float yaw=Math.max(minYaw,Math.min(maxYaw,minYaw+i*step));
            WeaponSegment segment=segmentAtYaw.apply(yaw);
            SupportContact candidate=contactOnSegment(yaw,segment);
            if(candidate.reached()) {
                if(feasible==null || candidate.fraction()>feasible.fraction()+1e-7
                        || Math.abs(candidate.fraction()-feasible.fraction())<=1e-7
                        && Math.abs(yaw-naturalYaw)<Math.abs(feasible.mainYaw()-naturalYaw)) feasible=candidate;
            } else if(best==null || candidate.gap()<best.gap()) best=candidate;
        }
        return feasible!=null?feasible:best;
    }
    /** Farthest point toward the authored foregrip lying on the support-hand reach sphere. */
    public static SupportContact contactOnSegment(float yaw, WeaponSegment segment) {
        var d=segment.foregrip().add(segment.grip().scale(-1));
        var m=segment.grip().add(segment.shoulder().scale(-1));
        double a=d.dot(d), b=2*m.dot(d), c=m.dot(m)-segment.handLength()*segment.handLength();
        double disc=b*b-4*a*c;
        if(a>1e-12 && disc>=0) {
            double root=Math.sqrt(disc), t1=(-b-root)/(2*a), t2=(-b+root)/(2*a);
            double t=Math.max(t1,t2);
            if(t>=0 && t<=1) return new SupportContact(yaw,segment.grip().add(d.scale(t)),t,0,true);
            t=Math.min(t1,t2);
            if(t>=0 && t<=1) return new SupportContact(yaw,segment.grip().add(d.scale(t)),t,0,true);
        }
        double t=a>1e-12?Math.max(0,Math.min(1,-m.dot(d)/a)):0;
        var nearest=segment.grip().add(d.scale(t));
        double gap=Math.abs(nearest.add(segment.shoulder().scale(-1)).length()-segment.handLength());
        return new SupportContact(yaw,nearest,t,gap,false);
    }
    /** Gap from the actual one-bone arm endpoint to a model-space contact anchor. */
    public static double supportReachGap(HumanoidModel<?> model, HumanoidArm arm,
                                         GunArmAimMath.Rotation aimedRotation, GunArmAimMath.Vec target) {
        var part=arm==HumanoidArm.LEFT?model.leftArm:model.rightArm;
        float x=part.xRot,y=part.yRot,z=part.zRot;
        part.xRot=aimedRotation.x(); part.yRot=aimedRotation.y(); part.zRot=aimedRotation.z();
        PoseStack frame=armFrame(model,arm);
        part.xRot=x; part.yRot=y; part.zRot=z;
        Vector3f endpoint=frame.last().pose().transformPosition(new Vector3f(arm==HumanoidArm.LEFT?1/16f:-1/16f,10/16f,0));
        return new GunArmAimMath.Vec(target.x()-endpoint.x(),target.y()-endpoint.y(),target.z()-endpoint.z()).length();
    }
    private static PoseStack armFrame(HumanoidModel<?> model, HumanoidArm arm) {
        PoseStack frame=new PoseStack();
        if(model instanceof net.minecraft.client.model.PlayerModel<?> player) player.translateToHand(arm,frame);
        else (arm==HumanoidArm.LEFT?model.leftArm:model.rightArm).translateAndRotate(frame);
        return frame;
    }
    private static GunArmAimMath.Vec inverseArm(GunArmAimMath.Vec v, GunArmAimMath.Rotation a) {
        double cz=Math.cos(a.z()),sz=Math.sin(a.z());
        double x=cz*v.x()+sz*v.y(), y=-sz*v.x()+cz*v.y(), z=v.z();
        double cy=Math.cos(a.y()),sy=Math.sin(a.y());
        double x2=cy*x-sy*z, z2=sy*x+cy*z;
        double cx=Math.cos(a.x()),sx=Math.sin(a.x());
        return new GunArmAimMath.Vec(x2,cx*y+sx*z2,-sx*y+cx*z2);
    }

    /** Applies C=T(g) R s T(-g), with g and the authored basis measured through full T. */
    public static void applyCorrection(PoseStack pose, Matrix4f fullT, GunPresentationBindings.Binding binding,
                                       float scale, GunArmAimMath.Vec targetBore, GunArmAimMath.Vec targetUp) {
        applyCorrection(pose,fullT,binding,scale,targetBore,targetUp,null);
    }
    public static void applyCorrection(PoseStack pose, Matrix4f fullT, GunPresentationBindings.Binding binding,
                                       float scale, GunArmAimMath.Vec targetBore, GunArmAimMath.Vec targetUp,
                                       GunArmAimMath.Vec targetGrip) {
        Vector3f g=fullT.transformPosition(new Vector3f((float)binding.grip().x(),(float)binding.grip().y(),(float)binding.grip().z()));
        Vector3f b=fullT.transformDirection(new Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z()));
        Vector3f u=fullT.transformDirection(new Vector3f((float)binding.up().x(),(float)binding.up().y(),(float)binding.up().z()));
        var r=GunArmAimMath.align(new GunArmAimMath.Vec(b.x,b.y,b.z),new GunArmAimMath.Vec(u.x,u.y,u.z),targetBore,targetUp);
        Vector3f destination=targetGrip==null?new Vector3f(g):new Vector3f((float)targetGrip.x(),(float)targetGrip.y(),(float)targetGrip.z());
        pose.translate(destination.x,destination.y,destination.z);
        pose.mulPose(Axis.ZP.rotation(r.z())); pose.mulPose(Axis.YP.rotation(r.y())); pose.mulPose(Axis.XP.rotation(r.x()));
        pose.scale(scale,scale,scale);
        pose.translate(-g.x,-g.y,-g.z);
    }
}
