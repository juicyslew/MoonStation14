package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Strict structural audit for the canonical alert prototype JSON schema. */
public final class AlertSchemaAudit {
    private static final Set<String> TOP_LEVEL = Set.of(
            "name_translation_key", "description_translation_key", "color", "order", "category");

    private AlertSchemaAudit() {
    }

    public static void audit(JsonObject alert) {
        if (alert == null) {
            fail("$", "expected object");
        }
        for (String field : alert.keySet()) {
            if (!TOP_LEVEL.contains(field)) {
                fail("$.", "unknown field '" + field + "'");
            }
        }

        requireString(alert, "name_translation_key", "$.name_translation_key");
        nonBlank(alert.get("name_translation_key"), "$.name_translation_key");
        requireString(alert, "description_translation_key", "$.description_translation_key");
        nonBlank(alert.get("description_translation_key"), "$.description_translation_key");
        requireColor(alert.get("color"), "$.color");
        requireNonnegativeInteger(alert.get("order"), "$.order");
        if (alert.has("category")) {
            requireCanonicalResourceLocation(alert.get("category"), "$.category");
        }
    }

    public static void audit(ResourceLocation id, JsonObject alert) {
        try {
            audit(alert);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Alert " + id + ": " + exception.getMessage(), exception);
        }
    }

    private static void requireColor(JsonElement value, String path) {
        requireString(value, path);
        if (!value.getAsString().matches("0x[0-9a-fA-F]{1,8}")) {
            fail(path, "must be a hexadecimal string beginning with 0x");
        }
    }

    private static void requireCanonicalResourceLocation(JsonElement value, String path) {
        requireString(value, path);
        String serialized = value.getAsString();
        ResourceLocation parsed;
        try {
            if (!serialized.contains(":")) {
                fail(path, "must use a fully qualified resource location");
            }
            parsed = ResourceLocation.parse(serialized);
        } catch (RuntimeException exception) {
            fail(path, "must be a canonical resource location");
            return;
        }
        if (!serialized.equals(parsed.toString())) {
            fail(path, "must be a canonical resource location");
        }
    }

    private static void requireNonnegativeInteger(JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            fail(path, "expected a nonnegative integer");
        }
        try {
            int parsed = value.getAsInt();
            if (parsed < 0 || !value.getAsString().matches("(?:0|[1-9][0-9]*)")) {
                fail(path, "expected a nonnegative integer");
            }
        } catch (RuntimeException exception) {
            fail(path, "expected a nonnegative integer");
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

    private static void nonBlank(JsonElement value, String path) {
        if (value.getAsString().isBlank()) {
            fail(path, "must not be blank");
        }
    }

    private static void fail(String path, String message) {
        throw new IllegalArgumentException("Alert schema at " + path + ": " + message);
    }
}
