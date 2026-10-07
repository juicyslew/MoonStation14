package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.standing.PhysicalPosture;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps medically-dead prototype bodies loaded as corpses instead of vanilla's 20-tick cleanup. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMedicalCorpseMixin {
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
