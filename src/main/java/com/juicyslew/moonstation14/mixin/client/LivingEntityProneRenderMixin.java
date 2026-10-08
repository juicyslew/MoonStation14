package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import com.juicyslew.moonstation14.ms14.standing.PronePoseMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.MultiBufferSource;

/** Presents downed bodies horizontally without changing their physical pose or movement state. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityProneRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void moonstation14$hideMedicalCameraSelfBody(LivingEntity entity, float entityYaw,
                                                         float partialTick, PoseStack poseStack,
                                                         MultiBufferSource buffer, int packedLight,
                                                         CallbackInfo callback) {
        if (ObserverClient.hideLocalCarrierModel(entity)) {
            callback.cancel();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (ObserverClient.usesMedicalCamera() && ObserverClient.isObservedMedicalBody(entity)
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            callback.cancel();
        }
    }

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void moonstation14$layDownedBody(LivingEntity entity, PoseStack poseStack, float bob,
                                             float bodyYaw, float partialTick, float scale,
                                             CallbackInfo callback) {
        boolean physicalProne = PostureSystem.isDownPosture(entity);
        boolean synchronizedKnockdown = com.juicyslew.moonstation14.ms14.character.KnockdownRenderPolicy
                .shouldRenderProne(entity);
        if (!physicalProne && !synchronizedKnockdown) return;

        // Airlock shocks/crushes still use synchronized knockdown state. Fold that
        // projection into the posture renderer now that the legacy facing mixin is retired.
        if (!physicalProne) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            callback.cancel();
            return;
        }

        // Prone heading is synchronized from accepted owner movement, never camera look.
        var attachment = entity.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
        float facing = attachment == null ? bodyYaw : attachment.data().movementFacingYaw();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing));
        float modelScale = (float) com.juicyslew.moonstation14.ms14.standing.PronePoseGeometry.modelScale(entity);
        PronePoseMath.applySharedRootPose(poseStack, entity.getBbWidth(), modelScale,
                attachment != null && attachment.data().faceUp());
        callback.cancel();
    }
}
