package com.juicyslew.moonstation14.ms14.fire;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.character.components.FlammablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative persistence and mutation boundary for character fire stacks.
 * This milestone applies configured typed direct damage through {@link DamageSystem},
 * but does not set vanilla fire ticks, play fire sounds, spread fire, or ignite equipment.
 */
public final class FireStackSystem {
    private static final Map<LivingEntity, Long> PROCESSED_FIRE_WINDOWS = new WeakHashMap<>();
    private FireStackSystem() {
    }

    public static EffectResult flammable(Entity target, float multiplier,
                                         Float multiplierOnExisting, float scale) {
        if (!(target instanceof LivingEntity living) || !supports(living)) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        try {
            FireStackComponent current = existing(living);
            FireStackComponent next = FireStackReducer.flammable(
                    current, multiplier, multiplierOnExisting, scale);
            persistIfChanged(living, current, next);
            return EffectResult.APPLIED;
        } catch (IllegalArgumentException exception) {
            return EffectResult.FAILED;
        }
    }

    public static EffectResult applyFlammable(Entity target, float multiplier,
                                              Float multiplierOnExisting, float scale) {
        return flammable(target, multiplier, multiplierOnExisting, scale);
    }

    public static EffectResult ignite(Entity target, float ignoredScale) {
        if (!(target instanceof LivingEntity living) || !supports(living)) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        FireStackComponent current = existing(living);
        if (current.ignited() || current.stacks() <= 0f) {
            return EffectResult.APPLIED;
        }
        persistIfChanged(living, current, FireStackReducer.ignite(current));
        return EffectResult.APPLIED;
    }

    public static EffectResult applyIgnite(Entity target, float ignoredScale) {
        return ignite(target, ignoredScale);
    }

    public static EffectResult extinguish(Entity target, float adjustment, float scale) {
        if (!(target instanceof LivingEntity living) || !supports(living)) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        try {
            FireStackComponent current = existing(living);
            FireStackComponent next = FireStackReducer.extinguish(current, adjustment, scale);
            persistIfChanged(living, current, next);
            return EffectResult.APPLIED;
        } catch (IllegalArgumentException exception) {
            return EffectResult.FAILED;
        }
    }

    public static EffectResult applyExtinguish(Entity target, float adjustment, float scale) {
        return extinguish(target, adjustment, scale);
    }

