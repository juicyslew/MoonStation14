package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;

import java.util.Set;

/** Strict structural and semantic validation for character target capabilities. */
public final class CharacterSchemaAudit {
    private static final Set<String> ROOT_FIELDS = Set.of("slip_data", "movement", "host_entity_types", "thermal",
            "hands", "blood", "lungs", "components", "metabolizer_types");
    private static final Set<String> MOVEMENT_FIELDS = Set.of("mode", "acceleration", "walk_speed", "sprint_speed",
            "ground_friction_with_input", "ground_friction_without_input", "minimum_friction_speed");
    private static final Set<String> SLIP_FIELDS = Set.of("can_receive_stun", "no_slip",
            "standing_eligible", "prone_eligible", "reactive_groups", "reactive_methods");
    private static final Set<String> THERMAL_FIELDS = Set.of("mass_kg", "specific_heat_joules_per_kg_kelvin",
            "atmosphere_transfer_efficiency", "heat_damage_threshold_kelvin", "cold_damage_threshold_kelvin",
             "current_kelvin", "heat_damage_per_second", "cold_damage_per_second", "damage_cap",
             "normal_body_temperature_kelvin", "metabolism_heat_joules_per_second",
             "radiated_heat_joules_per_second", "implicit_heat_regulation_joules_per_second",
             "sweat_heat_regulation_joules_per_second", "shivering_heat_regulation_joules_per_second",
              "thermal_regulation_threshold_kelvin", "space_heat_capacity_joules_per_kelvin",
              "space_heat_scale", "space_temperature_kelvin");
    private static final Set<String> BLOOD_FIELDS = Set.of("reference_solution", "max_volume_modifier", "update_interval_seconds",
        "bleed_decay_per_update", "max_bleed_rate", "damage_bleed_multipliers", "blood_refresh_per_update",
        "bloodloss_threshold_fraction", "bloodloss_damage_per_update", "bloodloss_heal_per_update",
              "bloodloss_ignore_resistances", "metabolism_exclusions",
              "bleed_puddle_threshold");
    private static final Set<String> LUNGS_FIELDS = Set.of("breath_interval_seconds", "breath_volume_liters",
             "max_lung_moles", "breath_moles_to_saturation_multiplier",
             "max_saturation", "initial_saturation", "min_saturation", "saturation_loss_per_update",
            "suffocation_threshold", "suffocation_damage_per_update", "suffocation_recovery_per_update",
             "suffocation_ignore_resistances", "toxic_gas_damage_per_mole", "toxic_gas_damage_cap_per_inhale");
    private static final Set<String> BAROTRAUMA_FIELDS = Set.of("type", "damage", "maxDamage");

    private CharacterSchemaAudit() {
    }

