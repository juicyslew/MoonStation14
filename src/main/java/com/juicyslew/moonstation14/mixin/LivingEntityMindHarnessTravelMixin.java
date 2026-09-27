package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses vanilla travel only while a mob is explicitly harness-owned. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMindHarnessTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$skipVanillaTravelForHarnessOwnedMob(Vec3 input, CallbackInfo callback) {
        Object entity = this;
        if (entity instanceof Mob && ((MindControlledMob) entity).moonstation14$isMovementOwned()) {
            // Tracked remote clients cannot infer authoritative accepted displacement here; the owning
            // server and local predictor update walk animation at their explicit movement step instead.
            callback.cancel();
        }
    }
}
