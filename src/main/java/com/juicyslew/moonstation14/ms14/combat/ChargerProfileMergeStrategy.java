package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Inherited charger fields use nearest explicit parent, then child override. */
public final class ChargerProfileMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return SetHolder.FIELDS.contains(field); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonElement value = null;
        for (JsonObject parent : parents) if (parent.has(field)) value = parent.get(field).deepCopy();
        return child.has(field) ? child.get(field).deepCopy() : value;
    }
    private static final class SetHolder { static final java.util.Set<String> FIELDS = java.util.Set.of("idle_power", "charge_rate", "eject_on_interact", "slot_id", "accepted_capabilities"); }
}
