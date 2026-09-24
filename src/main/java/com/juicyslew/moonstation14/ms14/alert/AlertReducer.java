package com.juicyslew.moonstation14.ms14.alert;

import com.juicyslew.moonstation14.component.codec.json.AlertData;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/** Pure state transitions for AdjustAlert. */
public final class AlertReducer {
    private AlertReducer() {}
    public static Map<ResourceKey<AlertData>, AlertInstance> apply(Map<ResourceKey<AlertData>, AlertInstance> current,
            ResourceKey<AlertData> key, boolean clear, long timeTicks, boolean showCooldown, long now) {
        return apply(current, key, clear, timeTicks, showCooldown, now, java.util.Optional.empty());
    }
    public static Map<ResourceKey<AlertData>, AlertInstance> apply(Map<ResourceKey<AlertData>, AlertInstance> current,
            ResourceKey<AlertData> key, boolean clear, long timeTicks, boolean showCooldown, long now,
            java.util.Optional<net.minecraft.resources.ResourceLocation> category) {
        java.util.Objects.requireNonNull(current, "current");
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(category, "category");
        current.forEach((storedKey, instance) -> {
            java.util.Objects.requireNonNull(storedKey, "alert key");
            java.util.Objects.requireNonNull(instance, "alert instance");
        });
        if (timeTicks < 0) throw new IllegalArgumentException("timeTicks must be nonnegative");
        HashMap<ResourceKey<AlertData>, AlertInstance> next = new HashMap<>(current);
        if (clear && timeTicks == 0) next.remove(key);
        else {
            OptionalLong deadline = timeTicks == 0 ? OptionalLong.empty() : OptionalLong.of(Math.addExact(now, timeTicks));
            next.put(key, new AlertInstance(deadline, showCooldown));
        }
        if (next.size() > AlertComponent.MAX_ENTRIES) {
            throw new IllegalArgumentException("alert map exceeds maximum of " + AlertComponent.MAX_ENTRIES);
        }
        return Map.copyOf(next);
    }
    public static Map<ResourceKey<AlertData>, AlertInstance> expire(Map<ResourceKey<AlertData>, AlertInstance> current, long now) {
        HashMap<ResourceKey<AlertData>, AlertInstance> next = new HashMap<>(current);
        next.entrySet().removeIf(entry -> entry.getValue().deadline().isPresent() && now >= entry.getValue().deadline().getAsLong());
        return Map.copyOf(next);
    }

    /** Removes extant alerts sharing the requested category before the new state is reduced. */
    public static Map<ResourceKey<AlertData>, AlertInstance> replaceCategory(
            Map<ResourceKey<AlertData>, AlertInstance> state,
            ResourceKey<AlertData> requested,
            java.util.Optional<net.minecraft.resources.ResourceLocation> requestedCategory,
            java.util.function.Function<ResourceKey<AlertData>, java.util.Optional<net.minecraft.resources.ResourceLocation>> categories) {
        java.util.Objects.requireNonNull(state, "state");
        java.util.Objects.requireNonNull(requested, "requested");
        java.util.Objects.requireNonNull(requestedCategory, "requestedCategory");
        java.util.Objects.requireNonNull(categories, "categories");
        if (requestedCategory.isEmpty()) return Map.copyOf(state);
        HashMap<ResourceKey<AlertData>, AlertInstance> result = new HashMap<>(state);
        for (ResourceKey<AlertData> key : state.keySet()) {
            if (!key.equals(requested) && categories.apply(key).filter(requestedCategory.get()::equals).isPresent()) {
                result.remove(key);
            }
        }
        return Map.copyOf(result);
    }
}
