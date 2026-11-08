package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel;
import com.juicyslew.moonstation14.ms14.standing.GunProneAimGeometry;

/** Deterministic articulated support pose for the admitted prone gun stance. */
final class ProneGunTwoBonePose {
    private ProneGunTwoBonePose() { }

    static void apply(LivingEntity entity, ArticulatedHarnessModel<?> model, HumanoidArm active,
                      HumanoidArm support, ItemStack stack) {
        var posture = GunProneAimPresentation.effectivePosture(entity);
        if (posture == null || !posture.down()) return;
        applyTorso(model, (float) GunProneAimGeometry.TORSO_RADIANS, (float)Math.toRadians(entity.getXRot()));
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        var binding=id==null?null:GunPresentationBindings.find(id);
        if(binding==null) return;
        var nativeFrame=GunMountTransform.nativeFrame(entity,stack,active);
        float scale=1;
        try { scale=com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack,true).visual().modelScale(); }
        catch(RuntimeException ignored) { }
        var worldLook=entity.getLookAngle();
        var localLook=com.juicyslew.moonstation14.ms14.standing.PronePoseMath.worldDirectionToModel(
                worldLook,posture.movementFacingYaw(),posture.faceUp());
        var localUp=com.juicyslew.moonstation14.ms14.standing.PronePoseMath.worldDirectionToModel(
                new net.minecraft.world.phys.Vec3(0,1,0),posture.movementFacingYaw(),posture.faceUp());
        solveContacts(model,active,support,binding,nativeFrame.last().pose(),scale,
                new GunMountTransform.AimTargets(new GunArmAimMath.Vec(localLook.x,localLook.y,localLook.z),new GunArmAimMath.Vec(localUp.x,localUp.y,localUp.z)));
    }

    record Contacts(ArticulatedArmPose.Pose primary, ArticulatedArmPose.Pose support,
                    double supportFraction, double supportGap) { }

    static Contacts solveContacts(ArticulatedHarnessModel<?> model, HumanoidArm active, HumanoidArm support,
                                  GunPresentationBindings.Binding binding, org.joml.Matrix4f nativeT,
                                  float scale, GunMountTransform.AimTargets targets) {
        var contact=GunMountTransform.handContactPoint(model,active);
        var gp=new org.joml.Vector3f((float)contact.x(),(float)contact.y(),(float)contact.z());
        var nativeGrip=new GunArmAimMath.Vec(gp.x,gp.y,gp.z);
        var left=model.armPart(HumanoidArm.LEFT); var right=model.armPart(HumanoidArm.RIGHT);
        var center=new GunArmAimMath.Vec((left.x+right.x)/32.0,(left.y+right.y)/32.0-.10,.25);
        var floor=new GunArmAimMath.Vec(0,0,1);
        var supportContact=GunMountTransform.handContactPoint(model,support);
        var supportGrip=new GunArmAimMath.Vec(supportContact.x(),supportContact.y(),supportContact.z());
        var activePart=model.armPart(active); var activeShoulder=new GunArmAimMath.Vec((activePart.x+model.slimHandOffset(active))/16.0,activePart.y/16.0,activePart.z/16.0);
        var lower=nativeGrip.sub(new GunArmAimMath.Vec(0,.25,0));
        var supportPart=model.armPart(support); var supportShoulder=new GunArmAimMath.Vec((supportPart.x+model.slimHandOffset(support))/16.0,supportPart.y/16.0,supportPart.z/16.0);
        var supportLower=supportGrip.sub(new GunArmAimMath.Vec(0,.25,0));
        ArticulatedArmPose.Pose primary=null,supportPose=null; double fraction=Double.NaN,gap=Double.POSITIVE_INFINITY;
        double faceFrontY=(model.head.y-4.0)/16.0;
        double minimumHandSeparation=(model.palmWidthPixels()+.5)/16.0;
        for(int yi=0;yi<=12;yi++) for(int zi=0;zi<=8;zi++) {
            var candidate=new GunArmAimMath.Vec(center.x(),faceFrontY+yi*.025,.34-zi*.01);
            var main=TwoBoneArmSolve.solve(activeShoulder,candidate,model.upperLength(active),lower.length(),new GunArmAimMath.Vec(0,1,0),floor,0);
            if(!main.floorContact() || !main.reached()) continue;
            primary=ArticulatedArmPose.solveAndApply(model,active,candidate,nativeGrip,floor,0);
            if(binding.support()==null) {
                supportPose=ArticulatedArmPose.solveAndApply(model,support,primary.hand(),supportGrip,floor,0);
                return new Contacts(primary,supportPose,Double.NaN,Double.POSITIVE_INFINITY);
            }
            var seg=GunMountTransform.supportWeaponSegment(binding,nativeT,model,active,support,scale,
                    GunMountTransform.articulatedArmSpaceTargets(model,active,targets.bore(),targets.up()),contact);
            var d=seg.foregrip().add(seg.grip().scale(-1));
            for(int i=0;i<=40;i++) {
                double t=1-i/40.0;
                var v=seg.grip().add(d.scale(t)); var p=new GunArmAimMath.Vec(v.x(),v.y(),v.z());
                var solved=TwoBoneArmSolve.solve(supportShoulder,p,model.upperLength(support),supportLower.length(),new GunArmAimMath.Vec(0,1,0),floor,0);
                if(solved.floorContact() && solved.reached() && t>=.5) {
                    var primaryHand=primary.hand();
                    if(primaryHand.sub(p).length()<minimumHandSeparation) continue;
                    supportPose=ArticulatedArmPose.solveAndApply(model,support,p,supportGrip,floor,0);
                    fraction=t; gap=0;
                    return new Contacts(primary,supportPose,fraction,gap);
                }
            }
        }
        if(primary==null) primary=ArticulatedArmPose.solveAndApply(model,active,center,nativeGrip,floor,0);
        if(supportPose==null) supportPose=ArticulatedArmPose.solveAndApply(model,support,primary.hand(),supportGrip,floor,0);
        return new Contacts(primary,supportPose,fraction,gap);
    }

    /** Hip-fixed torso lift; all pivots are expressed in native model pixels. */
    static void applyTorso(ArticulatedHarnessModel<?> model, float tilt, float aimPitch) {
        model.setFrameMode(true);
        float c=(float)Math.cos(tilt), s=(float)Math.sin(tilt);
        model.body.xRot=-tilt;
        model.body.y=12-12*c;
        model.body.z=12*s;
        model.head.y=model.body.y;
        model.head.z=model.body.z;
        model.head.yRot=0;
        model.head.zRot=0;
        model.head.xRot=(float)(-Math.PI/2)+aimPitch;
        for (var arm : new net.minecraft.world.entity.HumanoidArm[]{net.minecraft.world.entity.HumanoidArm.LEFT,
                net.minecraft.world.entity.HumanoidArm.RIGHT}) {
            var part=arm==net.minecraft.world.entity.HumanoidArm.LEFT?model.leftArm:model.rightArm;
            float nativeY=part.getInitialPose().y;
            float skinTopY=nativeY-2;
            part.y=12+(skinTopY-12)*c;
            part.z=-(skinTopY-12)*s;
        }
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
    }
}
