package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.entities.CanisterRemovalLifecycle;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityGasCanisterRemovalMixin {
    @Inject(method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$beforeCanisterRemoval(Entity.RemovalReason reason, CallbackInfo callbackInfo) {
        Object entity = this;
        if (entity instanceof CanisterRemovalLifecycle canister) {
            if (!canister.beforeCanisterRemoval(reason)) callbackInfo.cancel();
        }
    }
}

