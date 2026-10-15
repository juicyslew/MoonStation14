package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.standing.PhysicalPosture;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps medically-dead prototype bodies loaded as corpses instead of vanilla's 20-tick cleanup. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMedicalCorpseMixin {
    @Unique private transient boolean moonstation14$retainedCorpseDamage;

    /** Permit the native damage pipeline through its dead-health checks only for a corpse already
     * medically DEAD at hurt entry. Incoming-damage hooks, armor, cancellation, and post events remain vanilla/NeoForge-owned. */
    @WrapMethod(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private boolean moonstation14$damageRetainedCorpse(DamageSource source, float amount, Operation<Boolean> original) {
        boolean previous = moonstation14$retainedCorpseDamage;
        LivingEntity body = (LivingEntity) (Object) this;
        moonstation14$retainedCorpseDamage = com.juicyslew.moonstation14.ms14.mobs.MobStateSystem
                .acceptsPhysicalCorpseDamage(body);
        try { return original.call(source, amount); }
        finally { moonstation14$retainedCorpseDamage = previous; }
    }

    @ModifyExpressionValue(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDeadOrDying()Z", ordinal = 0), require = 1)
    private boolean moonstation14$allowRetainedCorpsePastInitialDeadGate(boolean dead) {
        return dead && !moonstation14$retainedCorpseDamage;
    }

    @ModifyExpressionValue(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDeadOrDying()Z", ordinal = 1), require = 1)
    private boolean moonstation14$avoidRepeatedDeathForRetainedCorpse(boolean dead) {
        return dead && !moonstation14$retainedCorpseDamage;
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"), require = 1)
    private void moonstation14$restoreMedicalCorpsePosture(DamageSource damageSource, CallbackInfo callback) {
        LivingEntity body = (LivingEntity) (Object) this;
        if (MobStateSystem.policy(body) != null && MobStateSystem.isDead(body) && PostureSystem.isDown(body)) {
            PhysicalPosture.apply(body);
            body.refreshDimensions();
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void moonstation14$stepOwnedIncapacitatedBody(CallbackInfo callback) {
        LivingEntity body = (LivingEntity) (Object) this;
        if (body instanceof Mob mob && com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(body)) {
            com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.step(mob);
        }
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$preserveMedicalCorpseDeathLoot(ServerLevel level, DamageSource damageSource,
                                                               CallbackInfo callback) {
        LivingEntity body = (LivingEntity) (Object) this;
        // Corpse-first adaptation: native death loot/equipment/XP must not duplicate the retained body; SS14 drops hands authoritatively.
        if (!level.isClientSide && MobStateSystem.policy(body) != null && MobStateSystem.isDead(body)) {
            callback.cancel();
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$retainMedicalCorpse(CallbackInfo callback) {
        LivingEntity body = (LivingEntity) (Object) this;
        if (MobStateSystem.policy(body) != null && MobStateSystem.isDead(body)) {
            // Leave the native death timer bounded at the corpse pose; cancel only tickDeath's removal.
            if (body.deathTime > 20) body.deathTime = 20;
            callback.cancel();
        }
    }
}

