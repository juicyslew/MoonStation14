package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.google.gson.*;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Parent-first merge of optional capability records, with child field overrides. */
public final class ThrownItemProfileMergeStrategy implements PrototypeMergeStrategy {
    private static final List<String> FIELDS = List.of("damage_on_hit","embedding","inject_on_embed","ensnaring","collision_sound");
    @Override public boolean handles(String name) { return FIELDS.contains(name); }
    @Override public JsonElement merge(String name, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(name)) return null;
        JsonObject result = new JsonObject();
        for (JsonObject parent : parents) append(parent, name, result, false, id);
        append(child, name, result, true, id);
        return result.size() == 0 ? null : result;
    }
    private static void append(JsonObject source, String name, JsonObject result, boolean overwrite, ResourceLocation id) {
        JsonElement value = source.get(name); if (value == null) return;
        if (!value.isJsonObject()) throw new PrototypeResolutionException("Thrown profile '"+id+"' has malformed "+name);
        value.getAsJsonObject().entrySet().forEach(e -> { if (overwrite || !result.has(e.getKey())) result.add(e.getKey(),e.getValue().deepCopy()); });
    }
}
