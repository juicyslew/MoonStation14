package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla travel from applying a second movement step to custom-owned players. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementOwnerMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$skipVanillaTravel(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof ServerPlayer player && MovementStartupGate.enabledForServer()
                && MovementServerController.owns(player)) callback.cancel();
    }
}
