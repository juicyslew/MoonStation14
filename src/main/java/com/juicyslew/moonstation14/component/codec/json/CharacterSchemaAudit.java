package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Strict structural and semantic validation for character target capabilities. */
public final class CharacterSchemaAudit {
    private static final Set<String> ROOT_FIELDS = Set.of("slip_data", "movement", "host_entity_types", "thermal", "hands", "components");
    private static final Set<String> MOVEMENT_FIELDS = Set.of("mode", "acceleration", "walk_speed", "sprint_speed",
            "ground_friction_with_input", "ground_friction_without_input", "minimum_friction_speed");
    private static final Set<String> SLIP_FIELDS = Set.of("can_receive_stun", "no_slip",
            "standing_eligible", "prone_eligible", "reactive_groups", "reactive_methods");
    private static final Set<String> THERMAL_FIELDS = Set.of("mass_kg", "specific_heat_joules_per_kg_kelvin",
            "atmosphere_transfer_efficiency", "heat_damage_threshold_kelvin", "cold_damage_threshold_kelvin",
            "current_kelvin", "heat_damage_per_second", "cold_damage_per_second", "damage_cap");

    private CharacterSchemaAudit() {
    }

    public static void audit(JsonObject character) {
        if (character == null) fail("$", "expected object");
        checkFields(character, ROOT_FIELDS, "$");
        if (!character.has("slip_data")) fail("$.slip_data", "required field is missing");
        if (!character.get("slip_data").isJsonObject()) fail("$.slip_data", "expected object");
        auditSlipData(character.getAsJsonObject("slip_data"));
        if (character.has("movement")) {
            if (!character.get("movement").isJsonObject()) fail("$.movement", "expected object");
            auditMovement(character.getAsJsonObject("movement"));
        }
        if (character.has("thermal")) {
            if (!character.get("thermal").isJsonObject()) fail("$.thermal", "expected object");
            auditThermal(character.getAsJsonObject("thermal"));
        }
        if (character.has("hands")) auditHands(character.get("hands"));
        if (character.has("components")) {
            JsonElement components = character.get("components");
            if (!components.isJsonArray()) fail("$.components", "expected array");
            Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < components.getAsJsonArray().size(); i++) {
                JsonElement component = components.getAsJsonArray().get(i);
                String path = "$.components[" + i + "]";
                if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isString())
                    fail(path, "expected string");
                String name = component.getAsString();
                if (!"complex_interaction".equals(name)) fail(path, "unknown component");
                if (!seen.add(name)) fail(path, "duplicate component");
            }
        }
        if (character.has("host_entity_types")) {
            JsonElement hosts = character.get("host_entity_types");
            if (!hosts.isJsonArray()) fail("$.host_entity_types", "expected array");
            if (hosts.getAsJsonArray().size() > 16) fail("$.host_entity_types", "at most 16 host entity types are allowed");
            Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < hosts.getAsJsonArray().size(); i++) {
                JsonElement host = hosts.getAsJsonArray().get(i);
                String path = "$.host_entity_types[" + i + "]";
                if (!host.isJsonPrimitive() || !host.getAsJsonPrimitive().isString()) fail(path, "expected string");
                String value = host.getAsString();
                ResourceLocation parsed = ResourceLocation.tryParse(value);
                if (parsed == null || !value.equals(parsed.toString()) || !value.contains(":")) {
                    fail(path, "expected canonical namespaced resource location");
                }
                if (!seen.add(value)) fail(path, "duplicate host entity type");
            }
            if (!hosts.getAsJsonArray().isEmpty() && !character.has("movement")) {
                fail("$.movement", "required when host_entity_types is nonempty");
            }
        }
    }

    private static void auditHands(JsonElement hands) {
        if (!hands.isJsonArray()) fail("$.hands", "expected array");
        if (hands.getAsJsonArray().size() > HandState.MAX_HANDS) {
            fail("$.hands", "at most " + HandState.MAX_HANDS + " hands are allowed");
        }
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < hands.getAsJsonArray().size(); i++) {
            JsonElement hand = hands.getAsJsonArray().get(i);
            String path = "$.hands[" + i + "]";
            if (!hand.isJsonPrimitive() || !hand.getAsJsonPrimitive().isString()) fail(path, "expected string");
            String id = hand.getAsString();
            if (id.isBlank()) fail(path, "hand ID must not be blank");
            if (id.length() > HandState.MAX_ID_LENGTH) {
                fail(path, "hand ID must be at most " + HandState.MAX_ID_LENGTH + " characters");
            }
            if (!seen.add(id)) fail(path, "duplicate hand ID");
        }
        // An explicitly empty list is equivalent to omitting the capability entirely.
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

    public static void auditMovement(JsonObject movement) {
        checkFields(movement, MOVEMENT_FIELDS, "$.movement");
        JsonElement mode = movement.get("mode");
        if (mode == null) fail("$.movement.mode", "required field is missing");
        if (!mode.isJsonPrimitive() || !mode.getAsJsonPrimitive().isString() || !"grounded".equals(mode.getAsString())) {
            fail("$.movement.mode", "only canonical mode 'grounded' is supported");
        }
        for (String field : MOVEMENT_FIELDS) {
            if ("mode".equals(field)) continue;
            JsonElement value = movement.get(field);
            if (value == null) fail("$.movement." + field, "required field is missing");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) fail("$.movement." + field, "expected number");
            double number;
            try { number = value.getAsDouble(); } catch (NumberFormatException exception) {
                fail("$.movement." + field, "expected finite number"); return;
            }
            if (!Double.isFinite(number) || number < 0 || number > 100) {
                fail("$.movement." + field, "expected finite number in [0, 100]");
            }
        }
    }

    public static void auditThermal(JsonObject thermal) {
        checkFields(thermal, THERMAL_FIELDS, "$.thermal");
        auditThermalNumber(thermal, "mass_kg", 0.1, 500.0);
        auditThermalNumber(thermal, "specific_heat_joules_per_kg_kelvin", 1.0, 10000.0);
        auditThermalNumber(thermal, "atmosphere_transfer_efficiency", 0.0, 1.0);
        auditThermalNumber(thermal, "heat_damage_threshold_kelvin", 150.0, 500.0);
        auditThermalNumber(thermal, "cold_damage_threshold_kelvin", 150.0, 500.0);
        auditThermalNumber(thermal, "current_kelvin", 150.0, 500.0);
        auditThermalNumber(thermal, "heat_damage_per_second", 0.0, 100.0);
        auditThermalNumber(thermal, "cold_damage_per_second", 0.0, 100.0);
        auditThermalNumber(thermal, "damage_cap", 0.0, 100.0);
        double heatThreshold = thermal.get("heat_damage_threshold_kelvin").getAsDouble();
        double coldThreshold = thermal.get("cold_damage_threshold_kelvin").getAsDouble();
        double current = thermal.get("current_kelvin").getAsDouble();
        if (heatThreshold <= coldThreshold) fail("$.thermal", "heat threshold must be greater than cold threshold");
        if (current <= coldThreshold || current >= heatThreshold) {
            fail("$.thermal.current_kelvin", "must be between cold and heat thresholds");
        }
    }

    private static void auditThermalNumber(JsonObject thermal, String field, double minimum, double maximum) {
        JsonElement value = thermal.get(field);
        String path = "$.thermal." + field;
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) fail(path, "expected number");
        double number;
        try { number = value.getAsDouble(); } catch (NumberFormatException exception) {
            fail(path, "expected finite number"); return;
        }
        if (!Double.isFinite(number) || number <= minimum || number > maximum) {
            fail(path, "expected finite positive number in (" + minimum + ", " + maximum + "]");
        }
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
