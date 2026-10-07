package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityProneDimensionsMixin {
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void moonstation14$proneDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (pose == Pose.SWIMMING && PostureSystem.isDownPosture(self)) {
            EntityDimensions normal = ((LivingEntity)(Object)this).getDimensions(Pose.STANDING);
            cir.setReturnValue(EntityDimensions.scalable(normal.width(), .6f).withEyeHeight(.4f));
        }
    }
}
