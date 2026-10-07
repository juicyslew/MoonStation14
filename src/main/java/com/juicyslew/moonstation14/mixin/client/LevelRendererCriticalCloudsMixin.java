package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.eye.client.CriticalFogClient;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.LevelRenderer;

/** Prevents the un-fogged cloud pass from drawing over the opaque Critical sky. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererCriticalCloudsMixin {
    @Inject(method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"), cancellable = true)
    private void moonstation14$hideCloudsInCritical(PoseStack poseStack, Matrix4f frustumMatrix,
                                                    Matrix4f projectionMatrix, float partialTick,
                                                    double camX, double camY, double camZ, CallbackInfo ci) {
        if (CriticalFogClient.isCriticalObservedBody()) ci.cancel();
    }
}
