package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleDevelopmentMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Post-switch handoff only; never changes vanilla's mode-change result. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCreativeHandoffMixin {
    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void ms14$creativeHandoff(GameType requested, CallbackInfoReturnable<Boolean> returnValue) {
        if (requested == GameType.CREATIVE)
            LifecycleDevelopmentMode.creativeModeChangeReturned((ServerPlayer) (Object) this,
                    requested, returnValue.getReturnValue());
    }
}
