package com.juicyslew.moonstation14.ms14.devicenet;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Recursively merges supported policy records while preserving explicit nested overrides. */
public final class NetworkDeviceItemMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("deviceComponents") || field.equals("configurator"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonObject result = new JsonObject();
        for (JsonObject parent : parents) if (parent.has(field) && parent.get(field).isJsonObject()) overlay(result, parent.getAsJsonObject(field));
        if (child.has(field) && child.get(field).isJsonObject()) overlay(result, child.getAsJsonObject(field));
        return result;
    }
    private static void overlay(JsonObject target, JsonObject source) {
        source.entrySet().forEach(e -> target.add(e.getKey(), e.getValue().deepCopy()));
    }
}
