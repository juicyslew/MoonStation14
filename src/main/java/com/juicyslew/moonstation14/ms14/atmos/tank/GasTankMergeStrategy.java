package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Deep-merges initial gas species so a child can replace/add selected parent species. */
public final class GasTankMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("initialMoles"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonObject merged = new JsonObject();
        for (JsonObject parent : parents) if (parent.has(field) && parent.get(field).isJsonObject())
            parent.getAsJsonObject(field).entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
        if (child.has(field) && child.get(field).isJsonObject())
            child.getAsJsonObject(field).entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
        return merged;
    }
}

