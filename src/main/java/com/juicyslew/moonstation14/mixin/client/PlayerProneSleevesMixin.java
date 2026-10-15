package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.standing.PronePoseMath;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies final resting poses onto player outerwear after PlayerModel animation. */
@Mixin(PlayerModel.class)
public abstract class PlayerProneSleevesMixin {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void moonstation14$restLyingSleeves(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                 float ageInTicks, float netHeadYaw, float headPitch,
                                                 CallbackInfo callback) {
        var attachment = entity.getExistingDataOrNull(ModDataAttachments.POSTURE.get());
        if (attachment == null || !attachment.data().down()) return;
        PlayerModel<?> model = (PlayerModel<?>)(Object)this;
        model.head.z = PronePoseMath.headGroundingOffsetPixels(attachment.data().faceUp());
        model.hat.copyFrom(model.head);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        if (!PronePoseMath.shouldRestArms(attachment.data())) return;
        // PlayerModel owns these outerwear parts; copy only after its animation
        // pass so swimming/attack poses cannot leave stale rotations on clothing.
        model.jacket.copyFrom(model.body);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        neutral(model.jacket);
        neutral(model.leftPants);
        neutral(model.rightPants);
    }

    private static void neutral(net.minecraft.client.model.geom.ModelPart part) {
        part.xRot = part.yRot = part.zRot = 0;
    }
}

