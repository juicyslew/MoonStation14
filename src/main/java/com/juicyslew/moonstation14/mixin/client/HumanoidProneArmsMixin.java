package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.standing.PronePoseMath;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps idle lying humanoid parts neutral without suppressing active crawl animation. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidProneArmsMixin {
    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void moonstation14$resetProneHeadOffset(LivingEntity entity, float limbSwing,
                                                    float limbSwingAmount, float ageInTicks,
                                                    float netHeadYaw, float headPitch,
                                                    CallbackInfo callback) {
        ((HumanoidModel<?>)(Object)this).head.z = 0;
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void moonstation14$restLyingArms(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo callback) {
        var attachment = entity.getExistingDataOrNull(ModDataAttachments.POSTURE.get());
        if (attachment == null || !attachment.data().down()) {
            com.juicyslew.moonstation14.ms14.combat.client.GunArmPosePresentation.apply(entity,
                    (HumanoidModel<?>)(Object)this, netHeadYaw, headPitch);
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        boolean idle = PronePoseMath.shouldRestArms(attachment.data());
        neutral(model.head);
        model.head.z = PronePoseMath.headGroundingOffsetPixels(attachment.data().faceUp());
        model.hat.copyFrom(model.head);
        if (idle) {
            neutral(model.hat);
            boolean melee=model.attackTime>0 || entity.swinging;
            if (!melee) {
                neutral(model.leftArm);
                neutral(model.rightArm);
            }
            neutral(model.leftLeg);
            neutral(model.rightLeg);
        }
        // Share the ordinary combat pose owner after the prone head-grounding pose.
        // Its admission still leaves melee attacks ahead of the gun pose.
        com.juicyslew.moonstation14.ms14.combat.client.GunArmPosePresentation.apply(entity,model,netHeadYaw,headPitch);
    }

    private static void neutral(net.minecraft.client.model.geom.ModelPart part) {
        part.xRot = part.yRot = part.zRot = 0;
    }
}
