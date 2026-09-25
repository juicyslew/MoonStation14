package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/** Strict structural audit for the canonical status-effect JSON schema. */
public final class StatusEffectSchemaAudit {
    private static final Set<String> TOP_LEVEL = Set.of(
            "translation_key", "beneficial", "color", "behaviors", "eligibility",
            "movement_speed_multiplier");

    private StatusEffectSchemaAudit() {
    }

    public static void audit(JsonObject statusEffect) {
        if (statusEffect == null) {
            fail("$", "expected object");
        }
        for (String field : statusEffect.keySet()) {
            if (!TOP_LEVEL.contains(field)) {
                fail("$.", "unknown field '" + field + "'");
            }
        }
        requireString(statusEffect, "translation_key", "$.translation_key");
        if (statusEffect.get("translation_key").getAsString().isEmpty()) {
            fail("$.translation_key", "must not be empty");
        }
        if (statusEffect.has("beneficial")) {
            requireBoolean(statusEffect, "beneficial", "$.beneficial");
        }
        requireColor(statusEffect.get("color"), "$.color");
        auditEnumList(statusEffect.get("behaviors"), "$.behaviors", true);
        auditEnumList(statusEffect.get("eligibility"), "$.eligibility", false);
        boolean movementBehavior = statusEffect.getAsJsonArray("behaviors")
                .contains(new com.google.gson.JsonPrimitive("movement_speed"));
        if (movementBehavior != statusEffect.has("movement_speed_multiplier")) {
            fail("$.movement_speed_multiplier", movementBehavior
                    ? "required when behaviors contains movement_speed"
                    : "forbidden unless behaviors contains movement_speed");
        }
        if (statusEffect.has("movement_speed_multiplier")) {
            JsonElement multiplier = statusEffect.get("movement_speed_multiplier");
            if (multiplier == null || !multiplier.isJsonPrimitive()
                    || !multiplier.getAsJsonPrimitive().isNumber()) {
                fail("$.movement_speed_multiplier", "expected a finite nonnegative number");
            }
            double value = multiplier.getAsDouble();
            if (!Double.isFinite(value) || value < 0d) {
                fail("$.movement_speed_multiplier", "expected a finite nonnegative number");
            }
        }
    }

    public static void audit(ResourceLocation id, JsonObject statusEffect) {
        try {
            audit(statusEffect);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Status effect " + id + ": " + exception.getMessage(), exception);
        }
    }

    private static void auditEnumList(JsonElement value, String path, boolean behaviors) {
        if (value == null || !value.isJsonArray()) {
            fail(path, "expected an array of strings");
        }
        JsonArray array = value.getAsJsonArray();
        if (!behaviors && array.isEmpty()) {
            fail(path, "must not be empty");
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String itemPath = path + "[" + index + "]";
            JsonElement item = array.get(index);
            requireString(item, itemPath);
            String name = item.getAsString();
            if (!seen.add(name)) {
                fail(itemPath, "duplicate value '" + name + "'");
            }
            if (behaviors) {
                boolean known = java.util.Arrays.stream(StatusEffectBehavior.values())
                        .anyMatch(behavior -> behavior.serializedName().equals(name));
                if (!known) {
                    fail(itemPath, "unknown status effect behavior '" + name + "'");
                }
            } else if (!name.equals("living_entity")) {
                fail(itemPath, "unknown status effect eligibility '" + name + "'");
            }
        }
    }

    private static void requireColor(JsonElement value, String path) {
        requireString(value, path);
        if (!value.getAsString().matches("0x[0-9a-fA-F]{1,8}")) {
            fail(path, "must be a hexadecimal string beginning with 0x");
        }
    }

    private static void requireString(JsonObject object, String field, String path) {
        if (!object.has(field)) {
            fail(path, "required field is missing");
        }
        requireString(object.get(field), path);
    }

    private static void requireString(JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            fail(path, "expected a string");
        }
    }

    private static void requireBoolean(JsonObject object, String field, String path) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            fail(path, "expected a boolean");
        }
    }

    private static void fail(String path, String message) {
        throw new IllegalArgumentException("Status effect schema at " + path + ": " + message);
    }
}
