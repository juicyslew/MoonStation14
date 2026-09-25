package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.slip.MinecraftSlidingPhysics;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySlidingFrictionMixin {
    @Unique
    private transient boolean moonstation14$slidingTravelCaptured;
    @Unique
    private transient boolean moonstation14$slidingTravelGrounded;
    @Unique
    private transient double moonstation14$slidingTravelFactor = 1d;

    @WrapMethod(method = "travel(Lnet/minecraft/world/phys/Vec3;)V")
    private void moonstation14$withSlidingTravelFrame(Vec3 input, Operation<Void> original) {
        boolean previousCaptured = moonstation14$slidingTravelCaptured;
        boolean previousGrounded = moonstation14$slidingTravelGrounded;
        double previousFactor = moonstation14$slidingTravelFactor;
        moonstation14$slidingTravelCaptured = false;
        moonstation14$slidingTravelGrounded = false;
        moonstation14$slidingTravelFactor = 1d;
        try {
            original.call(input);
        } finally {
            moonstation14$slidingTravelCaptured = previousCaptured;
            moonstation14$slidingTravelGrounded = previousGrounded;
            moonstation14$slidingTravelFactor = previousFactor;
        }
    }

    @Inject(
            method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"),
            require = 1
    )
    private void moonstation14$captureSlidingTravelFrame(Vec3 input, CallbackInfo callbackInfo) {
        LivingEntity entity = (LivingEntity) (Object) this;
        moonstation14$slidingTravelGrounded = entity.onGround();
        moonstation14$slidingTravelFactor = moonstation14$slidingTravelGrounded ? safeFrictionFactor(entity) : 1d;
        moonstation14$slidingTravelCaptured = true;
    }

    @WrapOperation(
            method = "handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"),
            require = 1
    )
    private void moonstation14$scaleGroundedSlidingAcceleration(LivingEntity entity, float acceleration,
                                                                  Vec3 movement, Operation<Void> original) {
        double factor = moonstation14$slidingTravelCaptured && moonstation14$slidingTravelGrounded
                ? moonstation14$slidingTravelFactor : 1d;
        original.call(entity, (float) MinecraftSlidingPhysics.acceleration(acceleration, factor), movement);
    }

    @WrapOperation(
            method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"),
            require = 1
    )
    private void moonstation14$scaleGroundedSlidingRetention(LivingEntity entity, double x, double y, double z,
                                                                Operation<Void> original,
                                                                @Local(name = "f3") float vanillaRetention) {
        double factor = moonstation14$slidingTravelCaptured && moonstation14$slidingTravelGrounded
                ? moonstation14$slidingTravelFactor : 1d;
        if (factor == 1d || vanillaRetention == 0f) {
            original.call(entity, x, y, z);
            return;
        }
        double adjusted = MinecraftSlidingPhysics.groundHorizontalRetention(vanillaRetention, factor);
        double ratio = adjusted / vanillaRetention;
        original.call(entity, x * ratio, y, z * ratio);
    }

    @Unique
    private static double safeFrictionFactor(LivingEntity entity) {
        try {
            return SlidingFrictionSystem.frictionFactor(entity);
        } catch (RuntimeException exception) {
            return 1d;
        }
    }
}
