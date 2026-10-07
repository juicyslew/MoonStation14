package com.juicyslew.moonstation14.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Removes vanilla's automatic damage-reaction shove without affecting explicit impulses. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageKnockbackMixin {
    @Redirect(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"),
            require = 1)
    private void moonstation14$suppressAutomaticDamageKnockback(LivingEntity entity,
                                                                  double strength, double x, double z) {
        // Deliberately omit only the knockback call made inside hurt. Explicit knockback
        // calls (melee, projectiles, explosions, etc.) elsewhere retain vanilla behavior.
    }
}
