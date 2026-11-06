package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFramePresentationContext;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restricts endpoint getter overrides to the exact owner entity being drawn by Sable's frame renderer. */
@Mixin(LevelRenderer.class)
public abstract class SableOwnedFrameEntityRenderContextMixin {
    @Inject(method="renderEntity",at=@At("HEAD"),require=1)
    private void moonstation14$beginOwnedFrameEntity(CallbackInfo ci,@Local(argsOnly=true) Entity entity) {
        OwnedFramePresentationContext.begin(entity);
    }
    @Inject(method="renderEntity",at=@At("RETURN"),require=1)
    private void moonstation14$endOwnedFrameEntity(CallbackInfo ci,@Local(argsOnly=true) Entity entity) {
        OwnedFramePresentationContext.end(entity);
    }
}
