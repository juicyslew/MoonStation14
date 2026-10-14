package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Field-wise parent defaults with child chemical entries replacing same reagent IDs. */
public final class SeedProfileMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return List.of("biology", "lifecycle", "chemicals").contains(field); }
    @Override public JsonElement merge(String field, ResourceLocation childId, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonObject result = new JsonObject();
        for (JsonObject parent : parents) mergeObject(parent.get(field), result, false);
        mergeObject(child.get(field), result, true);
        // An explicitly empty chemicals object represents the SeedData C# default
        // (no inherited chemistry), and must remain distinguishable from absence.
        return result.size() == 0 && !child.has(field) ? null : result;
    }
    private static void mergeObject(JsonElement raw, JsonObject into, boolean child) {
        if (raw == null) return;
        if (!raw.isJsonObject()) throw new IllegalArgumentException("seed " + raw + " must be an object");
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet())
            if (child || !into.has(entry.getKey())) into.add(entry.getKey(), entry.getValue().deepCopy());
    }
}
