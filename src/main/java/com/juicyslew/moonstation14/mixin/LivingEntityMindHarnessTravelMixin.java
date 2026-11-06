package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses native travel for harness-owned mobs and eligible incapacitated medical bodies.
 * Keep owned aiStep normalization writes guarded too; see movement invariants in
 * docs/shuttle-relocation-sprint/CHARACTER-INPUT-CADENCE.md.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMindHarnessTravelMixin {
    // travel cancellation does not stop aiStep's two native velocity normalization writes.
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
                && (!(entity instanceof Mob mob) || !moonstation14$hasCustomPhysicsOwner(mob))) {
            Vec3 delta = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                    .deltaVelocityPerSecond(living).scale(.05);
            if (delta.lengthSqr() > 0) living.setDeltaMovement(living.getDeltaMovement().add(delta));
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$skipVanillaTravelForHarnessOwnedMob(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof Mob mob && moonstation14$hasCustomPhysicsOwner(mob)) {
            LivingEntity body = (LivingEntity) entity;
            // Owned living bodies suppress vanilla travel. Retained medical corpses still need
            // authoritative collision-resolved motion, without restoring AI or owner input.
            // Tracked remote clients cannot infer authoritative accepted displacement here; the owning
            // server and local predictor update walk animation at their explicit movement step instead.
            callback.cancel();
        }
    }

    private static boolean moonstation14$hasCustomPhysicsOwner(Mob mob) {
        return com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(mob)
                || mob instanceof MindControlledMob controlled && controlled.moonstation14$isMovementOwned()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep
                        .isCustomPhysicalOwner(mob);
    }
}
