package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses native travel for mobs whose movement is resolved by the shared world step. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMindHarnessTravelMixin {
    // Cancelling travel does not prevent aiStep's own velocity normalization writes.
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            ordinal = 0), require = 1)
    private void moonstation14$preserveOwnedAiStepVelocity(LivingEntity entity, Vec3 movement) {
        if (!(entity instanceof Mob mob) || !moonstation14$hasCustomPhysicsOwner(mob))
            entity.setDeltaMovement(movement);
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",
            ordinal = 0), require = 1)
    private void moonstation14$preserveOwnedAiStepVelocity(LivingEntity entity, double x, double y, double z) {
        if (!(entity instanceof Mob mob) || !moonstation14$hasCustomPhysicsOwner(mob))
            entity.setDeltaMovement(x, y, z);
    }

    @Inject(method = "travel", at = @At("HEAD"), require = 1)
    private void moonstation14$applyNativeTetherImpulse(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof LivingEntity living && !living.level().isClientSide
                && !living.isPassenger() && !living.isDeadOrDying() && !living.isSpectator() && !living.noPhysics
                && (!(entity instanceof Mob mob) || !moonstation14$hasCustomPhysicsOwner(mob))) {
            Vec3 perSecond = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                    .deltaVelocityPerSecond(living);
            perSecond = perSecond.add(com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                    .deltaVelocity(living.level(), living.position(), living.level().getGameTime()));
            Vec3 delta = perSecond.scale(.05);
            if (delta.lengthSqr() > 0) living.setDeltaMovement(living.getDeltaMovement().add(delta));
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$skipVanillaTravelForHarnessOwnedMob(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof LivingEntity living && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.isFlying(living)
                && (!(entity instanceof Mob mob) || moonstation14$hasCustomPhysicsOwner(mob)
                || com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep
                        .isNaturalFlightBodyEligible(mob))) {
            callback.cancel();
            return;
        }
        if (entity instanceof Mob mob && moonstation14$hasCustomPhysicsOwner(mob)) callback.cancel();
    }

    private static boolean moonstation14$hasCustomPhysicsOwner(Mob mob) {
        return com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(mob)
                || mob instanceof MindControlledMob controlled && controlled.moonstation14$isMovementOwned()
                || mob instanceof NpcMovementDriven driven && driven.moonstation14$isNpcMovementDriven()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep
                        .isCustomPhysicalOwner(mob);
    }
}
