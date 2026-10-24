package com.juicyslew.moonstation14.ms14.combat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
/** Fieldwise merge for partial inherited gun component data. */
public final class GunProfileMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("gun") || field.equals("revolver_ammo_provider") || field.equals("pacifism_allowed") || field.equals("ammo_counter"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        if (field.equals("pacifism_allowed") || field.equals("ammo_counter")) {
            JsonElement selected = child.get(field);
            if (selected == null) for (JsonObject parent : parents) if (parent.has(field)) { selected = parent.get(field); break; }
            return selected == null ? null : selected.deepCopy();
        }
        JsonObject result = new JsonObject();
        for (JsonObject parent : parents) add(result, parent.get(field), false);
        add(result, child.get(field), true);
        return result.size() == 0 ? null : result;
    }
    private static void add(JsonObject dst, JsonElement value, boolean overwrite) {
        if (value == null) return;
        if (!value.isJsonObject()) throw new IllegalArgumentException("gun profile component must be an object");
        value.getAsJsonObject().entrySet().forEach(e -> {
            if (e.getValue().isJsonObject() && dst.has(e.getKey()) && dst.get(e.getKey()).isJsonObject()) {
                JsonObject nested = dst.getAsJsonObject(e.getKey());
                e.getValue().getAsJsonObject().entrySet().forEach(n -> { if (overwrite || !nested.has(n.getKey())) nested.add(n.getKey(), n.getValue().deepCopy()); });
            } else if (overwrite || !dst.has(e.getKey())) dst.add(e.getKey(), e.getValue().deepCopy());
        });
    }
}
