package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Resolves supported inherited machine fields, merging nested input policy fieldwise. */
public final class ChemMasterMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("powerLoadWatts") || field.equals("cableReach")
            || field.equals("uiRange") || field.equals("inputSlot") || field.equals("clickSound")
            || field.equals("slotInsertSound") || field.equals("slotEjectSound") || field.equals("dumpableSolution"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        if (!field.equals("inputSlot") && !field.equals("dumpableSolution")) {
            JsonElement result = null;
            for (JsonObject parent : parents) if (parent.has(field)) result = parent.get(field).deepCopy();
            return child.has(field) ? child.get(field).deepCopy() : result;
        }
        JsonObject result = new JsonObject();
        for (JsonObject parent : parents) if (parent.has(field) && parent.get(field).isJsonObject())
            parent.getAsJsonObject(field).entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue().deepCopy()));
        if (child.has(field) && child.get(field).isJsonObject())
            child.getAsJsonObject(field).entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue().deepCopy()));
        return result;
    }
}

