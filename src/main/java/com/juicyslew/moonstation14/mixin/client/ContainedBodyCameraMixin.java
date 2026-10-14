package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only removes detached-camera clipping for the exact bound body when its authoritative view requests it. */
@Mixin(Camera.class)
public abstract class ContainedBodyCameraMixin {
    @Shadow private Entity entity;

    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$unclipContainedBody(float requested, CallbackInfoReturnable<Float> cir) {
        if (requested == 4.0F && ContainedCharacterClientView.shouldUnclip(entity)) cir.setReturnValue(4.0F);
    }
}

