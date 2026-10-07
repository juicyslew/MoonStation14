package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPayload;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPresentationPolicy;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies semantic source presentation before vanilla sends its hurt-event packet. */
@Mixin(LivingEntity.class)
abstract class LivingEntityDamageFeedbackMixin {
    @Unique private boolean moonstation14$clearNativeHurtCounter;

    /** Suppress only the native hurt vocalization; death/gasp sound paths are untouched. */
    @Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
    private void moonstation14$sourceAwareHurtSound(DamageSource source, CallbackInfo ci) {
        LivingEntity living = (LivingEntity) (Object) this;
        if (!living.level().isClientSide() && !DamageSystem.feedbackBeforeNativePacket(living, source).hurtSound()) ci.cancel();
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void moonstation14$beginFeedback(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        moonstation14$clearNativeHurtCounter = false;
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void moonstation14$finishFeedback(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (moonstation14$clearNativeHurtCounter) {
            ((LivingEntity) (Object) this).hurtTime = 0;
            moonstation14$clearNativeHurtCounter = false;
        }
    }

    @Redirect(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void moonstation14$damageFeedback(Level level, Entity target, DamageSource source) {
        if (!(target instanceof LivingEntity living) || level.isClientSide()) {
            level.broadcastDamageEvent(target, source);
            return;
        }
        DamageFeedbackProfile profile = DamageSystem.feedbackBeforeNativePacket(living, source);
        DamageFeedbackPayload.publish(living, profile);
        if (DamageFeedbackPresentationPolicy.useNativeDamageEvent(profile)) level.broadcastDamageEvent(target, source);
        else moonstation14$clearNativeHurtCounter = DamageFeedbackPresentationPolicy.clearServerHurtCounter(profile);
    }
}
