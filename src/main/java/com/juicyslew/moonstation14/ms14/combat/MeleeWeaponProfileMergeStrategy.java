package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolutionException;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Merges partial weapon component fields using the shared prototype resolver. */
public final class MeleeWeaponProfileMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String fieldName) { return "weapon".equals(fieldName) || "damage_on_hit".equals(fieldName) || "chemical_injector".equals(fieldName); }
    @Override public JsonElement merge(String fieldName, ResourceLocation childId, JsonObject child, List<JsonObject> parents) {
        if (!handles(fieldName)) return null;
        if (!"weapon".equals(fieldName)) {
            JsonObject result = new JsonObject();
            for (JsonObject parent : parents) {
                JsonElement value = parent.get(fieldName);
                if (value != null) {
                    if (!value.isJsonObject()) throw new PrototypeResolutionException("Melee weapon profile '" + childId + "' has malformed " + fieldName);
                    value.getAsJsonObject().entrySet().forEach(e -> { if (!result.has(e.getKey())) result.add(e.getKey(), e.getValue().deepCopy()); });
                }
            }
            if (child.has(fieldName)) {
                JsonElement value = child.get(fieldName);
                if (!value.isJsonObject()) throw new PrototypeResolutionException("Melee weapon profile '" + childId + "' has malformed " + fieldName);
                value.getAsJsonObject().entrySet().forEach(e -> result.add(e.getKey(), e.getValue().deepCopy()));
            }
            return result.size() == 0 ? null : result;
        }
        JsonObject result = new JsonObject();
        for (int i = 0; i < parents.size(); i++) mergeInto(parents.get(i), result, false, childId, "parent[" + i + "]");
        if (child.has(fieldName)) mergeInto(child, result, true, childId, "child");
        return result.size() == 0 ? null : result;
    }
    private static void mergeInto(JsonObject src, JsonObject dst, boolean overwrite, ResourceLocation id, String kind) {
        JsonElement weapon = src.get("weapon");
        if (weapon == null) return;
        if (!weapon.isJsonObject()) throw new PrototypeResolutionException("Melee weapon profile '" + id + "' has malformed " + kind + " weapon field");
        for (Map.Entry<String, JsonElement> e : weapon.getAsJsonObject().entrySet())
            if (("chemical_injector".equals(e.getKey()) || "audio".equals(e.getKey())) && e.getValue().isJsonObject() && dst.has(e.getKey()) && dst.get(e.getKey()).isJsonObject()) {
                JsonObject nested = dst.getAsJsonObject(e.getKey());
                e.getValue().getAsJsonObject().entrySet().forEach(n -> {
                    if ("audio".equals(e.getKey()) && n.getValue().isJsonObject() && nested.has(n.getKey()) && nested.get(n.getKey()).isJsonObject()) {
                        JsonObject cue = nested.getAsJsonObject(n.getKey());
                        n.getValue().getAsJsonObject().entrySet().forEach(setting -> { if (overwrite || !cue.has(setting.getKey())) cue.add(setting.getKey(), setting.getValue().deepCopy()); });
                    } else if (overwrite || !nested.has(n.getKey())) nested.add(n.getKey(), n.getValue().deepCopy());
                });
            } else if (overwrite || !dst.has(e.getKey())) dst.add(e.getKey(), e.getValue().deepCopy());
    }
}
