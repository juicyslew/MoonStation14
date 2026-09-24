package com.juicyslew.moonstation14.ms14.alert.prototype;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

/** Validates AdjustAlert references against the same detached candidate as reagents and statuses. */
public final class AlertReferenceValidator {
    private AlertReferenceValidator() {
    }

    /** Validates every AdjustAlert in resolved metabolism and reactive effect arrays. */
    public static void validate(
            Map<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> encodedCatalogs) {
        Objects.requireNonNull(encodedCatalogs, "encoded catalogs");
        Map<ResourceLocation, JsonObject> reagents = encodedCatalogs.get(ModReagents.REAGENT_TYPE.typeId());
        Map<ResourceLocation, JsonObject> alerts = encodedCatalogs.get(ModAlerts.ALERT_TYPE.typeId());
        if (reagents == null || alerts == null) {
            throw new IllegalArgumentException("Alert reference validation requires reagent and alert catalogs");
        }

        for (Map.Entry<ResourceLocation, JsonObject> reagent : reagents.entrySet()) {
            validateReagent(reagent.getKey(), reagent.getValue(), alerts);
        }
    }

    private static void validateReagent(ResourceLocation source, JsonObject reagent,
                                        Map<ResourceLocation, JsonObject> alerts) {
        JsonElement metabolisms = reagent.get("metabolisms");
        if (metabolisms != null && metabolisms.isJsonObject()) {
            for (Map.Entry<String, JsonElement> stage : metabolisms.getAsJsonObject().entrySet()) {
                JsonObject stageObject = stage.getValue().getAsJsonObject();
                validateEffects(source, stageObject.get("effects"),
                        "metabolisms." + stage.getKey() + ".effects", alerts);
            }
        }

        JsonElement reactiveEffects = reagent.get("reactiveeffects");
        if (reactiveEffects != null && reactiveEffects.isJsonObject()) {
            for (Map.Entry<String, JsonElement> reaction : reactiveEffects.getAsJsonObject().entrySet()) {
                JsonObject reactionObject = reaction.getValue().getAsJsonObject();
                validateEffects(source, reactionObject.get("effects"),
                        "reactiveeffects." + reaction.getKey() + ".effects", alerts);
            }
        }
    }

    private static void validateEffects(ResourceLocation source, JsonElement value, String path,
                                         Map<ResourceLocation, JsonObject> alerts) {
        if (value == null) {
            return;
        }
        if (!value.isJsonArray()) {
            throw failure(source, path, "expected an effect array");
        }
        JsonArray effects = value.getAsJsonArray();
        for (int index = 0; index < effects.size(); index++) {
            String effectPath = path + "[" + index + "]";
            JsonElement effectValue = effects.get(index);
            if (!effectValue.isJsonObject()) {
                throw failure(source, effectPath, "expected an effect object");
            }
            JsonObject effect = effectValue.getAsJsonObject();
            if (!"AdjustAlert".equals(stringValue(effect.get("type")))) {
                continue;
            }
            String referencePath = effectPath + ".alerttype";
            JsonElement reference = effect.get("alerttype");
            if (reference == null || !reference.isJsonPrimitive()
                    || !reference.getAsJsonPrimitive().isString()) {
                throw failure(source, referencePath, "expected an alert reference string");
            }
            validateReference(source, referencePath, reference.getAsString(), alerts);
        }
    }

    private static void validateReference(ResourceLocation source, String path, String value,
                                           Map<ResourceLocation, JsonObject> alerts) {
        ResourceLocation parsed;
        try {
            parsed = ModAlerts.locationFromReference(value);
        } catch (RuntimeException exception) {
            throw failure(source, path, exception.getMessage() == null
                    ? "malformed alert reference '" + value + "'" : exception.getMessage());
        }
        if (!alerts.containsKey(parsed)) {
            throw failure(source, path, "unresolved alert reference '" + value + "'");
        }
    }

    private static String stringValue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static IllegalArgumentException failure(ResourceLocation source, String path, String message) {
        return new IllegalArgumentException("Reagent " + source + " at " + path + ": " + message);
    }
}
