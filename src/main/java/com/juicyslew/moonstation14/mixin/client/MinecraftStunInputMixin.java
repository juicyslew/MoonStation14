package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftStunInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void moonstation14$blockStunnedAttack(CallbackInfoReturnable<Boolean> callback) {
        if (moonstation14$isActionBlocked()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void moonstation14$blockStunnedMining(boolean leftClick, CallbackInfo callback) {
        if (moonstation14$isActionBlocked()) {
            callback.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void moonstation14$blockStunnedUse(CallbackInfo callback) {
        if (moonstation14$isActionBlocked()) {
            callback.cancel();
        }
    }

    private boolean moonstation14$isActionBlocked() {
        Minecraft minecraft = (Minecraft) (Object) this;
        return minecraft.player != null
                && CharacterControlSystem.isClientActionBlocked(minecraft.player);
    }
}
