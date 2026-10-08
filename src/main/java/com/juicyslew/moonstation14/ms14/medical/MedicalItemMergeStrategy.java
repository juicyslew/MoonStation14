package com.juicyslew.moonstation14.ms14.medical;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Medical overrides replace scalar fields and shallow-merge damage deltas. */
public final class MedicalItemMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("item") || field.equals("stack") || field.equals("healing") || field.equals("health_analyzer") || field.equals("ui") || field.equals("toggle") || field.equals("syringe_policy"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonObject result = new JsonObject();
        for (JsonObject parent : parents) if (parent.has(field) && parent.get(field).isJsonObject())
            overlay(result, parent.getAsJsonObject(field));
        if (child.has(field) && child.get(field).isJsonObject()) overlay(result, child.getAsJsonObject(field));
        return result;
    }
    private static void overlay(JsonObject target, JsonObject source) {
        source.entrySet().forEach(entry -> {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && target.has(key) && target.get(key).isJsonObject()) {
                JsonObject nested = target.getAsJsonObject(key).deepCopy();
                value.getAsJsonObject().entrySet().forEach(child -> nested.add(child.getKey(), child.getValue().deepCopy()));
                target.add(key, nested);
            } else target.add(key, value.deepCopy());
        });
    }
}
