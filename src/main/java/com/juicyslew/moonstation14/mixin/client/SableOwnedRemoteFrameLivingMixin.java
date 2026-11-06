package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents setup handoff and lets Sable clear cached lerps while an exact CHARACTER body is locally owned. */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class SableOwnedRemoteFrameLivingMixin {
    @Shadow protected int lerpSteps;

    @Inject(method = "sable$setupLerp()V", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private void moonstation14$disableOwnedRemoteLerpSetup(CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!entity.level().isClientSide || !GhostControlClient.ownsSableRemoteFrameInterpolation(entity)) return;
        // Stop vanilla's pending position lerp as well as Sable's custom remote rotation handoff.
        lerpSteps = 0;
        callback.cancel();
    }
}
