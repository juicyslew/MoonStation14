package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops Sable's remote plot-packet cache from projecting the exact locally predicted Human each tick. */
@Mixin(value = Entity.class, priority = 900)
public abstract class SableOwnedRemoteFrameEntityMixin {
    /** Sable's renderer must not consume a stale packet cache while this exact body is prediction-owned. */
    @Inject(method = "sable$getPlotPosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"),
            cancellable = true, remap = false, require = 1)
    private void moonstation14$hideOwnedRemotePlotPosition(CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide && GhostControlClient.ownsSableRemoteFrameInterpolation(entity))
            callback.setReturnValue(null);
    }
}
