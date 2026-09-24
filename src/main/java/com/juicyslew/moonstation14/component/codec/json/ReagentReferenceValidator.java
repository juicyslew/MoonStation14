package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validates the reagent references which are part of the reagent schema. */
public final class ReagentReferenceValidator {
    private static final String DEFAULT_NAMESPACE = "moonstation14";

    private ReagentReferenceValidator() {
    }

    public static int validate(ResourceLocation source, JsonObject reagent,
                                Set<ResourceLocation> known) {
        return validate(source, reagent, known, Set.of());
    }

    public static int validate(ResourceLocation source, JsonObject reagent,
                                Map<ResourceLocation, JsonObject> raw) {
        Set<ResourceLocation> known = raw.keySet();
        Set<ResourceLocation> abstractIds = new HashSet<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : raw.entrySet()) {
            JsonElement value = entry.getValue().get("abstract");
            if (value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean()) {
                abstractIds.add(entry.getKey());
            }
        }
        return validate(source, reagent, known, abstractIds);
    }

    public static int validate(ResourceLocation source, JsonObject reagent,
                               Set<ResourceLocation> known, Set<ResourceLocation> abstractIds) {
        int count = 0;
        JsonElement metabolisms = reagent.get("metabolisms");
        if (metabolisms != null && metabolisms.isJsonObject()) {
            for (Map.Entry<String, JsonElement> stage : metabolisms.getAsJsonObject().entrySet()) {
                JsonObject stageObject = stage.getValue().getAsJsonObject();
                JsonElement metabolites = stageObject.get("metabolites");
                if (metabolites != null && metabolites.isJsonObject()) {
                    for (String reference : metabolites.getAsJsonObject().keySet()) {
                        check(source, sourcePath("metabolisms", stage.getKey(), "metabolites", reference),
                                reference, known, abstractIds);
                        count++;
                    }
                }

                JsonElement effects = stageObject.get("effects");
                if (effects != null && effects.isJsonArray()) {
                    count += effects(source, effects.getAsJsonArray(),
                            "metabolisms." + stage.getKey() + ".effects", known, abstractIds);
                }
            }
        }

        JsonElement reactiveEffects = reagent.get("reactiveeffects");
        if (reactiveEffects != null && reactiveEffects.isJsonObject()) {
            for (Map.Entry<String, JsonElement> reaction : reactiveEffects.getAsJsonObject().entrySet()) {
                JsonObject reactionObject = reaction.getValue().getAsJsonObject();
                JsonElement effects = reactionObject.get("effects");
                if (effects != null && effects.isJsonArray()) {
                    count += effects(source, effects.getAsJsonArray(),
                            "reactiveeffects." + reaction.getKey() + ".effects", known, abstractIds);
                }
            }
        }
        return count;
    }

    private static int effects(ResourceLocation source, JsonArray effects, String path,
                               Set<ResourceLocation> known, Set<ResourceLocation> abstractIds) {
        int count = 0;
        for (int i = 0; i < effects.size(); i++) {
            JsonObject effect = effects.get(i).getAsJsonObject();
            String effectPath = path + "[" + i + "]";
            String type = effect.get("type").getAsString();
            if ("AdjustReagent".equals(type) && effect.has("reagent")) {
                check(source, effectPath + ".reagent", effect.get("reagent"), known, abstractIds);
                count++;
            } else if ("CleanBloodstream".equals(type) && effect.has("excluded")) {
                check(source, effectPath + ".excluded", effect.get("excluded"), known, abstractIds);
                count++;
            }

            JsonElement conditions = effect.get("conditions");
            if (conditions != null && conditions.isJsonArray()) {
                JsonArray conditionArray = conditions.getAsJsonArray();
                for (int j = 0; j < conditionArray.size(); j++) {
                    JsonObject condition = conditionArray.get(j).getAsJsonObject();
                    if ("ReagentCondition".equals(condition.get("type").getAsString())
                            && condition.has("reagent")) {
                        check(source, effectPath + ".conditions[" + j + "].reagent",
                                condition.get("reagent"), known, abstractIds);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void check(ResourceLocation source, String path, String value,
                              Set<ResourceLocation> known, Set<ResourceLocation> abstractIds) {
        ResourceLocation reference;
        try {
            reference = parse(value);
        } catch (RuntimeException exception) {
            fail(source, path, "malformed reagent reference '" + value + "'");
            return;
        }
        String expected = value.indexOf(':') < 0
                ? DEFAULT_NAMESPACE + ":" + value : value;
        boolean exactTarget = known.stream().anyMatch(id -> id.toString().equals(expected));
        if (!exactTarget || !known.contains(reference)) {
            fail(source, path, "unresolved reagent reference '" + value + "'");
        }
        if (abstractIds.contains(reference)) {
            fail(source, path, "reagent reference targets abstract reagent '" + value + "'");
        }
    }

    private static void check(ResourceLocation source, String path, JsonElement value,
                              Set<ResourceLocation> known, Set<ResourceLocation> abstractIds) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            fail(source, path, "expected reagent reference string");
        }
        check(source, path, value.getAsString(), known, abstractIds);
    }

    private static ResourceLocation parse(String value) {
        return value.contains(":")
                ? ResourceLocation.parse(value)
                : ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, value);
    }

    private static String sourcePath(String... parts) {
        return String.join(".", parts);
    }

    private static void fail(ResourceLocation source, String path, String message) {
        throw new IllegalArgumentException("Reagent " + source + " at " + path + ": " + message);
    }
}
