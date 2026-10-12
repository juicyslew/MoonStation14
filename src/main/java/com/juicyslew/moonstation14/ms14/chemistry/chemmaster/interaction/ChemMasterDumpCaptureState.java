package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.BiFunction;

/** The dump service's single current capture per player, with explicit replacement and cleanup semantics. */
final class ChemMasterDumpCaptureState<T> {
    @FunctionalInterface interface Removal<T> { void accept(UUID player, T capture, String reason); }
    private final Map<UUID, T> captures = new HashMap<>();
    T get(UUID player) { return captures.get(player); }
    void replace(UUID player, T capture) { captures.put(player, capture); }
    boolean removeIf(UUID player, Predicate<T> owned) {
        T current = captures.get(player);
        if (current == null || !owned.test(current)) return false;
        captures.remove(player);
        return true;
    }
    void removeInvalid(BiFunction<UUID, T, String> invalidReason, Removal<T> removed) {
        captures.entrySet().removeIf(entry -> {
            String reason = invalidReason.apply(entry.getKey(), entry.getValue());
            if (reason == null) return false;
            removed.accept(entry.getKey(), entry.getValue(), reason);
            return true;
        });
    }
    void clear() { captures.clear(); }
}
