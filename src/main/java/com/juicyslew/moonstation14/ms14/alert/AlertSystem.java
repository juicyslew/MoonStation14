package com.juicyslew.moonstation14.ms14.alert;

import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** Authoritative mutations and expiry for character-owned alert state. */
public final class AlertSystem {
    private AlertSystem() {
    }

    public enum Outcome { APPLIED, UNSUPPORTED_TARGET, FAILED }

    public static long secondsToTicks(float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0f)
            throw new IllegalArgumentException("alert time must be finite and nonnegative");
        double ticks = seconds * 20.0;
        if (ticks > Long.MAX_VALUE) throw new ArithmeticException("alert duration exceeds tick range");
        long rounded = Math.round(ticks);
        return seconds > 0f ? Math.max(1L, rounded) : 0L;
    }

    public static Outcome apply(LivingEntity entity, ServerLevel level, ResourceKey<AlertData> key,
                                boolean clear, long ticks, boolean cooldown) {
        return apply(entity, level, key, clear, ticks, cooldown, PrototypeRuntime.serverAlerts());
    }

    /** Catalog-injected seam shared by production dispatch and category tests. */
    public static Outcome apply(LivingEntity entity, ServerLevel level, ResourceKey<AlertData> key,
                                boolean clear, long ticks, boolean cooldown,
                                PrototypeCatalog<AlertData> catalog) {
        if (!(entity instanceof IStatusEffectTrait trait)) return Outcome.UNSUPPORTED_TARGET;
        if (ticks < 0 || key == null || catalog == null) return Outcome.FAILED;
        AlertData metadata = catalog.get(key.location());
        if (metadata == null) return Outcome.FAILED;

        TraitHandler<IStatusEffectTrait> handle = trait.toHandleSelf();
        AlertAttachment state = MS14Provider.getDetached(handle, MS14Bridges.ALERT);
        var before = state.toComponent();
        Map<ResourceKey<AlertData>, AlertInstance> old = state.snapshot();
        Map<ResourceKey<AlertData>, AlertInstance> filtered = new HashMap<>(old);
        if (!(clear && ticks == 0) && metadata.category().isPresent()) {
            for (ResourceKey<AlertData> existing : old.keySet())
                if (catalog.get(existing.location()) == null) return Outcome.FAILED;
            filtered = new HashMap<>(AlertReducer.replaceCategory(old, key, metadata.category(), existing -> {
                AlertData existingData = catalog.get(existing.location());
                return existingData == null ? java.util.Optional.empty() : existingData.category();
            }));
        }
        try {
            state.replace(AlertReducer.apply(filtered, key, clear, ticks, cooldown, level.getGameTime()));
        } catch (ArithmeticException | IllegalArgumentException failure) {
            return Outcome.FAILED;
        }
        MS14Provider.updateIfChanged(handle, MS14Bridges.ALERT, before, state);
        return Outcome.APPLIED;
    }

    /** Clears one owned key without requiring its prototype to remain loaded. */
    public static Outcome remove(LivingEntity entity, ResourceKey<AlertData> key) {
        if (!(entity instanceof IStatusEffectTrait trait)) return Outcome.UNSUPPORTED_TARGET;
        if (!entity.hasData(com.juicyslew.moonstation14.component.ModDataAttachments.ALERT.get()))
            return Outcome.APPLIED;
        TraitHandler<IStatusEffectTrait> handle = trait.toHandleSelf();
        AlertAttachment state = MS14Provider.getDetached(handle, MS14Bridges.ALERT);
        var before = state.toComponent();
        if (state.get(key).isEmpty()) return Outcome.APPLIED;
        try {
            state.replace(AlertReducer.apply(state.snapshot(), key, true, 0, false, 0));
        } catch (ArithmeticException | IllegalArgumentException failure) {
            return Outcome.FAILED;
        }
        MS14Provider.updateIfChanged(handle, MS14Bridges.ALERT, before, state);
        return Outcome.APPLIED;
    }

    public static void expire(LivingEntity entity, ServerLevel level) {
        if (!(entity instanceof IStatusEffectTrait trait)) return;
        TraitHandler<IStatusEffectTrait> handle = trait.toHandleSelf();
        AlertAttachment state = MS14Provider.getDetached(handle, MS14Bridges.ALERT);
        var before = state.toComponent();
        state.replace(AlertReducer.expire(state.snapshot(), level.getGameTime()));
        MS14Provider.updateIfChanged(handle, MS14Bridges.ALERT, before, state);
    }
}
