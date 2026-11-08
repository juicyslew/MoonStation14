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
        if (attachment == null || !attachment.data().down()) return;
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        boolean idle = PronePoseMath.shouldRestArms(attachment.data());
        neutral(model.head);
        model.head.z = PronePoseMath.headGroundingOffsetPixels(attachment.data().faceUp());
        model.hat.copyFrom(model.head);
        if (!idle) return;
        neutral(model.hat);
        neutral(model.leftArm);
        neutral(model.rightArm);
        neutral(model.leftLeg);
        neutral(model.rightLeg);
    }

    private static void neutral(net.minecraft.client.model.geom.ModelPart part) {
        part.xRot = part.yRot = part.zRot = 0;
    }
}

