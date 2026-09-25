package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs custom authority only after vanilla has restored its last accepted position. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerMovementTickMixin {
    @Shadow @Final public ServerPlayer player;

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V", shift = At.Shift.AFTER), require = 1)
    private void moonstation14$afterVanillaRestore(CallbackInfo callback) {
        MovementServerController.onAfterVanillaRestore(player);
    }
}
