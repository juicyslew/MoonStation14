package com.juicyslew.moonstation14.ms14.chemistry.beaker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Field-level inheritance merge, matching the existing medical composition strategy. */
public final class BeakerMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) {
        return field.equals("solutionContainer") || field.equals("fitsInDispenser") || field.equals("examinableSolution") || field.equals("drainableSolution") || field.equals("solutionVisuals") || field.equals("refillableSolution") || field.equals("solutionTransfer") || field.equals("drinkSolution");
    }

    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        if (field.equals("fitsInDispenser")) {
            JsonElement value = null;
            for (JsonObject parent : parents) if (parent.has(field)) value = parent.get(field).deepCopy();
            return child.has(field) ? child.get(field).deepCopy() : value;
        }
        JsonObject merged = new JsonObject();
        for (JsonObject parent : parents) if (parent.has(field) && parent.get(field).isJsonObject())
            parent.getAsJsonObject(field).entrySet().forEach(e -> merged.add(e.getKey(), e.getValue().deepCopy()));
        if (child.has(field) && child.get(field).isJsonObject())
            child.getAsJsonObject(field).entrySet().forEach(e -> merged.add(e.getKey(), e.getValue().deepCopy()));
        return merged;
    }
}
