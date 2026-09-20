package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusEffectMixin implements IStatusEffectTrait {

    @Override
    public TraitHandler<IStatusEffectTrait> toHandleSelf() {
        return new TraitHandler<>(this, this);
    }
}