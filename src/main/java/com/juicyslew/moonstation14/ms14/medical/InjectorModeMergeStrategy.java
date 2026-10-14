package com.juicyslew.moonstation14.ms14.medical;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class InjectorModeMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("kind") || field.equals("transfer_amounts") || field.equals("ignore_delay_for_volume") || field.equals("mob_time_seconds") || field.equals("container_draw_time_seconds") || field.equals("additional_delay_per_unit_seconds") || field.equals("self_modifier") || field.equals("downed_modifier"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonElement value = child.get(field);
        if (value != null) return value.deepCopy();
        for (JsonObject parent : parents) if (parent.has(field)) return parent.get(field).deepCopy();
        return null;
    }
}

