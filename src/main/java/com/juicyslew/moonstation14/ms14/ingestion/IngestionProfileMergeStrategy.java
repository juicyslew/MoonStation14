package com.juicyslew.moonstation14.ms14.ingestion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolutionException;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Inherits individual edible fields so child profiles retain parent defaults. */
public final class IngestionProfileMergeStrategy implements PrototypeMergeStrategy {
    @Override
    public boolean handles(String fieldName) {
        return "edible".equals(fieldName);
    }

    @Override
    public JsonElement merge(String fieldName, ResourceLocation childId, JsonObject child,
                             List<JsonObject> resolvedParents) {
        if (!handles(fieldName)) return null;
        JsonObject result = new JsonObject();
        for (int i = 0; i < resolvedParents.size(); i++) {
            mergeObject(resolvedParents.get(i), result, false, childId, "parent[" + i + "]");
        }
        if (child.has(fieldName)) mergeObject(child, result, true, childId, "child");
        return result.size() == 0 ? null : result;
    }

    private static void mergeObject(JsonObject source, JsonObject target, boolean child,
                                    ResourceLocation id, String sourceKind) {
        JsonElement edible = source.get("edible");
        if (edible == null) return;
        if (!edible.isJsonObject())
            throw new PrototypeResolutionException("Ingestion profile '" + id + "' has malformed "
                    + sourceKind + " edible field: expected an object");
        for (Map.Entry<String, JsonElement> entry : edible.getAsJsonObject().entrySet()) {
            if (child || !target.has(entry.getKey())) target.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }
}
