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
    private static final Set<String> ROOT_FIELDS = Set.of("host_entity_types", "components");
    private static final Set<String> MOVEMENT_FIELDS = Set.of("mode", "acceleration", "walk_speed", "sprint_speed",
            "ground_friction_with_input", "ground_friction_without_input", "minimum_friction_speed");
    private static final java.util.Map<String, Set<String>> THERMAL_COMPONENT_FIELDS = java.util.Map.of(
            "Temperature", Set.of("mass_kg", "specific_heat_joules_per_kg_kelvin", "atmosphere_transfer_efficiency",
                    "current_kelvin", "space_heat_capacity_joules_per_kelvin", "space_heat_scale", "space_temperature_kelvin"),
            "TemperatureDamage", Set.of("heat_damage_threshold_kelvin", "cold_damage_threshold_kelvin",
                    "heat_damage_per_second", "cold_damage_per_second", "damage_cap"),
            "ThermalRegulator", Set.of("normal_body_temperature_kelvin", "metabolism_heat_joules_per_second",
                    "radiated_heat_joules_per_second", "implicit_heat_regulation_joules_per_second",
                    "sweat_heat_regulation_joules_per_second", "shivering_heat_regulation_joules_per_second",
                    "thermal_regulation_threshold_kelvin"));
    private static final Set<String> BLOOD_FIELDS = Set.of("reference_solution", "max_volume_modifier", "update_interval_seconds",
        "bleed_decay_per_update", "max_bleed_rate", "damage_bleed_multipliers", "blood_refresh_per_update",
        "bloodloss_threshold_fraction", "bloodloss_damage_per_update", "bloodloss_heal_per_update",
              "bloodloss_ignore_resistances", "metabolism_exclusions",
              "bleed_puddle_threshold");
    private static final Set<String> RESPIRATOR_FIELDS = Set.of("type", "breath_interval_seconds", "breath_volume_liters",
             "max_saturation", "initial_saturation", "min_saturation", "saturation_loss_per_update",
             "suffocation_threshold", "suffocation_damage_per_update", "suffocation_recovery_per_update",
             "suffocation_ignore_resistances");
    private static final Set<String> BAROTRAUMA_FIELDS = Set.of("type", "damage", "maxDamage");
    private static final Set<String> COMPLEX_INTERACTION_FIELDS = Set.of("type");

    private CharacterSchemaAudit() {
    }

    public static void audit(JsonObject character) {
        if (character == null) fail("$", "expected object");
        rejectLegacySlip(character);
        rejectLegacyBlood(character);
        rejectLegacyLungs(character);
        rejectLegacyThermal(character);
        rejectLegacyHands(character);
        rejectLegacyMovement(character);
        checkFields(character, ROOT_FIELDS, "$");
        rejectLegacyMetabolizerTypes(character);
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

    public static void rejectLegacyMetabolizerTypes(JsonObject character) {
        if (character.has("metabolizer_types")) fail("$.metabolizer_types",
                "legacy top-level metabolizer_types is unsupported; use components:[{type:'Metabolizer',types:[...]}]");
    }

    private static void auditMetabolizerTypes(JsonElement values, String path) {
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

    public static void rejectLegacyBlood(JsonObject character) {
        // Also invoked on raw abstract/parent declarations by the character catalog validator.
        rejectLegacyMetabolizerTypes(character);
        if (character.has("blood")) fail("$.blood", "legacy top-level blood is unsupported; manually move its fields into components:[{type:'Bloodstream', ...}]");
    }

    public static void rejectLegacyLungs(JsonObject character) {
        if (character.has("lungs")) fail("$.lungs", "legacy top-level lungs is unsupported; move mob fields into components:[{type:'Respirator', ...}] and organ fields into the attached organ prototype's Lung");
    }

    public static void rejectLegacyThermal(JsonObject character) {
        if (character.has("thermal")) fail("$.thermal", "legacy top-level thermal is unsupported; move fields into components Temperature, TemperatureDamage, ThermalRegulator");
    }

    public static void rejectLegacySlip(JsonObject character) {
        if (character.has("slip_data")) fail("$.slip_data", "legacy top-level slip_data is unsupported; move standing_eligible/prone_eligible into StandingState, use NoSlip and Stunnable markers, and reactive_groups/reactive_methods in Reactive components");
    }

    public static void rejectLegacyHands(JsonObject character) {
        if (character.has("hands")) fail("$.hands", "legacy top-level hands is unsupported; use components:[{type:'Hands',hands:[...]}]");
    }

    public static void rejectLegacyMovement(JsonObject character) {
        if (character.has("movement")) fail("$.movement", "legacy top-level movement is unsupported; use components:[{type:'MovementSpeedModifier', ...}]");
    }

    public static void auditBlood(JsonObject blood, String path, boolean complete) {
        java.util.Set<String> allowed = new java.util.HashSet<>(BLOOD_FIELDS);
        allowed.add("type");
        checkFields(blood, allowed, path);
        if (complete || blood.has("max_volume_modifier")) auditBloodNumber(blood, "max_volume_modifier", 1.0, 1_000_000.0, false, path);
        if (complete || blood.has("reference_solution")) auditReferenceSolution(blood, path);
        if (complete || blood.has("metabolism_exclusions")) auditBloodExclusions(blood, path, complete);
        if (complete || blood.has("update_interval_seconds")) auditBloodNumber(blood, "update_interval_seconds", 0.0, 1_000_000.0, true, path);
        if (complete || blood.has("bleed_decay_per_update")) auditBloodNumber(blood, "bleed_decay_per_update", 0.0, 1_000_000.0, false, path);
        if (complete || blood.has("max_bleed_rate")) auditBloodNumber(blood, "max_bleed_rate", 0.0, 1_000_000.0, false, path);
        if (complete || blood.has("damage_bleed_multipliers")) auditBloodMap(blood, "damage_bleed_multipliers", true, path);
        if (complete || blood.has("blood_refresh_per_update")) auditBloodNumber(blood, "blood_refresh_per_update", 0.0, 1_000_000.0, false, path);
        if (complete || blood.has("bloodloss_threshold_fraction")) auditBloodNumber(blood, "bloodloss_threshold_fraction", 0.0, 1.0, false, path);
        if (complete || blood.has("bloodloss_damage_per_update")) auditBloodMap(blood, "bloodloss_damage_per_update", false, path);
        if (complete || blood.has("bloodloss_heal_per_update")) auditBloodMap(blood, "bloodloss_heal_per_update", false, path);
        if (complete || blood.has("bleed_puddle_threshold")) auditCentVolume(blood, "bleed_puddle_threshold", true, path);
        JsonElement ignoreResistances = blood.get("bloodloss_ignore_resistances");
        if (complete && ignoreResistances == null) fail(path + ".bloodloss_ignore_resistances", "required field is missing");
        if ((complete || blood.has("bloodloss_ignore_resistances")) &&
                (ignoreResistances == null || !ignoreResistances.isJsonPrimitive() || !ignoreResistances.getAsJsonPrimitive().isBoolean())) {
            fail(path + ".bloodloss_ignore_resistances", "expected boolean");
        }
        if (blood.has("update_interval_seconds") && finiteNumber(blood.get("update_interval_seconds"))) {
            double ticks = blood.get("update_interval_seconds").getAsDouble() * 20.0;
            if (!Double.isFinite(ticks) || Math.round(ticks) < 1 || Math.round(ticks) > Integer.MAX_VALUE) {
                fail(path + ".update_interval_seconds", "must round to a server interval in [1, 2147483647] ticks");
            }
        }
    }

    static void auditHands(JsonElement hands, String path) {
        if (hands == null || !hands.isJsonArray()) fail(path, "expected array");
        if (hands.getAsJsonArray().size() > HandState.MAX_HANDS) {
            fail(path, "at most " + HandState.MAX_HANDS + " hands are allowed");
        }
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < hands.getAsJsonArray().size(); i++) {
            JsonElement hand = hands.getAsJsonArray().get(i);
            String itemPath = path + "[" + i + "]";
            if (!hand.isJsonPrimitive() || !hand.getAsJsonPrimitive().isString()) fail(itemPath, "expected string");
            String id = hand.getAsString();
            if (id.isBlank()) fail(itemPath, "hand ID must not be blank");
            if (id.length() > HandState.MAX_ID_LENGTH) {
                fail(itemPath, "hand ID must be at most " + HandState.MAX_ID_LENGTH + " characters");
            }
            if (!seen.add(id)) fail(itemPath, "duplicate hand ID");
        }
        // An explicitly empty list is equivalent to omitting the capability entirely.
    }

    private static void auditCentVolume(JsonObject blood, String field, boolean strictlyPositive, String parentPath) {
        String path = parentPath + "." + field;
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

    public static void auditRespirator(JsonObject lungs, String path, boolean complete) {
        checkFields(lungs, RESPIRATOR_FIELDS, path);
        for (String field : RESPIRATOR_FIELDS) {
            if (field.equals("type") || field.equals("suffocation_ignore_resistances") || (!complete && !lungs.has(field))) continue;
            double min = field.equals("min_saturation") ? -2.0 : 0.0;
            double max = field.equals("min_saturation") ? 0.0 : 1_000_000.0;
            boolean exclusive = field.equals("breath_interval_seconds") || field.equals("breath_volume_liters") || field.equals("max_saturation");
            auditLungNumber(lungs, field, min, max, exclusive, path);
        }
        JsonElement ignore = lungs.get("suffocation_ignore_resistances");
        if (complete && ignore == null) fail(path + ".suffocation_ignore_resistances", "required field is missing");
        if (complete || lungs.has("suffocation_ignore_resistances")) {
        if (!ignore.isJsonPrimitive() || !ignore.getAsJsonPrimitive().isBoolean()) {
            fail(path + ".suffocation_ignore_resistances", "expected boolean");
        }
        }
        if (finiteNumber(lungs.get("breath_interval_seconds"))) {
            double ticks = lungs.get("breath_interval_seconds").getAsDouble() * 20.0;
            if (!Double.isFinite(ticks) || Math.round(ticks) < 1 || Math.round(ticks) > Integer.MAX_VALUE) {
                fail(path + ".breath_interval_seconds", "must round to a server interval in [1, 2147483647] ticks");
            }
        }
        if (finiteNumber(lungs.get("max_saturation"))) {
            double max = lungs.get("max_saturation").getAsDouble();
            if (finiteNumber(lungs.get("initial_saturation")) && lungs.get("initial_saturation").getAsDouble() > max) {
                fail(path + ".initial_saturation", "must be at most max_saturation");
            }
            if (finiteNumber(lungs.get("suffocation_threshold")) && lungs.get("suffocation_threshold").getAsDouble() > max) {
                fail(path + ".suffocation_threshold", "must be at most max_saturation");
            }
        }
        if (finiteNumber(lungs.get("min_saturation")) && finiteNumber(lungs.get("initial_saturation"))
                && lungs.get("initial_saturation").getAsDouble() < lungs.get("min_saturation").getAsDouble())
            fail(path + ".initial_saturation", "must be at least min_saturation");
    }

    public static void auditComponentsIfPresent(JsonObject character) {
        if (!character.has("components")) return;
        JsonElement elements = character.get("components");
        if (!elements.isJsonArray()) fail("$.components", "expected array");
        if (elements.getAsJsonArray().size() > 32) fail("$.components", "at most 32 components are allowed");
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
        auditTypedComponent(object, path, true);
    }

    /** Validate every field supplied by an inheritance fragment without requiring omitted fields yet. */
    public static void auditComponentFragment(JsonObject object, String path) {
        auditTypedComponent(object, path, false);
    }

    private static void auditTypedComponent(JsonObject object, String path, boolean complete) {
        JsonElement discriminator = object.get("type");
        if (discriminator == null || !discriminator.isJsonPrimitive() || !discriminator.getAsJsonPrimitive().isString())
            fail(path + ".type", "expected string discriminator");
        String type = discriminator.getAsString();
        if (!CharacterComponentRegistry.registered(type)) fail(path + ".type", "unknown component type");
        if (com.juicyslew.moonstation14.ms14.character.components.ComplexInteractionComponent.TYPE.equals(type)) {
            checkFields(object, COMPLEX_INTERACTION_FIELDS, path);
            return;
        }
        if ("Bloodstream".equals(type)) {
            auditBlood(object, path, complete);
            return;
        }
        if ("Respirator".equals(type)) {
            auditRespirator(object, path, complete);
            return;
        }
         if ("Body".equals(type) || "Hunger".equals(type) || "Thirst".equals(type) || "Stomach".equals(type)
                 || "Flammable".equals(type) || "Blindable".equals(type)) {
            checkFields(object, Set.of("type"), path);
            return;
        }
        if ("MovementSpeedModifier".equals(type)) {
            auditMovement(object, path, complete);
            return;
        }
        if ("NoSlip".equals(type) || "Stunnable".equals(type)) {
            checkFields(object, Set.of("type"), path);
            return;
        }
        if ("StandingState".equals(type)) {
            checkFields(object, Set.of("type", "standing_eligible", "prone_eligible"), path);
            for (String field : Set.of("standing_eligible", "prone_eligible")) {
                if (complete || object.has(field)) auditBoolean(object.get(field), path + "." + field);
            }
            return;
        }
        if ("Reactive".equals(type)) {
            checkFields(object, Set.of("type", "reactive_groups", "reactive_methods"), path);
            if (complete || object.has("reactive_groups"))
                auditStringArray(object, "reactive_groups", Set.of("flammable", "extinguish", "acidic"), path);
            if (complete || object.has("reactive_methods"))
                auditStringArray(object, "reactive_methods", Set.of("touch"), path);
            if (object.has("reactive_groups") && object.has("reactive_methods")) {
                boolean groups = !object.getAsJsonArray("reactive_groups").isEmpty();
                boolean methods = !object.getAsJsonArray("reactive_methods").isEmpty();
                if (groups != methods) fail(path, "reactive groups and methods must both be empty or populated");
            }
            return;
        }
        if ("Hands".equals(type)) {
            checkFields(object, Set.of("type", "hands"), path);
            if (complete || object.has("hands")) auditHands(object.get("hands"), path + ".hands");
            return;
        }
        if ("Metabolizer".equals(type)) {
            checkFields(object, Set.of("type", "types"), path);
            if (complete || object.has("types")) {
                JsonElement values = object.get("types");
                if (values == null) fail(path + ".types", "required field is missing");
                auditMetabolizerTypes(values, path + ".types");
            }
            return;
        }
        if ("InitialBody".equals(type)) {
            checkFields(object, Set.of("type", "organs"), path);
            if (complete || object.has("organs")) {
                JsonElement organs = object.get("organs");
                if (organs == null || !organs.isJsonObject()) fail(path + ".organs", "expected organ category map");
                if (organs.getAsJsonObject().size() > 1) fail(path + ".organs", "at most one supported organ category");
                for (String category : organs.getAsJsonObject().keySet()) {
                    if (!"Lungs".equals(category)) fail(path + ".organs." + category, "unknown organ category");
                    JsonElement value = organs.getAsJsonObject().get(category);
                    String fieldPath = path + ".organs." + category;
                    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                        fail(fieldPath, "expected canonical organ prototype ID");
                    String id = value.getAsString();
                    ResourceLocation parsed = ResourceLocation.tryParse(id);
                    if (parsed == null || !id.contains(":") || !id.equals(parsed.toString()))
                        fail(fieldPath, "expected canonical namespaced organ prototype ID");
                }
            }
            return;
        }
        if (THERMAL_COMPONENT_FIELDS.containsKey(type)) {
            auditThermalComponent(object, path, type, complete);
            return;
        }
        checkFields(object, BAROTRAUMA_FIELDS, path);
        JsonElement max = object.get("maxDamage");
        if ((complete || object.has("maxDamage"))
                && (!finiteNumber(max) || max.getAsDouble() < 0 || max.getAsDouble() > 1_000_000))
            fail(path + ".maxDamage", "expected finite nonnegative number at most 1000000");
        JsonElement damage = object.get("damage");
        if (complete || object.has("damage")) {
            if (damage == null || !damage.isJsonObject()) fail(path + ".damage", "expected object");
            checkFields(damage.getAsJsonObject(), Set.of("types"), path + ".damage");
            auditDamageMap(damage.getAsJsonObject(), "types", path + ".damage", false);
        }
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

    private static void auditLungNumber(JsonObject lungs, String field, double min, double max, boolean exclusiveMin, String parentPath) {
        JsonElement value = lungs.get(field);
        String path = parentPath + "." + field;
        if (value == null) fail(path, "required field is missing");
        if (!finiteNumber(value)) fail(path, "expected finite number");
        double number = value.getAsDouble();
        if ((exclusiveMin ? number <= min : number < min) || number > max) {
            fail(path, "expected finite number in " + (exclusiveMin ? "(" : "[") + min + ", " + max + "]");
        }
    }

    private static void auditReferenceSolution(JsonObject blood, String parentPath) {
        String path = parentPath + ".reference_solution";
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

    private static void auditBloodExclusions(JsonObject blood, String parentPath, boolean complete) {
        String path = parentPath + ".metabolism_exclusions";
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
        if (complete && reference != null && reference.isJsonObject()) {
            for (String id : reference.getAsJsonObject().keySet()) {
                if (!seen.contains(id)) fail(path, "must include every reference_solution reagent (missing " + id + ")");
            }
        }
    }

    private static void auditBloodNumber(JsonObject blood, String field, double min, double max, boolean exclusiveMin, String parentPath) {
        JsonElement value = blood.get(field);
        String path = parentPath + "." + field;
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

    private static void auditBloodMap(JsonObject blood, String field, boolean allowSigned, String parentPath) {
        String path = parentPath + "." + field;
        JsonElement value = blood.get(field);
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonObject()) fail(path, "expected object mapping canonical damage keys to numbers");
        JsonObject map = value.getAsJsonObject();
        for (String key : map.keySet()) {
            if (!DamageKeys.ALL.contains(key)) fail(path + "." + key, "unknown or non-canonical damage key");
            JsonElement amount = map.get(key);
            if (amount == null || !amount.isJsonPrimitive() || !amount.getAsJsonPrimitive().isNumber()) fail(path + "." + key, "expected number");
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

    private static void auditBoolean(JsonElement value, String path) {
        if (value == null) fail(path, "required field is missing");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) fail(path, "expected boolean");
    }

    public static void auditMovement(JsonObject movement, String path, boolean complete) {
        java.util.Set<String> allowed = new java.util.HashSet<>(MOVEMENT_FIELDS);
        allowed.add("type");
        checkFields(movement, allowed, path);
        JsonElement mode = movement.get("mode");
        if (complete || movement.has("mode")) {
            if (mode == null) fail(path + ".mode", "required field is missing");
            if (!mode.isJsonPrimitive() || !mode.getAsJsonPrimitive().isString() || !"grounded".equals(mode.getAsString())) {
                fail(path + ".mode", "only canonical mode 'grounded' is supported");
            }
        }
        for (String field : MOVEMENT_FIELDS) {
            if ("mode".equals(field)) continue;
            if (!complete && !movement.has(field)) continue;
            JsonElement value = movement.get(field);
            if (value == null) fail(path + "." + field, "required field is missing");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) fail(path + "." + field, "expected number");
            double number;
            try { number = value.getAsDouble(); } catch (NumberFormatException exception) {
                fail(path + "." + field, "expected finite number"); return;
            }
            if (!Double.isFinite(number) || number < 0 || number > 100) {
                fail(path + "." + field, "expected finite number in [0, 100]");
            }
        }
    }

    private static void auditThermalComponent(JsonObject object, String path, String type, boolean complete) {
        Set<String> fields = THERMAL_COMPONENT_FIELDS.get(type);
        java.util.Set<String> allowed = new java.util.HashSet<>(fields);
        allowed.add("type");
        checkFields(object, allowed, path);
        for (String field : fields) {
            if (!complete && !object.has(field)) continue;
            JsonElement value = object.get(field);
            String fieldPath = path + "." + field;
            if (value == null) fail(fieldPath, "required field is missing");
            if (!finiteNumber(value)) fail(fieldPath, "expected finite number");
            double number = value.getAsDouble();
            double min = switch (field) {
                case "mass_kg" -> 0.1;
                case "specific_heat_joules_per_kg_kelvin" -> 1.0;
                case "atmosphere_transfer_efficiency", "space_heat_capacity_joules_per_kelvin", "space_heat_scale",
                     "space_temperature_kelvin", "heat_damage_per_second", "cold_damage_per_second", "damage_cap" -> 0.0;
                case "thermal_regulation_threshold_kelvin", "metabolism_heat_joules_per_second",
                     "radiated_heat_joules_per_second", "implicit_heat_regulation_joules_per_second",
                     "sweat_heat_regulation_joules_per_second", "shivering_heat_regulation_joules_per_second" -> 0.0;
                default -> 150.0;
            };
            double max = switch (field) {
                case "mass_kg" -> 500.0;
                case "specific_heat_joules_per_kg_kelvin" -> 10000.0;
                case "atmosphere_transfer_efficiency" -> 1.0;
                case "space_heat_scale" -> 1000.0;
                case "heat_damage_per_second", "cold_damage_per_second", "damage_cap" -> 100.0;
                case "thermal_regulation_threshold_kelvin" -> 350.0;
                case "space_heat_capacity_joules_per_kelvin", "metabolism_heat_joules_per_second",
                     "radiated_heat_joules_per_second", "implicit_heat_regulation_joules_per_second",
                     "sweat_heat_regulation_joules_per_second", "shivering_heat_regulation_joules_per_second" -> 1_000_000.0;
                default -> 500.0;
            };
            boolean nonnegative = field.endsWith("joules_per_second") || field.equals("thermal_regulation_threshold_kelvin")
                    || field.equals("atmosphere_transfer_efficiency");
            if ((nonnegative ? number < min : number <= min) || number > max)
                fail(fieldPath, "out of range");
        }
        if (type.equals("TemperatureDamage") && object.has("heat_damage_threshold_kelvin")
                && object.has("cold_damage_threshold_kelvin")
                && object.get("heat_damage_threshold_kelvin").getAsDouble() <= object.get("cold_damage_threshold_kelvin").getAsDouble())
            fail(path, "heat threshold must exceed cold threshold");
    }

    private static void auditStringArray(JsonObject object, String field, Set<String> allowed, String parentPath) {
        JsonElement value = object.get(field);
        String path = parentPath + "." + field;
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