    public static void audit(JsonObject character) {
        if (character == null) fail("$", "expected object");
        checkFields(character, ROOT_FIELDS, "$");
        auditMetabolizerTypes(character);
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
        auditBloodIfPresent(character);
        auditLungsIfPresent(character);
        auditComponentsIfPresent(character);
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
        }
    }

    public static void auditMetabolizerTypes(JsonObject character) {
        if (!character.has("metabolizer_types")) return;
        String path = "$.metabolizer_types";
        JsonElement values = character.get("metabolizer_types");
        if (!values.isJsonArray()) fail(path, "expected array");
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < values.getAsJsonArray().size(); i++) {
            JsonElement value = values.getAsJsonArray().get(i);
            String itemPath = path + "[" + i + "]";
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) fail(itemPath, "expected string");
            String id = value.getAsString();
            if (MetabolizerTypeEnum.getEnum(id) == null) fail(itemPath, "unknown or non-canonical metabolizer type");
            if (!seen.add(id)) fail(itemPath, "duplicate metabolizer type");
        }
    }

    /** Validate a present blood block before JsonOps can convert nested JSON nulls. */
    public static void auditBloodIfPresent(JsonObject character) {
        if (character.has("blood")) {
            if (!character.get("blood").isJsonObject()) fail("$.blood", "expected object");
            auditBlood(character.getAsJsonObject("blood"));
        }
    }

    public static void auditBlood(JsonObject blood) {
        checkFields(blood, BLOOD_FIELDS, "$.blood");
        auditBloodNumber(blood, "max_volume_modifier", 1.0, 1_000_000.0, false);
        auditReferenceSolution(blood);
        auditBloodExclusions(blood);
        auditBloodNumber(blood, "update_interval_seconds", 0.0, 1_000_000.0, true);
        auditBloodNumber(blood, "bleed_decay_per_update", 0.0, 1_000_000.0, false);
        auditBloodNumber(blood, "max_bleed_rate", 0.0, 1_000_000.0, false);
        auditBloodMap(blood, "damage_bleed_multipliers", true);
        auditBloodNumber(blood, "blood_refresh_per_update", 0.0, 1_000_000.0, false);
        auditBloodNumber(blood, "bloodloss_threshold_fraction", 0.0, 1.0, false);
        auditBloodMap(blood, "bloodloss_damage_per_update", false);
        auditBloodMap(blood, "bloodloss_heal_per_update", false);
        auditCentVolume(blood, "bleed_puddle_threshold", true);
        JsonElement ignoreResistances = blood.get("bloodloss_ignore_resistances");
        if (ignoreResistances == null) fail("$.blood.bloodloss_ignore_resistances", "required field is missing");
        if (!ignoreResistances.isJsonPrimitive() || !ignoreResistances.getAsJsonPrimitive().isBoolean()) {
            fail("$.blood.bloodloss_ignore_resistances", "expected boolean");
        }
        if (blood.has("update_interval_seconds") && finiteNumber(blood.get("update_interval_seconds"))) {
            double ticks = blood.get("update_interval_seconds").getAsDouble() * 20.0;
            if (!Double.isFinite(ticks) || Math.round(ticks) < 1 || Math.round(ticks) > Integer.MAX_VALUE) {
                fail("$.blood.update_interval_seconds", "must round to a server interval in [1, 2147483647] ticks");
            }
        }
    }

    static void auditHands(JsonElement hands) {
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

    private static void auditCentVolume(JsonObject blood, String field, boolean strictlyPositive) {
        String path = "$.blood." + field;
        JsonElement value = blood.get(field);
        if (value == null) fail(path, "required field is missing");
        if (!finiteNumber(value)) fail(path, "expected finite number");
        double number = value.getAsDouble();
        if ((strictlyPositive ? number <= 0.0 : number < 0.0) || number > 1_000_000.0
                || Math.abs(number * 100.0 - Math.rint(number * 100.0)) > 1e-8) {
            fail(path, strictlyPositive
                    ? "expected positive volume, at most 1000000, representable to 0.01"
                    : "expected nonnegative volume, at most 1000000, representable to 0.01");
        }
    }

    /** Validate optional lung policy before JsonOps can dereference nested JSON nulls. */
    public static void auditLungsIfPresent(JsonObject character) {
        if (character.has("lungs")) {
            if (!character.get("lungs").isJsonObject()) fail("$.lungs", "expected object");
            auditLungs(character.getAsJsonObject("lungs"));
        }
    }

    public static void auditLungs(JsonObject lungs) {
        checkFields(lungs, LUNGS_FIELDS, "$.lungs");
        auditLungNumber(lungs, "breath_interval_seconds", 0.0, 1_000_000.0, true);
        auditLungNumber(lungs, "breath_volume_liters", 0.0, 1_000_000.0, true);
        auditLungNumber(lungs, "max_lung_moles", 0.0, 1_000_000.0, true);
        auditLungNumber(lungs, "breath_moles_to_saturation_multiplier", 0.0, 1_000_000.0, true);
        auditLungNumber(lungs, "max_saturation", 0.0, 1_000_000.0, true);
        auditLungNumber(lungs, "initial_saturation", 0.0, 1_000_000.0, false);
        auditLungNumber(lungs, "min_saturation", -1_000_000.0, 0.0, false);
        auditLungNumber(lungs, "saturation_loss_per_update", 0.0, 1_000_000.0, false);
        auditLungNumber(lungs, "suffocation_threshold", 0.0, 1_000_000.0, false);
        auditLungNumber(lungs, "suffocation_damage_per_update", 0.0, 1_000_000.0, false);
        auditLungNumber(lungs, "suffocation_recovery_per_update", 0.0, 1_000_000.0, false);
        auditDamageMap(lungs, "toxic_gas_damage_per_mole", "$.lungs", true);
        auditLungNumber(lungs, "toxic_gas_damage_cap_per_inhale", 0.0, 1_000_000.0, false);
        JsonElement ignore = lungs.get("suffocation_ignore_resistances");
        if (ignore == null) fail("$.lungs.suffocation_ignore_resistances", "required field is missing");
        if (!ignore.isJsonPrimitive() || !ignore.getAsJsonPrimitive().isBoolean()) {
            fail("$.lungs.suffocation_ignore_resistances", "expected boolean");
        }
        if (finiteNumber(lungs.get("breath_interval_seconds"))) {
            double ticks = lungs.get("breath_interval_seconds").getAsDouble() * 20.0;
            if (!Double.isFinite(ticks) || Math.round(ticks) < 1 || Math.round(ticks) > Integer.MAX_VALUE) {
                fail("$.lungs.breath_interval_seconds", "must round to a server interval in [1, 2147483647] ticks");
            }
        }
        if (finiteNumber(lungs.get("max_saturation"))) {
            double max = lungs.get("max_saturation").getAsDouble();
            if (finiteNumber(lungs.get("initial_saturation")) && lungs.get("initial_saturation").getAsDouble() > max) {
                fail("$.lungs.initial_saturation", "must be at most max_saturation");
            }
            if (finiteNumber(lungs.get("suffocation_threshold")) && lungs.get("suffocation_threshold").getAsDouble() > max) {
                fail("$.lungs.suffocation_threshold", "must be at most max_saturation");
            }
        }
        if (finiteNumber(lungs.get("min_saturation")) && finiteNumber(lungs.get("initial_saturation"))
                && lungs.get("initial_saturation").getAsDouble() < lungs.get("min_saturation").getAsDouble())
            fail("$.lungs.initial_saturation", "must be at least min_saturation");
    }

    public static void auditComponentsIfPresent(JsonObject character) {
        if (!character.has("components")) return;
        JsonElement elements = character.get("components");
        if (!elements.isJsonArray()) fail("$.components", "expected array");
        if (elements.getAsJsonArray().size() > 16) fail("$.components", "at most 16 components are allowed");
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < elements.getAsJsonArray().size(); i++) {
            String path = "$.components[" + i + "]";
            JsonElement element = elements.getAsJsonArray().get(i);
            if (!element.isJsonObject()) fail(path, "expected object");
            JsonObject object = element.getAsJsonObject();
            auditComponent(object, path);
            if (!seen.add(object.get("type").getAsString())) fail(path + ".type", "duplicate component type");
        }
    }

    public static void auditComponent(JsonObject object, String path) {
        JsonElement discriminator = object.get("type");
        if (discriminator == null || !discriminator.isJsonPrimitive() || !discriminator.getAsJsonPrimitive().isString())
            fail(path + ".type", "expected string discriminator");
        String type = discriminator.getAsString();
        if (!CharacterComponentRegistry.registered(type)) fail(path + ".type", "unknown component type");
        // Only registered Barotrauma exists today; adding another type requires its own audit here.
        checkFields(object, BAROTRAUMA_FIELDS, path);
        JsonElement max = object.get("maxDamage");
        if (!finiteNumber(max) || max.getAsDouble() < 0 || max.getAsDouble() > 1_000_000)
            fail(path + ".maxDamage", "expected finite nonnegative number at most 1000000");
        JsonElement damage = object.get("damage");
        if (damage == null || !damage.isJsonObject()) fail(path + ".damage", "expected object");
        checkFields(damage.getAsJsonObject(), Set.of("types"), path + ".damage");
        auditDamageMap(damage.getAsJsonObject(), "types", path + ".damage", false);
    }

    private static void auditDamageMap(JsonObject object, String field, String parentPath, boolean gasMap) {
        String path = parentPath + "." + field;
        JsonElement value = object.get(field);
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonObject()) fail(path, "expected object");
        JsonObject map = value.getAsJsonObject();
        if (gasMap) {
            for (String gas : map.keySet()) {
                boolean known = false;
                for (GasType type : GasType.values()) if (type.id().equals(gas)) known = true;
                if (!known) fail(path + "." + gas, "unknown or non-canonical gas ID");
                JsonElement nested = map.get(gas);
                if (!nested.isJsonObject()) fail(path + "." + gas, "expected object mapping damage keys to numbers");
                auditDamageEntries(nested.getAsJsonObject(), path + "." + gas);
            }
        } else auditDamageEntries(map, path);
    }

    private static void auditDamageEntries(JsonObject map, String path) {
        for (String key : map.keySet()) {
            if (!DamageKeys.ALL.contains(key)) fail(path + "." + key, "unknown or non-canonical damage key");
            JsonElement amount = map.get(key);
            if (!finiteNumber(amount) || amount.getAsDouble() < 0 || amount.getAsDouble() > 1_000_000.0) {
                fail(path + "." + key, "expected finite nonnegative number at most 1000000");
            }
        }
    }

    private static void auditLungNumber(JsonObject lungs, String field, double min, double max, boolean exclusiveMin) {
        JsonElement value = lungs.get(field);
        String path = "$.lungs." + field;
        if (value == null) fail(path, "required field is missing");
        if (!finiteNumber(value)) fail(path, "expected finite number");
        double number = value.getAsDouble();
        if ((exclusiveMin ? number <= min : number < min) || number > max) {
            fail(path, "expected finite number in " + (exclusiveMin ? "(" : "[") + min + ", " + max + "]");
        }
    }

    private static void auditReferenceSolution(JsonObject blood) {
        String path = "$.blood.reference_solution";
        JsonElement value = blood.get("reference_solution");
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonObject()) fail(path, "expected object mapping canonical reagent prototype IDs to volumes");
        JsonObject solution = value.getAsJsonObject();
        if (solution.size() == 0) fail(path, "must contain at least one reagent");
        for (String id : solution.keySet()) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null || !id.equals(parsed.toString()) || !id.contains(":")) {
                fail(path + "." + id, "expected canonical namespaced reagent prototype ID");
            }
            JsonElement amount = solution.get(id);
            if (!finiteNumber(amount)) fail(path + "." + id, "expected finite number");
            double volume = amount.getAsDouble();
            if (volume <= 0 || volume > 1_000_000 || Math.abs(volume * 100.0 - Math.rint(volume * 100.0)) > 1e-8) {
                fail(path + "." + id, "expected positive volume, at most 1000000, representable to 0.01");
            }
        }
    }

    private static void auditBloodExclusions(JsonObject blood) {
        String path = "$.blood.metabolism_exclusions";
        JsonElement value = blood.get("metabolism_exclusions");
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonArray()) fail(path, "expected array of canonical reagent prototype IDs");
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < value.getAsJsonArray().size(); i++) {
            JsonElement entry = value.getAsJsonArray().get(i);
            String itemPath = path + "[" + i + "]";
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) fail(itemPath, "expected string");
            String id = entry.getAsString();
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null || !id.equals(parsed.toString()) || !id.contains(":")) {
                fail(itemPath, "expected canonical namespaced reagent prototype ID");
            }
            if (!seen.add(id)) fail(itemPath, "duplicate metabolism exclusion");
        }
        JsonElement reference = blood.get("reference_solution");
        if (reference != null && reference.isJsonObject()) {
            for (String id : reference.getAsJsonObject().keySet()) {
                if (!seen.contains(id)) fail(path, "must include every reference_solution reagent (missing " + id + ")");
            }
        }
    }

    private static void auditBloodNumber(JsonObject blood, String field, double min, double max, boolean exclusiveMin) {
        JsonElement value = blood.get(field);
        String path = "$.blood." + field;
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) fail(path, "expected number");
        double number;
        try { number = value.getAsDouble(); } catch (NumberFormatException exception) {
            fail(path, "expected finite number"); return;
        }
        if (!Double.isFinite(number) || (exclusiveMin ? number <= min : number < min) || number > max) {
            fail(path, "expected finite number in " + (exclusiveMin ? "(" : "[") + min + ", " + max + "]");
        }
    }

    private static void auditBloodMap(JsonObject blood, String field, boolean allowSigned) {
        String path = "$.blood." + field;
        JsonElement value = blood.get(field);
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonObject()) fail(path, "expected object mapping canonical damage keys to numbers");
        JsonObject map = value.getAsJsonObject();
        for (String key : map.keySet()) {
            if (!DamageKeys.ALL.contains(key)) fail(path + "." + key, "unknown or non-canonical damage key");
            JsonElement amount = map.get(key);
            if (!amount.isJsonPrimitive() || !amount.getAsJsonPrimitive().isNumber()) fail(path + "." + key, "expected number");
            double number;
            try { number = amount.getAsDouble(); } catch (NumberFormatException exception) {
                fail(path + "." + key, "expected finite number"); return;
            }
            if (!Double.isFinite(number) || (allowSigned ? Math.abs(number) > 1_000_000.0 : number < 0.0 || number > 1_000_000.0)) {
                fail(path + "." + key, allowSigned
                        ? "expected finite number in [-1000000, 1000000]"
                        : "expected finite number in [0, 1000000]");
            }
        }
    }

    private static boolean finiteNumber(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return false;
        try { return Double.isFinite(element.getAsDouble()); } catch (NumberFormatException ignored) { return false; }
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
        auditThermalNumber(thermal, "normal_body_temperature_kelvin", 150.0, 500.0);
        for (String field : Set.of("metabolism_heat_joules_per_second", "radiated_heat_joules_per_second",
                "implicit_heat_regulation_joules_per_second", "sweat_heat_regulation_joules_per_second",
                "shivering_heat_regulation_joules_per_second")) {
            auditThermalNonnegative(thermal, field, 1_000_000.0);
        }
        auditThermalNonnegative(thermal, "thermal_regulation_threshold_kelvin", 350.0);
        auditThermalNumber(thermal, "space_heat_capacity_joules_per_kelvin", 0.0, 1_000_000.0);
        auditThermalNumber(thermal, "space_heat_scale", 0.0, 1_000.0);
        auditThermalNumber(thermal, "space_temperature_kelvin", 0.0, 500.0);
        double heatThreshold = thermal.get("heat_damage_threshold_kelvin").getAsDouble();
        double coldThreshold = thermal.get("cold_damage_threshold_kelvin").getAsDouble();
        double current = thermal.get("current_kelvin").getAsDouble();
        if (heatThreshold <= coldThreshold) fail("$.thermal", "heat threshold must be greater than cold threshold");
        if (current <= coldThreshold || current >= heatThreshold) {
            fail("$.thermal.current_kelvin", "must be between cold and heat thresholds");
        }
        double normal = thermal.get("normal_body_temperature_kelvin").getAsDouble();
        if (normal <= coldThreshold || normal >= heatThreshold) {
            fail("$.thermal.normal_body_temperature_kelvin", "must be between cold and heat thresholds");
        }
    }

    public static void auditThermalIfPresent(JsonObject character) {
        if (character.has("thermal")) {
            if (!character.get("thermal").isJsonObject()) fail("$.thermal", "expected object");
            auditThermal(character.getAsJsonObject("thermal"));
        }
    }

    private static void auditThermalNonnegative(JsonObject thermal, String field, double maximum) {
        JsonElement value = thermal.get(field);
        String path = "$.thermal." + field;
        if (!finiteNumber(value)) fail(path, "required finite number");
        if (value.getAsDouble() < 0 || value.getAsDouble() > maximum) fail(path, "out of range");
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
