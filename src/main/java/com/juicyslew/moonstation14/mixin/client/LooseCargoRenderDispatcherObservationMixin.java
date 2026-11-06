package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.loose_objects.client.LooseCargoCaptureClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Non-mutating observations of the actual client dispatcher cull and render inputs. */
@Mixin(EntityRenderDispatcher.class)
public abstract class LooseCargoRenderDispatcherObservationMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("RETURN"), require = 1)
    private void moonstation14$observeActualCull(Entity entity, Frustum frustum, double cameraX, double cameraY,
            double cameraZ, CallbackInfoReturnable<Boolean> callback) {
        LooseCargoCaptureClient.observeDispatcherCull(entity, callback.getReturnValue(), cameraX, cameraY, cameraZ);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), require = 1)
    private void moonstation14$observeActualDispatch(Entity entity, double x, double y, double z,
            float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight,
            CallbackInfo callback) {
        LooseCargoCaptureClient.observeDispatcherRender(entity, x, y, z, entityYaw, partialTick, pose, packedLight);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"), require = 1)
    private void moonstation14$closeObservedDispatch(Entity entity, double x, double y, double z,
            float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight,
            CallbackInfo callback) {
        LooseCargoCaptureClient.observeDispatcherReturn(entity);
    }
}
