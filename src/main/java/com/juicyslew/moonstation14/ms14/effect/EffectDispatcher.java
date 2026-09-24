package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.EffectData;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Type-safe registry and dispatcher for data-only effect definitions. */
public final class EffectDispatcher {
    private final Map<Class<? extends EffectData>, EffectHandler<?>> handlers = new ConcurrentHashMap<>();
    private final Map<Class<? extends EffectData>, String> unsupportedReasons = new ConcurrentHashMap<>();
    private final Map<Class<? extends EffectData>, Boolean> warnedUnsupported = new ConcurrentHashMap<>();
    private final Consumer<EffectData> unsupportedWarning;
    private final boolean productionWarnings;
    private final Object registrationLock = new Object();

    public EffectDispatcher() {
        this.unsupportedWarning = effect -> { };
        this.productionWarnings = true;
    }

    EffectDispatcher(Consumer<EffectData> unsupportedWarning) {
        this.unsupportedWarning = Objects.requireNonNull(unsupportedWarning, "unsupportedWarning");
        this.productionWarnings = false;
    }

    public <T extends EffectData> void register(Class<T> type, EffectHandler<T> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        synchronized (registrationLock) {
            if (handlers.containsKey(type)) {
                throw new IllegalArgumentException("An effect handler is already registered for " + type.getName());
            }
            if (unsupportedReasons.containsKey(type)) {
                throw new IllegalArgumentException("An unsupported effect is already registered for " + type.getName());
            }
            handlers.put(type, handler);
        }
    }

    /** Registers a known effect whose owning capability is intentionally deferred or unavailable. */
    public <T extends EffectData> void registerUnsupported(Class<T> type, String reason) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("unsupported reason must not be blank");
        }
        synchronized (registrationLock) {
            if (handlers.containsKey(type)) {
                throw new IllegalArgumentException("An effect handler is already registered for " + type.getName());
            }
            if (unsupportedReasons.containsKey(type)) {
                throw new IllegalArgumentException("An unsupported effect is already registered for " + type.getName());
            }
            unsupportedReasons.put(type, reason);
        }
    }

    /** Package-private audit view used by the exhaustive support-matrix tests. */
    Set<Class<? extends EffectData>> registeredHandlerTypes() {
        return Set.copyOf(handlers.keySet());
    }

    /** Package-private audit view used by the exhaustive support-matrix tests. */
    Map<Class<? extends EffectData>, String> registeredUnsupportedTypes() {
        return Map.copyOf(unsupportedReasons);
    }

    public EffectResult dispatch(EffectData effect, EffectContext context) {
        Objects.requireNonNull(effect, "effect");
        Class<? extends EffectData> type = effect.getClass();
        String reason = unsupportedReasons.get(type);
        if (reason != null) {
            warnUnsupported(effect, reason);
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        EffectHandler<?> handler = handlers.get(type);
        if (handler == null) {
            warnUnsupported(effect, null);
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        Objects.requireNonNull(context, "context");
        return invoke(handler, effect, context);
    }

    private void warnUnsupported(EffectData effect, String reason) {
        if (warnedUnsupported.putIfAbsent(effect.getClass(), Boolean.TRUE) != null) {
            return;
        }
        if (productionWarnings) {
            if (reason != null) {
                MoonStation14.LOGGER.warn("Effect {} is explicitly unsupported: {}", effect.type(), reason);
            } else {
                MoonStation14.LOGGER.warn("Effect {} has no classified handler; returning unsupported", effect.type());
            }
        }
        unsupportedWarning.accept(effect);
    }

    @SuppressWarnings("unchecked")
    private static <T extends EffectData> EffectResult invoke(
            EffectHandler<?> handler, EffectData effect, EffectContext context) {
        return ((EffectHandler<T>) handler).apply((T) effect, context);
    }
}
