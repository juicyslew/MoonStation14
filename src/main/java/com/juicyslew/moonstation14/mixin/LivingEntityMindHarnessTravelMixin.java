package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses native travel for harness-owned mobs and eligible incapacitated medical bodies. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMindHarnessTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), require = 1)
    private void moonstation14$applyNativeTetherImpulse(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof LivingEntity living && !living.level().isClientSide
                && (!(entity instanceof Mob mob) || !((MindControlledMob) mob).moonstation14$isMovementOwned())) {
            Vec3 delta = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                    .deltaVelocityPerSecond(living).scale(.05);
            if (delta.lengthSqr() > 0) living.setDeltaMovement(living.getDeltaMovement().add(delta));
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$skipVanillaTravelForHarnessOwnedMob(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof Mob mob && (com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics
                .eligible(mob) || ((MindControlledMob) mob).moonstation14$isMovementOwned())) {
            LivingEntity body = (LivingEntity) entity;
            // Owned living bodies suppress vanilla travel. Retained medical corpses still need
            // authoritative collision-resolved motion, without restoring AI or owner input.
            // Tracked remote clients cannot infer authoritative accepted displacement here; the owning
            // server and local predictor update walk animation at their explicit movement step instead.
            callback.cancel();
        }
    }
}
