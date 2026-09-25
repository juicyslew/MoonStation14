package com.juicyslew.moonstation14.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's animation calculation for the client-owned movement step. */
@Mixin(LivingEntity.class)
public interface LivingEntityMovementAnimationInvoker {
    @Invoker("calculateEntityAnimation")
    void moonstation14$calculateEntityAnimation(boolean flying);
}
