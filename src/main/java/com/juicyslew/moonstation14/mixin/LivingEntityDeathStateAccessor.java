package com.juicyslew.moonstation14.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow native death-animation state reset used by the explicit medical revive API. */
@Mixin(LivingEntity.class)
public interface LivingEntityDeathStateAccessor {
    @Accessor("dead") boolean ms14$isDead();
    @Accessor("dead") void ms14$setDead(boolean dead);
    @Accessor("deathTime") void ms14$setDeathTime(int deathTime);

}

