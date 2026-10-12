package com.juicyslew.moonstation14.ms14.chemistry.output;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Field-level parent composition for chemistry container capabilities. */
public final class ChemContainerMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("solution") || field.equals("storage"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonObject result = new JsonObject();
        for (JsonObject parent : parents) if (parent.has(field) && parent.get(field).isJsonObject())
            parent.getAsJsonObject(field).entrySet().forEach(e -> result.add(e.getKey(), e.getValue().deepCopy()));
        if (child.has(field) && child.get(field).isJsonObject())
            child.getAsJsonObject(field).entrySet().forEach(e -> result.add(e.getKey(), e.getValue().deepCopy()));
        return result;
    }
}
