package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import net.minecraft.resources.ResourceKey;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.Optional;

/**
 * Compatibility-only adapter for the obsolete upstream GenericStatusEffect.
 *
 * <p>This intentionally has a closed allowlist.  In particular, a decoded
 * component name is never treated as a Java class name and never creates a
 * marker for a capability that MoonStation does not implement.</p>
 */
@Deprecated(forRemoval = false)
final class GenericStatusEffectAdapter {
    private record Alias(String key, String component) {
    }

    private static final Alias JITTERING_ALIAS = new Alias("jitter", "jittering");
    private static final Alias JITTER_WITHOUT_COMPONENT = new Alias("jitter", "");
    private static final Map<Alias, ResourceKey<StatusEffectData>> ALLOWLIST = Map.of(
            JITTERING_ALIAS, com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.createKey("jitter"),
            JITTER_WITHOUT_COMPONENT, com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects.createKey("jitter"));

    private final Consumer<String> warningSink;
    private final Set<Alias> warnedAliases = ConcurrentHashMap.newKeySet();

    static GenericStatusEffectAdapter production() {
        return new GenericStatusEffectAdapter(message -> MoonStation14.LOGGER.warn(
                "GenericStatusEffect compatibility alias unsupported: {}", message));
    }

    GenericStatusEffectAdapter(Consumer<String> warningSink) {
        this.warningSink = java.util.Objects.requireNonNull(warningSink, "warningSink");
    }

    EffectResult apply(EffectData.GenericStatusEffect effect, EffectContext context) {
        var target = EffectHandlers.statusTarget(context);
        if (target.isEmpty()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        Alias alias = normalize(effect.effectKey(), effect.component());
        Optional<ResourceKey<StatusEffectData>> resolved = resolveAlias(alias);
        if (resolved.isEmpty()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        ResourceKey<StatusEffectData> canonical = resolved.orElseThrow();

        // This is the old system's distinction: an update/add needed a
        // component to instantiate, while remove/set could address a legacy
        // key without constructing one.
        if ((effect.subType() == StatusEffectOperation.UPDATE
                || effect.subType() == StatusEffectOperation.ADD)
                && alias.component().isEmpty()) {
            warn(alias, "update/add requires an allowlisted component alias");
            return EffectResult.SKIPPED_UNSUPPORTED;
        }

        // Legacy TrySetTime only adjusts an existing status. Unlike the new
        // ModifyStatusEffect SET operation, it does not create one.
        if (effect.subType() == StatusEffectOperation.SET
                && !StatusEffectSystem.hasStatus(target.orElseThrow(), context.level(), canonical)) {
            return EffectResult.APPLIED;
        }

        EffectHandlers.DurationConversion duration = EffectHandlers.scaledDuration(
                effect.time(), context.scale());
        duration = EffectHandlers.cadenceDuration(duration, context, false, 0, effect.subType());
        if (duration.zero()) {
            return EffectResult.APPLIED;
        }

        StatusEffectSystem.apply(target.orElseThrow(), context.level(), canonical,
                effect.subType(), duration.ticks(), 0);
        return EffectResult.APPLIED;
    }

    /** Pure/testable allowlist resolution; unknown aliases warn at most once. */
    Optional<ResourceKey<StatusEffectData>> resolveAlias(String key, String component) {
        return resolveAlias(normalize(key, component));
    }

    private Optional<ResourceKey<StatusEffectData>> resolveAlias(Alias alias) {
        ResourceKey<StatusEffectData> canonical = ALLOWLIST.get(alias);
        if (canonical == null) {
            warn(alias, "no allowlisted canonical status");
            return Optional.empty();
        }
        return Optional.of(canonical);
    }

    private void warn(Alias alias, String reason) {
        if (warnedAliases.add(alias)) {
            warningSink.accept(alias.key() + "/" + alias.component() + ": " + reason);
        }
    }

    private static Alias normalize(String key, String component) {
        return new Alias(normalizePart(key), normalizePart(component));
    }

    private static String normalizePart(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
