package com.juicyslew.moonstation14.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the protected vanilla movement predicate to integration tests. */
@Mixin(LivingEntity.class)
public interface LivingEntityImmobileInvoker {
    @Invoker("isImmobile")
    boolean moonstation14$callIsImmobile();
}
