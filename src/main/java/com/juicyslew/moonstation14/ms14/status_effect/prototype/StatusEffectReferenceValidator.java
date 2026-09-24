package com.juicyslew.moonstation14.ms14.status_effect.prototype;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

/** Validates status references against the same detached candidate as reagents. */
public final class StatusEffectReferenceValidator {
    private static final String DEFAULT_NAMESPACE = "moonstation14";

    private StatusEffectReferenceValidator() {
    }

    /**
 * Validates every status-bearing effect in resolved metabolism and reactive
 * effect arrays. GenericStatusEffect is intentionally not inspected here.
     */
    public static void validate(
            Map<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> encodedCatalogs) {
        Objects.requireNonNull(encodedCatalogs, "encoded catalogs");
        Map<ResourceLocation, JsonObject> reagents = encodedCatalogs.get(ModReagents.REAGENT_TYPE.typeId());
        Map<ResourceLocation, JsonObject> statuses = encodedCatalogs.get(ModStatusEffects.STATUS_EFFECT_TYPE.typeId());
        if (reagents == null || statuses == null) {
            throw new IllegalArgumentException("Status-effect reference validation requires reagent and status-effect catalogs");
        }

        for (Map.Entry<ResourceLocation, JsonObject> reagent : reagents.entrySet()) {
            validateReagent(reagent.getKey(), reagent.getValue(), statuses);
        }
    }

    private static void validateReagent(ResourceLocation source, JsonObject reagent,
                                        Map<ResourceLocation, JsonObject> statuses) {
        JsonElement metabolisms = reagent.get("metabolisms");
        if (metabolisms != null && metabolisms.isJsonObject()) {
            for (Map.Entry<String, JsonElement> stage : metabolisms.getAsJsonObject().entrySet()) {
                JsonObject stageObject = stage.getValue().getAsJsonObject();
                validateEffects(source, stageObject.get("effects"),
                        "metabolisms." + stage.getKey() + ".effects", statuses);
            }
        }

        JsonElement reactiveEffects = reagent.get("reactiveeffects");
        if (reactiveEffects != null && reactiveEffects.isJsonObject()) {
            for (Map.Entry<String, JsonElement> reaction : reactiveEffects.getAsJsonObject().entrySet()) {
                JsonObject reactionObject = reaction.getValue().getAsJsonObject();
                validateEffects(source, reactionObject.get("effects"),
                        "reactiveeffects." + reaction.getKey() + ".effects", statuses);
            }
        }
    }

    private static void validateEffects(ResourceLocation source, JsonElement value, String path,
                                        Map<ResourceLocation, JsonObject> statuses) {
        if (value == null) {
            return;
        }
        if (!value.isJsonArray()) {
            throw failure(source, path, "expected an effect array");
        }
        JsonArray effects = value.getAsJsonArray();
        for (int index = 0; index < effects.size(); index++) {
            JsonElement effectValue = effects.get(index);
            if (!effectValue.isJsonObject()) {
                throw failure(source, path + "[" + index + "]", "expected an effect object");
            }
            JsonObject effect = effectValue.getAsJsonObject();
            String type = stringValue(effect.get("type"));
            if ("ModifyKnockdown".equals(type)) {
                validateReference(source, path + "[" + index + "].status_effect", "knockdown", statuses, false);
                continue;
            }
            if ("Electrocute".equals(type)) {
                validateReference(source, path + "[" + index + "].status_effect",
                        "statuseffectstunned", statuses, false);
                continue;
            }
            if (!"ModifyStatusEffect".equals(type)
                    && !"MovementSpeedModifier".equals(type)) {
                continue;
            }
            String effectPath = path + "[" + index + "].effectproto";
            JsonElement referenceValue = effect.get("effectproto");
            if ("MovementSpeedModifier".equals(type) && referenceValue == null) {
                // The upstream DTO's omitted default is the legacy spelling;
                // runtime resolution deliberately canonicalizes it to the new
                // lowercase prototype without changing the serialized DTO.
                validateReference(source, effectPath, "reagentspeedstatuseffect", statuses, true);
                continue;
            }
            if (referenceValue == null || !referenceValue.isJsonPrimitive()
                    || !referenceValue.getAsJsonPrimitive().isString()) {
                throw failure(source, effectPath, "expected a status effect reference string");
            }
            validateReference(source, effectPath, referenceValue.getAsString(), statuses,
                    "MovementSpeedModifier".equals(type));
        }
    }

    private static void validateReference(ResourceLocation source, String path, String value,
                                           Map<ResourceLocation, JsonObject> statuses,
                                           boolean requireMovementBehavior) {
        ResourceLocation parsed;
        try {
            parsed = value.indexOf(':') < 0
                    ? ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, value)
                    : ResourceLocation.parse(value);
        } catch (RuntimeException exception) {
            throw failure(source, path, "malformed status effect reference '" + value + "'");
        }

        String canonical = parsed.toString();
        String expectedSpelling = value.indexOf(':') < 0 ? parsed.getPath() : canonical;
        if (!value.equals(expectedSpelling) || !statuses.containsKey(parsed)) {
            throw failure(source, path, "unresolved or non-canonical status effect reference '" + value + "'");
        }
        JsonObject status = statuses.get(parsed);
        if (status == null) {
            throw failure(source, path, "status effect reference does not target a concrete status effect '" + value + "'");
        }
        if (requireMovementBehavior) {
            StatusEffectData definition;
            try {
                definition = StatusEffectData.CODEC.parse(JsonOps.INSTANCE, status).getOrThrow();
            } catch (RuntimeException exception) {
                throw failure(source, path, "status effect reference targets an invalid status definition '"
                        + value + "': " + exception.getMessage());
            }
            if (!definition.behaviors().contains(StatusEffectBehavior.MOVEMENT_SPEED)
                    || definition.movementSpeedMultiplier().isEmpty()) {
                throw failure(source, path, "status effect reference must target a movement_speed status with a valid default '"
                        + value + "'");
            }
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
