package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Only removes detached-camera clipping for the exact bound body when its authoritative view requests it. */
@Mixin(Camera.class)
public abstract class ContainedBodyCameraMixin {
    @Shadow private Entity entity;
    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$unclipContainedBody(float requested, CallbackInfoReturnable<Float> cir) {
        if (requested == 4.0F && ContainedCharacterClientView.shouldUnclip(entity)) cir.setReturnValue(4.0F);
    }

    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", ordinal = 0), require = 1)
    private void moonstation14$translateContainedBasePosition(Args args, net.minecraft.world.level.BlockGetter level,
                                                               net.minecraft.world.entity.Entity cameraEntity,
                                                               boolean detached, boolean thirdPerson, float partialTick) {
        ContainedCharacterClientView.cameraTranslation(cameraEntity, partialTick).ifPresent(delta -> {
            args.set(0, (double) args.get(0) + delta.x);
            args.set(1, (double) args.get(1) + delta.y);
            args.set(2, (double) args.get(2) + delta.z);
        });
    }
}
