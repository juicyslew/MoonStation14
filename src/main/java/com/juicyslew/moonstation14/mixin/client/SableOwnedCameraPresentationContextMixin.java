package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFramePresentationContext;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps only the exact predicted owner camera's SDK endpoint inversions on the published frame interval. */
@Mixin(Camera.class)
public abstract class SableOwnedCameraPresentationContextMixin {
    @Inject(method="setup",at=@At("HEAD"),require=1)
    private void moonstation14$beginOwnedCamera(CallbackInfo ci,@Local(argsOnly=true) Entity cameraEntity) {
        OwnedFramePresentationContext.begin(cameraEntity);
    }
    @Inject(method="setup",at=@At("RETURN"),require=1)
    private void moonstation14$endOwnedCamera(CallbackInfo ci,@Local(argsOnly=true) Entity cameraEntity) {
        OwnedFramePresentationContext.end(cameraEntity);
    }
}
