package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityMeleeLungePoseMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void moonstation14$pushLunge(LivingEntity entity, float entityYaw, float partialTick,
                                        PoseStack pose, MultiBufferSource buffers, int packedLight,
                                        CallbackInfo ci) {
        pose.pushPose();
        var offset = MeleeArcClient.lungeOffset(entity, partialTick);
        pose.translate(offset.x, offset.y, offset.z);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void moonstation14$popLunge(LivingEntity entity, float entityYaw, float partialTick,
                                        PoseStack pose, MultiBufferSource buffers, int packedLight,
                                        CallbackInfo ci) {
        pose.popPose();
    }
}
