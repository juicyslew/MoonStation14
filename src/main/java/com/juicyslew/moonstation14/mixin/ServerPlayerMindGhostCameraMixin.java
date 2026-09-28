package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps vanilla Shift dismount behavior except when it would cancel a committed mind ghost camera. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMindGhostCameraMixin {
    @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;wantsToStopRiding()Z"), require = 1)
    private boolean moonstation14$keepCommittedMindGhostCamera(boolean original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        return original && !(GhostMobHarnessControl.shouldKeepGhostCamera(player)
                || LifecycleCharacterSessionControl.shouldKeepCharacterCamera(player)
                || LifecycleGhostSessionControl.shouldKeepGhostCamera(player));
    }
}
