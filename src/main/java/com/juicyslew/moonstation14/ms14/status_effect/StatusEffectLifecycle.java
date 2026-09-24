package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import net.minecraft.resources.ResourceKey;

/**
 * Lifecycle boundary for a status effect definition.
 *
 * <p>The interface intentionally has no world or entity dependency.  A
 * production implementation may capture its target when it is constructed,
 * while tests can provide a recording implementation without constructing a
 * world.</p>
 */
public interface StatusEffectLifecycle {
    boolean canApply(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                     StatusEffectInstance instance);

    void onApplied(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                   StatusEffectInstance instance);

    void onChanged(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                   StatusEffectInstance instance);

    /**
     * Reconciles the behavior projection for an active instance.
     *
     * <p>This hook must be idempotent and safe to call repeatedly.  It is used
     * on every active tick and when an entity is reloaded or reconciled.</p>
     */
    void ensureApplied(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                       StatusEffectInstance instance);

    void onRemoved(ResourceKey<StatusEffectData> key, StatusEffectData definition,
                   StatusEffectInstance instance);

    /**
     * Removes projections for a status whose definition disappeared during a
     * reload.  There is intentionally no definition argument: invalidated
     * definitions have no behavior list to consult.
     */
    default void onInvalidated(ResourceKey<StatusEffectData> key, StatusEffectInstance instance) {
    }
}
