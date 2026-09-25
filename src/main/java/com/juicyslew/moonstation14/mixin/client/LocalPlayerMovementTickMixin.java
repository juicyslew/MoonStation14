package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.movement.client.MovementClientController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the client motor once after vanilla has refreshed input and before position reporting. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMovementTickMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendPosition()V"), require = 1)
    private void moonstation14$predictBeforePositionSend(CallbackInfo callback) {
        MovementClientController.tick((LocalPlayer) (Object) this);
    }
}
