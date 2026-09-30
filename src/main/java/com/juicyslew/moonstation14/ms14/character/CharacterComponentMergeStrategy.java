package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolutionException;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Character-only, type-keyed component inheritance. Parent components are
 * visited in declaration order; the first parent wins conflicting top-level
 * fields, while later parents fill missing fields of a shared type.
 * Child entries patch top-level fields of that inherited component, while
 * nested values (notably Barotrauma damage.types) replace the whole property.
 * No codec defaults are inserted during resolution.
 */
public final class CharacterComponentMergeStrategy implements PrototypeMergeStrategy {
    private static final String COMPONENTS = "components";

    @Override
    public boolean handles(String fieldName) {
        return COMPONENTS.equals(fieldName) || "hands".equals(fieldName);
    }

    @Override
    public JsonElement merge(String fieldName, ResourceLocation childId, JsonObject child,
                             List<JsonObject> resolvedParents) {
        if ("hands".equals(fieldName)) {
            if (child.has("hands")) throw new PrototypeResolutionException("Character '" + childId
                    + "': legacy top-level $.hands is unsupported; use components:[{type:'Hands',hands:[...]}]");
            return null;
        }
        if (!handles(fieldName)) return null;
        Map<String, JsonObject> inherited = new LinkedHashMap<>();
        boolean present = child.has(COMPONENTS);
        for (JsonObject parent : resolvedParents) {
            if (!parent.has(COMPONENTS)) continue;
            present = true;
            for (Map.Entry<String, JsonObject> entry : entries(childId, parent).entrySet()) {
                JsonObject merged = inherited.computeIfAbsent(entry.getKey(), ignored -> new JsonObject());
                for (Map.Entry<String, JsonElement> field : entry.getValue().entrySet()) {
                    if (!merged.has(field.getKey())) merged.add(field.getKey(), field.getValue().deepCopy());
                }
            }
        }
        Map<String, JsonObject> resultEntries = new LinkedHashMap<>();
        if (child.has(COMPONENTS)) {
            for (Map.Entry<String, JsonObject> entry : entries(childId, child).entrySet()) {
                String type = entry.getKey();
                JsonObject patch = entry.getValue();
                JsonObject merged = patch.deepCopy();
                if (inherited.containsKey(type)) {
                    for (Map.Entry<String, JsonElement> field : inherited.get(type).entrySet()) {
                        // Nested JSON is atomic: an explicit damage object replaces
                        // the prior damage object, including its entire types map.
                        if (!merged.has(field.getKey())) merged.add(field.getKey(), field.getValue().deepCopy());
                    }
                }
                resultEntries.put(type, merged);
            }
        }
        if (!present) return null;
        inherited.forEach(resultEntries::putIfAbsent);
        JsonArray result = new JsonArray();
        resultEntries.values().forEach(component -> result.add(component.deepCopy()));
        return result;
    }

    private Map<String, JsonObject> entries(ResourceLocation id, JsonObject source) {
        JsonElement value = source.get(COMPONENTS);
        if (value == null || !value.isJsonArray()) {
            throw invalid(id, null, "$.components must be an array (not null)");
        }
        Map<String, JsonObject> result = new LinkedHashMap<>();
        JsonArray array = value.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.components[" + i + "]";
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) throw invalid(id, null, path + " must be an object");
            JsonObject component = element.getAsJsonObject();
            JsonElement discriminator = component.get("type");
            if (discriminator == null || !discriminator.isJsonPrimitive()
                    || !discriminator.getAsJsonPrimitive().isString()) {
                throw invalid(id, null, path + ".type must be a string");
            }
            String type = discriminator.getAsString();
            if (!CharacterComponentRegistry.registered(type)) {
                throw invalid(id, type, path + ".type is not registered");
            }
            if (result.containsKey(type)) throw invalid(id, type, path + ".type is duplicated in one array");
            try {
                CharacterSchemaAudit.auditComponentFragment(component, path);
            } catch (IllegalArgumentException exception) {
                throw new PrototypeResolutionException("Character '" + id + "' component type '" + type
                        + "': " + exception.getMessage(), exception);
            }
            result.put(type, component);
        }
        return result;
    }

    private PrototypeResolutionException invalid(ResourceLocation id, String type, String detail) {
        return new PrototypeResolutionException("Character '" + id + "' component"
                + (type == null ? "" : " type '" + type + "'") + ": " + detail);
    }
}
