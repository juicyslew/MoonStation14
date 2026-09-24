package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Supplies field-specific inheritance without making the raw resolver aware
 * of a particular prototype domain.
 */
@FunctionalInterface
public interface PrototypeMergeStrategy {
    /**
     * Returns a replacement for a handled field, or {@code null} when the
     * field should be absent. Unhandled fields are merged by the resolver.
     */
    JsonElement merge(String fieldName, ResourceLocation childId, JsonObject child,
                      List<JsonObject> resolvedParents);

    /** Returns whether this strategy owns the named top-level field. */
    default boolean handles(String fieldName) {
        return false;
    }
}