    /** Applies one due 20-tick fire lifecycle step without materializing absent state. */
    public static void dry(LivingEntity entity) {
        if (!supports(entity)) {
            EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, false);
            return;
        }
        FireStackComponent current = existing(entity);
        if (current.stacks() > 0f && current.ignited()) {
            processIgnited(entity, current);
            return;
        }
        if (current.stacks() >= 0f) {
            EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, false);
            return;
        }
        persistIfChanged(entity, current, FireStackReducer.dry(current));
    }

    private static void processIgnited(LivingEntity entity, FireStackComponent current) {
        var character = ActiveCharacterPolicy.resolveActor(entity).orElse(null);
        var flammable = character == null ? null : character.component(FlammablePrototypeComponent.class).orElse(null);
        if (flammable == null || !(entity.level() instanceof ServerLevel level)) {
            EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, false);
            return;
        }
        BlockPos eye = BlockPos.containing(entity.getEyePosition());
        var strict = AtmosphereService.INSTANCE.sample(level, eye);
        var reading = AtmosphereService.INSTANCE.readAtmosphere(level, eye);
        if (strict.isEmpty() || reading.isEmpty()) return;
        AtmosphereReading physical = reading.orElseThrow();
        LifecycleTransition transition = lifecycleTransition(current, strict.orElseThrow(),
                physical.status(), flammable.firestackFade(), flammable.damage().types());
        if (transition.deferred()) return;
        long window = Math.floorDiv(level.getGameTime() + entity.getId(), 20L);
        synchronized (PROCESSED_FIRE_WINDOWS) {
            if (!isNewFireWindow(PROCESSED_FIRE_WINDOWS.get(entity), window)) return;
            PROCESSED_FIRE_WINDOWS.put(entity, window);
        }
        applyFireStep(transition,
                heat -> com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureSystem.adjustHeat(entity, heat, 1f),
                damage -> DamageSystem.applyHealthChange(entity, damage, 1f, false));
        persistIfChanged(entity, current, transition.next());
    }

    static boolean isNewFireWindow(Long previousWindow, long currentWindow) {
        return previousWindow == null || previousWindow.longValue() != currentWindow;
    }

    /** Applies effects in lifecycle order, kept injectable for focused deterministic tests. */
    static void applyFireStep(LifecycleTransition transition, Consumer<Float> heatSink,
                              Consumer<Map<String, Float>> damageSink) {
        if (transition.deferred()) return;
        if (transition.heatJoules() > 0f) heatSink.accept(transition.heatJoules());
        if (!transition.directDamage().isEmpty()) damageSink.accept(transition.directDamage());
    }

    /** Pure lifecycle result, separating deferral from a completed zero-stack extinguish. */
    record LifecycleTransition(FireStackComponent next, float heatJoules, boolean deferred,
                               boolean extinguished, Map<String, Float> directDamage) {
        LifecycleTransition {
            directDamage = Map.copyOf(directDamage);
        }
    }

    static LifecycleTransition lifecycleTransition(FireStackComponent current, GasMixture strict,
                                                     AtmosphereReading.Status status, float fade) {
        return lifecycleTransition(current, strict, status, fade, Map.of());
    }

    static LifecycleTransition lifecycleTransition(FireStackComponent current, GasMixture strict,
                                                     AtmosphereReading.Status status, float fade,
                                                     Map<String, Float> configuredDamage) {
        if (current == null || status == null || (status != AtmosphereReading.Status.FINITE
                && status != AtmosphereReading.Status.EXTERIOR) || strict == null) {
            return new LifecycleTransition(current, 0f, true, false, Map.of());
        }
        if (current.stacks() <= 0f || !current.ignited()) {
            return new LifecycleTransition(current, 0f, true, false, Map.of());
        }
        if (strict.moles(GasType.OXYGEN) < 1.0) {
            return new LifecycleTransition(FireStackReducer.extinguish(current, 0f, 1f),
                    0f, false, true, Map.of());
        }
        Map<String, Float> directDamage = new LinkedHashMap<>();
        if (configuredDamage != null) {
            for (var entry : configuredDamage.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || !Float.isFinite(entry.getValue())
                        || !Float.isFinite(current.stacks()) || current.stacks() <= 0f) {
                    directDamage.clear();
                    break;
                }
                double value = (double) entry.getValue() * current.stacks();
                if (!Double.isFinite(value) || value > Float.MAX_VALUE) {
                    directDamage.clear();
                    break;
                }
                directDamage.put(entry.getKey(), (float) value);
            }
        }
        FireStackComponent next = FireStackReducer.fadeIgnited(current, fade);
        float heat = 12500f * current.stacks();
        if (!Float.isFinite(heat)) heat = 0f;
        return new LifecycleTransition(next, heat, false, next.isEmpty(), directDamage);
    }

    public static void dryOneInterval(LivingEntity entity) {
        dry(entity);
    }

    public static boolean needsDrying(FireStackComponent state) {
        return state != null && state.stacks() < 0f;
    }

    public static boolean supports(LivingEntity entity) {
        return entity != null && entity.level() instanceof ServerLevel && !entity.fireImmune()
                && ActiveCharacterPolicy.resolveActor(entity)
                .flatMap(character -> character.component(FlammablePrototypeComponent.class)).isPresent();
    }

    public static FireStackComponent existing(LivingEntity entity) {
        FireStackAttachment attachment = entity.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get());
        return attachment == null ? FireStackComponent.EMPTY : attachment.toComponent();
    }

    private static void persistIfChanged(LivingEntity entity, FireStackComponent before,
                                         FireStackComponent next) {
        if (before.equals(next)) {
            return;
        }
        if (next.isEmpty()) {
            entity.removeData(ModDataAttachments.FIRE_STACK.get());
            EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, false);
            return;
        }
        entity.setData(ModDataAttachments.FIRE_STACK.get(), next.toAttachment());
        EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING,
                needsDrying(next) || (next.stacks() > 0f && next.ignited()));
    }
}
