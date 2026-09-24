package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/**
 * Server-side lifecycle for a status attached to a living entity.
 *
 * <p>Behavior projections are visited in the declaration order supplied by
 * the definition; no status identifier is special-cased here.</p>
 */
public class DefaultStatusEffectLifecycle implements StatusEffectLifecycle {
    private final LivingEntity entity;
    private final ServerLevel level;

    public DefaultStatusEffectLifecycle(LivingEntity entity, ServerLevel level) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.level = Objects.requireNonNull(level, "level");
    }

    protected final LivingEntity entity() {
        return entity;
    }

    protected final ServerLevel level() {
        return level;
    }

    @Override
    public boolean canApply(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                            StatusEffectInstance instance) {
        Objects.requireNonNull(key, "status effect key");
        Objects.requireNonNull(definition, "status effect definition");
        Objects.requireNonNull(instance, "status effect instance");
        return definition.eligibility().contains(StatusEffectEligibility.LIVING_ENTITY)
                && (!definition.behaviors().contains(StatusEffectBehavior.MOVEMENT_SPEED)
                || entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null);
    }

    @Override
    public void onApplied(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                          StatusEffectInstance instance) {
        compose(definition, key, instance);
    }

    @Override
    public void onChanged(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                          StatusEffectInstance instance) {
        compose(definition, key, instance);
    }

    @Override
    public void ensureApplied(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                              StatusEffectInstance instance) {
        compose(definition, key, instance);
    }

    @Override
    public void onRemoved(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                          StatusEffectInstance instance) {
        MovementSpeedProjection.remove(entity, key);
    }

    @Override
    public void onInvalidated(ResourceKey<StatusEffectData> key, StatusEffectInstance instance) {
        MovementSpeedProjection.remove(entity, key);
    }

    private void compose(StatusEffectData definition, ResourceKey<StatusEffectData> key,
                         StatusEffectInstance instance) {
        Objects.requireNonNull(key, "status effect key");
        Objects.requireNonNull(instance, "status effect instance");
        StatusEffectPayload.MovementSpeedModifier movement = null;
        if (instance.isActive() && definition.behaviors().contains(StatusEffectBehavior.MOVEMENT_SPEED)) {
            float multiplier = instance.payload() instanceof StatusEffectPayload.MovementSpeedModifier payload
                    ? payload.multiplier()
                    : definition.movementSpeedMultiplier().orElseThrow(
                    () -> new IllegalStateException("movement status definition has no default multiplier"));
            movement = new StatusEffectPayload.MovementSpeedModifier(multiplier);
        }
        MovementSpeedProjection.reconcile(entity, key, movement);
        for (StatusEffectBehavior behavior : definition.behaviors()) {
            switch (behavior) {
                case MARKER -> markerNoOp();
                case CLIENT_JITTER -> clientJitterNoOp();
                case MOVEMENT_SPEED -> {
                    // Reconciled above from the authoritative attachment.
                }
            }
        }
    }

    /** Explicit server-side no-op for the marker projection in this milestone. */
    private void markerNoOp() {
        // Marker state is represented by the authoritative attachment only.
    }

    /** Explicit server-side no-op; presentation belongs to a later milestone. */
    private void clientJitterNoOp() {
        // Client jitter is intentionally not projected from the server yet.
    }
}
