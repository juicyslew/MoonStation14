package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStunImmobileMixin {
    @Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
    private void moonstation14$stunBlocksMovement(CallbackInfoReturnable<Boolean> callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        boolean actionBlocked = entity.level().isClientSide
                ? CharacterControlSystem.isClientActionBlocked(entity)
                : CharacterControlSystem.isStunned(entity);
        if (!callback.getReturnValue() && actionBlocked) {
            callback.setReturnValue(true);
        }
    }
}
