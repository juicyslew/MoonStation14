package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import com.juicyslew.moonstation14.ms14.standing.PostureMovementPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies resolved medical incapacity and crawler speed to vanilla movement without replacing physics. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPostureMovementMixin {
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private Vec3 moonstation14$adaptTravelWish(Vec3 wish) {
        LivingEntity body = (LivingEntity) (Object) this;
        if (MobStateSystem.isIncapacitated(body)) return Vec3.ZERO;
        if (!body.level().isClientSide) PostureSystem.onOwnCrawlInput(body, wish.horizontalDistanceSqr() > 1.0e-8);
        return wish;
    }

    @ModifyArg(method = "handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"),
            index = 0, require = 1)
    private float moonstation14$scaleGroundedPostureInput(float acceleration) {
        LivingEntity body = (LivingEntity) (Object) this;
        if (!body.onGround()) return acceleration;
        double friction = PostureSystem.downFriction(body);
        return (float) (acceleration * PostureMovementPhysics.inputGain(friction));
    }

    @Redirect(method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"),
            require = 1)
    private Vec3 moonstation14$adaptGroundedPostureDamping(Vec3 vector, double x, double y, double z) {
        LivingEntity body = (LivingEntity) (Object) this;
        if (!body.onGround() || !PostureSystem.isDown(body) || body.isInWater() || body.isInLava()
                || body.isFallFlying() || Math.abs(x - z) > 1e-9 || Math.abs(y - .98d) > 1e-9) {
            return vector.multiply(x, y, z);
        }
        double friction = PostureSystem.downFriction(body);
        return vector.multiply(PostureMovementPhysics.groundedDamping(x, friction), y,
                PostureMovementPhysics.groundedDamping(z, friction));
    }

    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true, require = 1)
    private void moonstation14$scaleCrawlSpeed(CallbackInfoReturnable<Float> callback) {
        LivingEntity body = (LivingEntity) (Object) this;
        double factor = PostureSystem.crawlMultiplier(body)
                * BodyPullService.movementMultiplier(body, body.isSprinting());
        if (factor != 1) callback.setReturnValue((float) (callback.getReturnValue() * factor));
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$blockJumpWhileDownOrIncapacitated(CallbackInfo callback) {
        LivingEntity body = (LivingEntity) (Object) this;
        if (MobStateSystem.isIncapacitated(body) || PostureSystem.isDown(body)) callback.cancel();
    }
}

