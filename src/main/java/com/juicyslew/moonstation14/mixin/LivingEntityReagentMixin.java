package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class LivingEntityReagentMixin implements IReagentTrait {
    @Override
    public float getCapacity() {
        return 1000f;
    }
}