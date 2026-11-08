package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Fieldwise inherited defaults; tags arrays replace the parent value. */
public final class BatteryProfileMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("capacity") || field.equals("starting_charge") || field.equals("tags"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonElement value = null;
        for (JsonObject parent : parents) if (parent.has(field)) value = parent.get(field).deepCopy();
        if (child.has(field)) value = child.get(field).deepCopy();
        return value;
    }
}
