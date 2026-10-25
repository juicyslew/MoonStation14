package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses native travel for harness-owned mobs and eligible incapacitated medical bodies. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMindHarnessTravelMixin {
    /** No-AI LivingEntity.aiStep applies an extra .98 velocity scale outside the shared motor. */
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"), require = 1)
    private Vec3 moonstation14$preserveMotorOwnedMomentum(Vec3 movement, double scale) {
        Object entity = this;
        if (entity instanceof Mob mob && (mob instanceof MindControlledMob owner
                && owner.moonstation14$isMovementOwned()
                || mob instanceof NpcMovementDriven driven && driven.moonstation14$isNpcMovementDriven()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(mob))) {
            return movement;
        }
        return movement.scale(scale);
    }

    @Inject(method = "travel", at = @At("HEAD"), require = 1)
    private void moonstation14$applyNativeTetherImpulse(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof LivingEntity living && !living.level().isClientSide
                && !living.isPassenger() && !living.isDeadOrDying() && !living.isSpectator() && !living.noPhysics
                && (!(entity instanceof Mob mob) || (!(mob instanceof MindControlledMob owner
                    && owner.moonstation14$isMovementOwned())
                    && !(mob instanceof NpcMovementDriven driven && driven.moonstation14$isNpcMovementDriven())))) {
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
                && (!(entity instanceof Mob mob)
                || mob instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                || mob instanceof NpcMovementDriven driven && driven.moonstation14$isNpcMovementDriven()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(mob)
                || com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep.isNaturalFlightBodyEligible(mob))) {
            callback.cancel();
            return;
        }
        if (entity instanceof Mob mob && (com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics
                .eligible(mob) || mob instanceof MindControlledMob controlled && controlled.moonstation14$isMovementOwned()
                || (!(mob instanceof MindControlledMob controlled && controlled.moonstation14$isMovementOwned())
                && mob instanceof NpcMovementDriven driven && driven.moonstation14$isNpcMovementDriven()))) {
            LivingEntity body = (LivingEntity) entity;
            // Owned living bodies suppress vanilla travel. Retained medical corpses still need
            // authoritative collision-resolved motion, without restoring AI or owner input.
            // Tracked remote clients cannot infer authoritative accepted displacement here; the owning
            // server and local predictor update walk animation at their explicit movement step instead.
            callback.cancel();
        }
    }
}
