package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.server.CommittedSpectatorGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spectator attack and other camera switches must not replace a committed owned body. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCommittedSpectatorCameraMixin {
    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void moonstation14$keepCommittedCamera(Entity target, CallbackInfo callback) {
        if (CommittedSpectatorGuard.blockCamera((ServerPlayer) (Object) this, target)) callback.cancel();
    }
}
