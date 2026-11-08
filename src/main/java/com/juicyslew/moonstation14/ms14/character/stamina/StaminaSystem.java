package com.juicyslew.moonstation14.ms14.character.stamina;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import java.util.OptionalInt;

/** Server-owned stamina resource, consumed only by a resolved character Stamina composition. */
public final class StaminaSystem {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> LOW =
            com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.createKey("stamina_low");
    private StaminaSystem() { }

    public enum DamageResult { UNSUPPORTED, CANCELLED, INVALID_EVENT_VALUE, APPLIED }

    public static StaminaState state(LivingEntity entity) {
        StaminaAttachment attachment = entity.getExistingDataOrNull(ModDataAttachments.STAMINA.get());
        return attachment == null ? StaminaState.EMPTY : attachment.state();
    }

    public static DamageResult takeStaminaDamage(LivingEntity target, float amount) {
        return takeStaminaDamage(target, amount, false);
    }

    public static DamageResult takeStaminaDamage(LivingEntity target, float amount, boolean ignoreResistance) {
        if (target == null || !(target.level() instanceof ServerLevel level) || !Float.isFinite(amount))
            return DamageResult.UNSUPPORTED;
        StaminaPrototypeComponent policy = policy(target);
        if (policy == null) return DamageResult.UNSUPPORTED;
        BeforeStaminaDamageEvent event = new BeforeStaminaDamageEvent(target, amount);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return DamageResult.CANCELLED;
        float admitted = ignoreResistance ? amount : event.amount();
        if (!Float.isFinite(admitted)) return DamageResult.INVALID_EVENT_VALUE;
        StaminaState before = state(target);
        StaminaReducer.DeltaResult result = StaminaReducer.applyDelta(policy, before, admitted, level.getGameTime());
        if (result.state().equals(before)) return DamageResult.APPLIED;
        publish(target, result.state());
        if (before.afterCritical() && !result.state().afterCritical() && result.state().damage() <= 0)
            removeLowStatus(target);
        else updateLowStatus(target, policy, result.state());
        if (result.enteredCritical())
            CharacterControlSystem.extendParalyze(target, StatusEffectSystem.secondsToTicks(policy.stunSeconds()));
        return DamageResult.APPLIED;
    }

    public static void tick(LivingEntity entity, ServerLevel level) {
        if (entity.level().isClientSide || entity.level() != level) return;
        StaminaPrototypeComponent policy = policy(entity);
        StaminaAttachment existing = entity.getExistingDataOrNull(ModDataAttachments.STAMINA.get());
        if (policy == null) {
            if (existing != null) {
                entity.removeData(ModDataAttachments.STAMINA.get());
                entity.syncData(ModDataAttachments.STAMINA.get());
            }
            removeLowStatus(entity);
            return;
        }
        if (existing == null) return; // Capability is configured, but neutral reads never materialize it.
        var plan = StaminaReducer.recoveryPlan(policy, existing.state(), level.getGameTime());
        if (plan.isEmpty()) return;
        publish(entity, plan.orElseThrow().state());
        takeStaminaDamage(entity, plan.orElseThrow().requestedDelta());
    }

    private static StaminaPrototypeComponent policy(LivingEntity entity) {
        return ActiveCharacterPolicy.resolveActor(entity)
                .flatMap(character -> character.component(StaminaPrototypeComponent.class)).orElse(null);
    }

    private static void publish(LivingEntity entity, StaminaState state) {
        if (!(entity instanceof IAttachmentHolder holder)) return;
        holder.setData(ModDataAttachments.STAMINA.get(), new StaminaAttachment(state));
        holder.syncData(ModDataAttachments.STAMINA.get());
    }

    private static void updateLowStatus(LivingEntity entity, StaminaPrototypeComponent policy, StaminaState state) {
        if (!(entity instanceof IStatusEffectTrait trait) || !(entity.level() instanceof ServerLevel level)) return;
        StatusEffectSystem.apply(trait.toHandleSelf(), level, LOW, StatusEffectOperation.UPDATE,
                OptionalInt.empty(), 0, new StatusEffectPayload.MovementSpeedModifier(
                        (float) StaminaReducer.slowdownMultiplier(policy, state)));
    }

    private static void removeLowStatus(LivingEntity entity) {
        if (!(entity instanceof IStatusEffectTrait trait) || !(entity.level() instanceof ServerLevel level)
                || !StatusEffectSystem.hasStatus(trait.toHandleSelf(), level, LOW)) return;
        StatusEffectSystem.apply(trait.toHandleSelf(), level, LOW, StatusEffectOperation.REMOVE,
                OptionalInt.empty(), 0);
    }
}
