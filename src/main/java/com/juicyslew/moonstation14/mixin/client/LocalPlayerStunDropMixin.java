package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerStunDropMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void moonstation14$blockStunnedDrop(boolean all, CallbackInfoReturnable<Boolean> callback) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == player && CharacterControlSystem.isClientActionBlocked(player)) {
            callback.setReturnValue(false);
        }
    }
}
