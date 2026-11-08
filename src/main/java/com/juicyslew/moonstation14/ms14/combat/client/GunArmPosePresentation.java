package com.juicyslew.moonstation14.ms14.combat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.combat.ModGunProfiles;
import com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemRenderer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.juicyslew.moonstation14.ms14.standing.PostureData;

/** Public, presentation-only combat gun arm pose. */
public final class GunArmPosePresentation {
    private GunArmPosePresentation() { }
    public static boolean admitted(boolean combat, boolean activeGun, boolean wieldRequired,
                                   boolean validatedWield, boolean melee) {
        return combat && activeGun && (!wieldRequired || validatedWield) && !melee;
    }
    static boolean sharedTorsoAdmitted(PostureData posture) {
        return posture != null && posture.down() && posture.supportedGunAim();
    }
    public static void apply(LivingEntity entity, HumanoidModel<?> model, float headYaw, float headPitch) {
        if (entity == null || model == null) return;
        boolean combat=com.juicyslew.moonstation14.ms14.combat.CombatModeClient.modeForPresentation(entity);
        var snapshot=BodyHandNetworking.clientCache().get(entity.level().dimension().location(),entity.getUUID(),entity.getId()).orElse(null);
        BodyAppearanceSnapshot.PoseContext context=snapshot==null?null:snapshot.poseContext();
        if(context==null) return;
        var hand=snapshot.hands().stream().filter(e->e.id().equals(context.activeHand())).findFirst().orElse(null);
        ItemStack stack=hand==null||hand.item()==null?ItemStack.EMPTY:hand.item().stack();
        if(stack.isEmpty()) return;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        var profile=id==null?null:ModGunProfiles.clientCatalog().get(id);
        var effectivePosture=GunProneAimPresentation.effectivePosture(entity);
        if (model instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<?> articulated
                && sharedTorsoAdmitted(effectivePosture))
            ProneGunTwoBonePose.applyTorso(articulated,
                    (float) com.juicyslew.moonstation14.ms14.standing.GunProneAimGeometry.TORSO_RADIANS,
                    (float) Math.toRadians(entity.getXRot()));
        boolean melee=model.attackTime>0 || entity.swinging;
        if(!admitted(combat,profile!=null,profile!=null&&profile.requiresWield(),context.hasValidatedWield(),melee)) return;

        boolean left=context.activeHand().equalsIgnoreCase("left");
        var mc=Minecraft.getInstance();
        var arm=left?model.leftArm:model.rightArm;
        boolean prone=effectivePosture!=null && effectivePosture.supportedGunAim();
        // Keep Minecraft's natural one-handed hold; the held-item mount correction
        // aligns its authored bore to this relaxed arm frame instead of swinging
        // an arm backward to compensate for model-specific rest rolls.
        if(context.hasValidatedWield()) {
            if(prone) {
                var activeArm=left?HumanoidArm.LEFT:HumanoidArm.RIGHT;
                var supportArm=left?HumanoidArm.RIGHT:HumanoidArm.LEFT;
                if(model instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel<?> articulated)
                    ProneGunTwoBonePose.apply(entity, articulated, activeArm, supportArm, stack);
            } else net.minecraft.client.model.AnimationUtils.animateCrossbowHold(model.rightArm,model.leftArm,model.head,!left);
        } else {
            {
                arm.xRot=(float)(-Math.PI/2)+model.head.xRot;
                arm.yRot=model.head.yRot+(left?-0.15F:0.15F);
                arm.zRot=0;
            }
        }
        if(context.hasValidatedWield()) {
            String support=context.wieldSupportHand();
            if(!support.equalsIgnoreCase(context.activeHand())) {
                var supportPart=support.equalsIgnoreCase("left")?model.leftArm:model.rightArm;
                if(supportPart!=arm && profile.requiresWield()) {
                    var activeArm=left?HumanoidArm.LEFT:HumanoidArm.RIGHT;
                    var supportArm=support.equalsIgnoreCase("left")?HumanoidArm.LEFT:HumanoidArm.RIGHT;
                    var contact=prone?null:GunMountTransform.solveSupportPose(entity,model,activeArm,supportArm,stack);
                    if(contact!=null) {
                        var shoulderFrame=new PoseStack();
                        if(model instanceof net.minecraft.client.model.PlayerModel<?> player)
                            player.translateToHand(supportArm,shoulderFrame);
                        else (supportArm==HumanoidArm.LEFT?model.leftArm:model.rightArm).translateAndRotate(shoulderFrame);
                        var p=shoulderFrame.last().pose().transformPosition(new org.joml.Vector3f());
                        var shoulder=new GunArmAimMath.Vec(p.x,p.y,p.z);
                        var rotation=GunMountTransform.supportArmRotation(shoulder,contact.point(),supportArm);
                        supportPart.xRot=rotation.x(); supportPart.yRot=rotation.y(); supportPart.zRot=rotation.z();
                    }
                }
            }
        }
    }

    static GunArmAimMath.Vec headForward(HumanoidModel<?> model) {
        PoseStack headFrame=new PoseStack();
        model.head.translateAndRotate(headFrame);
        var forward=headFrame.last().pose().transformDirection(new org.joml.Vector3f(0,0,-1));
        return new GunArmAimMath.Vec(forward.x,forward.y,forward.z);
    }
}
