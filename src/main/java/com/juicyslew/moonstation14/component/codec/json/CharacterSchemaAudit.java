package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Strict structural and semantic validation for character target capabilities. */
public final class CharacterSchemaAudit {
    private static final Set<String> ROOT_FIELDS = Set.of("slip_data");
    private static final Set<String> SLIP_FIELDS = Set.of("can_receive_stun", "no_slip",
            "standing_eligible", "prone_eligible", "reactive_groups", "reactive_methods");

    private CharacterSchemaAudit() {
    }

    public static void audit(JsonObject character) {
        if (character == null) fail("$", "expected object");
        checkFields(character, ROOT_FIELDS, "$");
        if (!character.has("slip_data")) fail("$.slip_data", "required field is missing");
        if (!character.get("slip_data").isJsonObject()) fail("$.slip_data", "expected object");
        auditSlipData(character.getAsJsonObject("slip_data"));
    }

    public static void audit(ResourceLocation id, JsonObject character) {
        try {
            audit(character);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Character " + id + ": " + exception.getMessage(), exception);
        }
    }

    public static void auditSlipData(JsonObject slip) {
        checkFields(slip, SLIP_FIELDS, "$.slip_data");
        for (String field : Set.of("can_receive_stun", "no_slip", "standing_eligible", "prone_eligible")) {
            JsonElement value = slip.get(field);
            if (value == null) fail("$.slip_data." + field, "required field is missing");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                fail("$.slip_data." + field, "expected boolean");
            }
        }
        auditStringArray(slip, "reactive_groups", Set.of("flammable", "extinguish", "acidic"));
        auditStringArray(slip, "reactive_methods", Set.of("touch"));
        boolean groups = !slip.getAsJsonArray("reactive_groups").isEmpty();
        boolean methods = !slip.getAsJsonArray("reactive_methods").isEmpty();
        if (groups != methods) fail("$.slip_data", "reactive groups and methods must both be empty or populated");
    }

    private static void auditStringArray(JsonObject object, String field, Set<String> allowed) {
        JsonElement value = object.get(field);
        String path = "$.slip_data." + field;
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonArray()) fail(path, "expected array");
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < value.getAsJsonArray().size(); i++) {
            JsonElement element = value.getAsJsonArray().get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                fail(path + "[" + i + "]", "expected string");
            }
            String serialized = element.getAsString();
            if (!allowed.contains(serialized)) fail(path + "[" + i + "]", "unknown or non-canonical value");
            if (!seen.add(serialized)) fail(path + "[" + i + "]", "duplicate value");
        }
    }

    private static void checkFields(JsonObject object, Set<String> allowed, String path) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) fail(path + "." + field, "unknown field");
        }
    }

    private static void fail(String path, String message) {
        throw new IllegalArgumentException("Character schema at " + path + ": " + message);
    }
}
