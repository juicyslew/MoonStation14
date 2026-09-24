package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.mojang.serialization.JsonOps;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Strict structural audit used before handing resolved reagent JSON to codecs. */
public final class ReagentSchemaAudit {
    private ReagentSchemaAudit() {
    }

    private record Schema(Set<String> fields, Set<String> required) {
    }

    private static final Set<String> TOP = Set.of(
            "id", "name", "group", "desc", "physicaldesc", "flavor", "color",
            "boilingpoint", "meltingpoint", "contrabandseverity", "plantmetabolism",
            "metabolisms", "metamorphicsprite", "metamorphicmaxfilllevels",
            "metamorphicfillbasename", "metamorphicchangecolor", "priceperunit",
            "reactiveeffects", "recognizable", "fizziness", "worksonthedead",
            "alloweddepartments", "allowedjobs", "slipdata", "friction", "tilereactions",
            "footstepsound", "standsout", "flavorminimum", "evaporationspeed", "viscosity",
            "absorbent", "parent", "abstract");
    private static final Set<String> METABOLISM = Set.of("effects", "metabolites", "metabolismrate");
    private static final Set<String> COMMON = Set.of("type", "conditions", "probability", "minscale", "scaling");
    private static final Set<String> REACTIVE = Set.of("methods", "effects");
    private static final Set<String> DAMAGE_TYPES = DamageSpecifierData.KNOWN_DAMAGE_TYPES;
    private static final Set<String> DAMAGE_GROUPS = Set.of(
            "brute", "burn", "airloss", "toxin", "genetic", "metaphysical");
    private static final Set<String> POPUP_RECIPIENTS = Set.of("pvs", "local");
    private static final Set<String> POPUP_METHODS = Set.of("popupentity", "popupcoordinates");
    private static final Set<String> POPUP_VISUALS = Set.of(
            "small", "smallcaution", "medium", "mediumcaution", "large", "largecaution");

    private static final Map<String, Schema> EFFECTS = Map.ofEntries(
            entry("EvenHealthChange", effect(Set.of("ignoreresistances", "damage"), "damage")),
            entry("HealthChange", effect(Set.of("ignoreresistances", "damage"), "damage")),
             entry("Vomit", effect(Set.of())), entry("Jitter", effect(Set.of("amplitude", "frequency", "time", "refresh"))),
            entry("Drunk", effect(Set.of("boozepower"))),
            entry("ModifyBleed", effect(Set.of("amount"), "amount")),
            entry("Oxygenate", effect(Set.of("factor"))),
            entry("ModifyLungGas", effect(Set.of("ratios"), "ratios")),
             entry("AdjustAlert", effect(Set.of("alerttype", "clear", "showcooldown", "time"), "alerttype")),
            entry("SatiateHunger", effect(Set.of("factor"))),
            entry("ModifyBloodLevel", effect(Set.of("amount"), "amount")),
            entry("SatiateThirst", effect(Set.of("factor"))),
             entry("PopupMessage", effect(Set.of("subtype", "method", "visualtype", "messages"), "messages")),
            entry("Emote", effect(Set.of("emote", "showinguidebook", "showinchat", "force"), "emote")),
            entry("ModifyStatusEffect", effect(Set.of("effectproto", "time", "subtype", "delay"), "effectproto")),
            entry("AdjustReagent", effect(Set.of("reagent", "amount"), "reagent", "amount")),
            entry("CureZombieInfection", effect(Set.of("innoculate"))),
            entry("ArtifactDurabilityRestore", effect(Set.of())), entry("ArtifactUnlock", effect(Set.of())),
            entry("GenericStatusEffect", effect(Set.of("key", "component", "subtype", "time"), "key")),
             entry("Flammable", effect(Set.of("multiplier", "multiplieronexisting"))), entry("Ignite", effect(Set.of())),
             entry("AdjustTemperature", effect(Set.of("amount"), "amount")), entry("Extinguish", effect(Set.of("firestacksadjustment"))),
             entry("MovementSpeedModifier", effect(Set.of("walkspeedmodifier", "sprintspeedmodifier",
                     "effectproto", "time", "subtype", "delay"))),
            entry("CleanBloodstream", effect(Set.of("excluded", "cleanserate"), "excluded", "cleanserate")),
            entry("MakeSentient", effect(Set.of())), entry("Polymorph", effect(Set.of("prototype"), "prototype")),
            entry("ResetNarcolepsy", effect(Set.of())),
             entry("ModifyKnockdown", effect(Set.of("time", "subtype", "delay", "crawling", "drop"))),
             entry("Electrocute", effect(Set.of("electrocutetime", "shockdamage", "refresh", "bypassinsulation", "siemenscoefficient"))),
             entry("EyeDamage", effect(Set.of("amount"))), entry("ReduceRotting", effect(Set.of("seconds"), "seconds")),
            entry("CauseZombieInfection", effect(Set.of())));

    private static final Map<String, Schema> CONDITIONS = Map.ofEntries(
            entry("ReagentCondition", condition(Set.of("reagent", "max", "min", "inverted"), "reagent")),
            entry("MobStateCondition", condition(Set.of("mobstate", "inverted"), "mobstate")),
            entry("MetabolizerTypeCondition", condition(Set.of("subtype", "inverted"), "subtype")),
            entry("TemperatureCondition", condition(Set.of("max", "min", "inverted"))),
            entry("BreathingCondition", condition(Set.of("inverted"))),
            entry("InternalsCondition", condition(Set.of("inverted"))),
            entry("TagCondition", condition(Set.of("inverted", "tag"), "tag")),
            entry("HungerCondition", condition(Set.of("max", "min", "inverted"))));

    private static final Map<String, Schema> PLANTS = Map.ofEntries(
            entry("PlantAdjustNutrition", plant(Set.of("amount"), "amount")),
            entry("PlantAdjustWeeds", plant(Set.of("probability", "amount"), "amount")),
            entry("PlantAdjustPests", plant(Set.of("probability", "amount"), "amount")),
            entry("PlantAdjustHealth", plant(Set.of("amount"), "amount")),
            entry("PlantAdjustWater", plant(Set.of("amount"), "amount")),
            entry("PlantAdjustToxins", plant(Set.of("amount"), "amount")),
            entry("PlantCryoxadone", plant(Set.of())),
            entry("PlantAffectGrowth", plant(Set.of("probability", "amount"), "probability", "amount")),
            entry("PlantDiethylamine", plant(Set.of())),
            entry("PlantAdjustMutationMod", plant(Set.of("probability", "amount"), "amount")),
            entry("PlantMutateChemicals", plant(Set.of("randompickbotanyreagent"), "randompickbotanyreagent")),
            entry("PlantPhalanximine", plant(Set.of("minscale"), "minscale")),
            entry("PlantRemoveKudzu", plant(Set.of())), entry("RobustHarvest", plant(Set.of())),
            entry("PlantAdjustMutationLevel", plant(Set.of("amount"), "amount")),
            entry("PlantRestoreSeeds", plant(Set.of("probability"), "probability")),
            entry("PlantAdjustPotency", plant(Set.of("amount"), "amount")));

    private static Map.Entry<String, Schema> entry(String type, Schema schema) {
        return Map.entry(type, schema);
    }

    private static Schema effect(Set<String> fields, String... required) {
        return new Schema(fields, Set.of(required));
    }

    private static Schema condition(Set<String> fields, String... required) {
        return new Schema(fields, Set.of(required));
    }

    private static Schema plant(Set<String> fields, String... required) {
        return new Schema(fields, Set.of(required));
    }

    public static void audit(ResourceLocation id, JsonObject reagent) {
        if (reagent == null) {
            fail(id, "$", "expected object");
        }
        checkUnknown(id, "$", reagent, TOP);
        requireString(id, reagent, "id", "id");
        if (!id.getPath().equals(reagent.get("id").getAsString())) {
            fail(id, "id", "resource id mismatch");
        }

        stringFields(id, reagent, "name", "group", "desc", "physicaldesc", "flavor",
                "contrabandseverity", "metamorphicfillbasename");
        numberFields(id, reagent, "boilingpoint", "meltingpoint", "metamorphicmaxfilllevels", "priceperunit",
                "fizziness", "friction", "flavorminimum", "evaporationspeed", "viscosity");
        booleanFields(id, reagent, "metamorphicchangecolor", "recognizable", "worksonthedead", "standsout",
                "absorbent", "abstract");
        if (reagent.has("color")) string(id, "color", reagent.get("color"));
        if (reagent.has("metamorphicsprite")) {
            JsonObject sprite = object(id, "metamorphicsprite", reagent.get("metamorphicsprite"));
            checkUnknown(id, "metamorphicsprite", sprite, Set.of("sprite", "state"));
            requireString(id, sprite, "sprite", "metamorphicsprite.sprite");
            requireString(id, sprite, "state", "metamorphicsprite.state");
        }
        stringArrayField(id, reagent, "alloweddepartments");
        stringArrayField(id, reagent, "allowedjobs");
        if (reagent.has("parent")) {
            JsonElement parent = reagent.get("parent");
            if (parent.isJsonArray()) stringArray(id, "parent", parent.getAsJsonArray());
            else string(id, "parent", parent);
        }

        if (reagent.has("plantmetabolism")) {
            JsonArray plants = array(id, reagent, "plantmetabolism");
            for (int i = 0; i < plants.size(); i++) auditPlant(id, "plantmetabolism[" + i + "]", object(id, "plantmetabolism[" + i + "]", plants.get(i)));
        }
        if (reagent.has("metabolisms")) auditMetabolisms(id, object(id, "metabolisms", reagent.get("metabolisms")));
        if (reagent.has("reactiveeffects")) auditReactiveEffects(id, object(id, "reactiveeffects", reagent.get("reactiveeffects")));
        if (reagent.has("slipdata")) auditSlipData(id, "slipdata", object(id, "slipdata", reagent.get("slipdata")));
        if (reagent.has("footstepsound")) auditFootstepSound(id, "footstepsound", object(id, "footstepsound", reagent.get("footstepsound")));
        if (reagent.has("tilereactions")) auditTileReactions(id, array(id, reagent, "tilereactions"));
    }

    public static int auditReferences(ResourceLocation id, JsonObject reagent, Set<ResourceLocation> known) {
        return ReagentReferenceValidator.validate(id, reagent, known);
    }

    public static int auditReferences(ResourceLocation id, JsonObject reagent,
                                      Map<ResourceLocation, JsonObject> raw) {
        return ReagentReferenceValidator.validate(id, reagent, raw);
    }

    private static void auditMetabolisms(ResourceLocation id, JsonObject metabolisms) {
        for (Map.Entry<String, JsonElement> stage : metabolisms.entrySet()) {
            String path = "metabolisms." + stage.getKey();
            try {
                MetabolismStage.fromSerializedName(stage.getKey());
            } catch (IllegalArgumentException exception) {
                fail(id, path, "unknown metabolism stage");
            }
            JsonObject object = object(id, path, stage.getValue());
            checkUnknown(id, path, object, METABOLISM);
            if (object.has("effects")) auditEffects(id, path, array(id, object, "effects", path + ".effects"));
            if (object.has("metabolites")) {
                JsonObject metabolites = object(id, path + ".metabolites", object.get("metabolites"));
                for (Map.Entry<String, JsonElement> entry : metabolites.entrySet()) {
                    finiteNonnegativeNumber(id, path + ".metabolites." + entry.getKey(), entry.getValue());
                }
            }
            if (object.has("metabolismrate")) {
                finitePositiveNumber(id, path + ".metabolismrate", object.get("metabolismrate"));
            }
        }
    }

    private static void auditReactiveEffects(ResourceLocation id, JsonObject reactions) {
        for (Map.Entry<String, JsonElement> entry : reactions.entrySet()) {
            String path = "reactiveeffects." + entry.getKey();
            JsonObject reaction = object(id, path, entry.getValue());
            checkUnknown(id, path, reaction, REACTIVE);
            requireArray(id, reaction, "methods", path + ".methods");
            stringArray(id, path + ".methods", reaction.getAsJsonArray("methods"));
            requireArray(id, reaction, "effects", path + ".effects");
            auditEffects(id, path, reaction.getAsJsonArray("effects"));
        }
    }

    private static void auditEffects(ResourceLocation id, String path, JsonArray effects) {
        for (int i = 0; i < effects.size(); i++) {
            String effectPath = path + ".effects[" + i + "]";
            JsonObject effect = object(id, effectPath, effects.get(i));
            String type = requireString(id, effect, "type", effectPath + ".type");
            Schema schema = EFFECTS.get(type);
            if (schema == null) fail(id, effectPath + ".type", "unknown effect type '" + type + "'");
            Set<String> allowed = new HashSet<>(COMMON);
            allowed.addAll(schema.fields());
            checkUnknown(id, effectPath, effect, allowed);
            checkRequired(id, effectPath, effect, schema.required());
            auditCommon(id, effectPath, effect);
            auditEffectFields(id, effectPath, type, effect);
            if (effect.has("conditions")) {
                JsonArray conditions = array(id, effect, "conditions", effectPath + ".conditions");
                for (int j = 0; j < conditions.size(); j++) {
                    auditCondition(id, effectPath + ".conditions[" + j + "]",
                            object(id, effectPath + ".conditions[" + j + "]", conditions.get(j)));
                }
            }
        }
    }

    private static void auditCommon(ResourceLocation id, String path, JsonObject effect) {
        if (effect.has("probability")) number(id, path + ".probability", effect.get("probability"));
        if (effect.has("minscale")) number(id, path + ".minscale", effect.get("minscale"));
        if (effect.has("scaling")) bool(id, path + ".scaling", effect.get("scaling"));
    }

    private static void auditEffectFields(ResourceLocation id, String path, String type, JsonObject effect) {
        switch (type) {
            case "EvenHealthChange" -> {
                optionalBool(id, path, effect, "ignoreresistances");
                numericObject(id, path + ".damage", effect.get("damage"), DAMAGE_GROUPS);
            }
            case "HealthChange" -> {
                optionalBool(id, path, effect, "ignoreresistances");
                JsonObject damage = object(id, path + ".damage", effect.get("damage"));
                checkUnknown(id, path + ".damage", damage, Set.of("types"));
                if (damage.has("types")) {
                    requireObjectField(id, damage, "types", path + ".damage.types");
                    numericObject(id, path + ".damage.types", damage.get("types"), DAMAGE_TYPES);
                }
            }
            case "Jitter" -> {
                optionalNonnegativeNumber(id, path, effect, "amplitude");
                optionalNonnegativeNumber(id, path, effect, "frequency");
                optionalNonnegativeNumber(id, path, effect, "time");
                optionalBool(id, path, effect, "refresh");
            }
            case "Drunk" -> optionalNonnegativeNumber(id, path, effect, "boozepower");
            case "ModifyBleed", "ModifyBloodLevel", "AdjustTemperature", "ReduceRotting" ->
                    number(id, path + "." + switch (type) {
                        case "ReduceRotting" -> "seconds";
                        default -> "amount";
                    }, effect.get(switch (type) {
                        case "ReduceRotting" -> "seconds";
                        default -> "amount";
                    }));
            case "Oxygenate", "SatiateHunger", "SatiateThirst" ->
                    optionalNumber(id, path, effect, switch (type) {
                        default -> "factor";
                    });
            case "Flammable" -> {
                optionalNumber(id, path, effect, "multiplier");
                optionalNumber(id, path, effect, "multiplieronexisting");
            }
            case "ModifyLungGas" -> numericObject(id, path + ".ratios", effect.get("ratios"));
            case "AdjustAlert" -> {
                alertReference(id, path + ".alerttype", effect.get("alerttype"));
                optionalBool(id, path, effect, "clear");
                optionalBool(id, path, effect, "showcooldown");
                optionalNonnegativeNumber(id, path, effect, "time");
            }
            case "PopupMessage" -> {
                optionalEnum(id, path, effect, "subtype", POPUP_RECIPIENTS);
                optionalEnum(id, path, effect, "method", POPUP_METHODS);
                optionalEnum(id, path, effect, "visualtype", POPUP_VISUALS);
                JsonArray messages = array(id, effect, "messages");
                if (messages.isEmpty()) fail(id, path + ".messages", "expected a nonempty array");
                for (int i = 0; i < messages.size(); i++) {
                    nonBlankString(id, path + ".messages[" + i + "]", messages.get(i));
                }
            }
            case "Emote" -> {
                nonBlankString(id, path + ".emote", effect.get("emote"));
                optionalBool(id, path, effect, "showinguidebook");
                optionalBool(id, path, effect, "showinchat");
                optionalBool(id, path, effect, "force");
            }
            case "ModifyStatusEffect" -> {
                nonBlankString(id, path + ".effectproto", effect.get("effectproto"));
                optionalNullableNonnegativeNumber(id, path, effect, "time");
                optionalOperation(id, path, effect, "subtype");
                optionalNonnegativeNumber(id, path, effect, "delay");
            }
            case "AdjustReagent" -> {
                nonBlankString(id, path + ".reagent", effect.get("reagent"));
                number(id, path + ".amount", effect.get("amount"));
            }
            case "CureZombieInfection" -> optionalBool(id, path, effect, "innoculate");
            case "GenericStatusEffect" -> {
                nonBlankString(id, path + ".key", effect.get("key"));
                optionalString(id, path, effect, "component");
                optionalOperation(id, path, effect, "subtype");
                optionalNonnegativeNumber(id, path, effect, "time");
            }
            case "MovementSpeedModifier" -> {
                optionalNonnegativeNumber(id, path, effect, "walkspeedmodifier");
                optionalNonnegativeNumber(id, path, effect, "sprintspeedmodifier");
                optionalNonBlankString(id, path, effect, "effectproto");
                optionalNullableNonnegativeNumber(id, path, effect, "time");
                optionalOperation(id, path, effect, "subtype");
                optionalNonnegativeNumber(id, path, effect, "delay");
            }
            case "CleanBloodstream" -> {
                string(id, path + ".excluded", effect.get("excluded"));
                number(id, path + ".cleanserate", effect.get("cleanserate"));
            }
            case "Polymorph" -> string(id, path + ".prototype", effect.get("prototype"));
            case "ModifyKnockdown" -> {
                optionalNullableNonnegativeNumber(id, path, effect, "time");
                optionalOperation(id, path, effect, "subtype");
                optionalNonnegativeNumber(id, path, effect, "delay");
                optionalBool(id, path, effect, "crawling");
                optionalBool(id, path, effect, "drop");
            }
            case "Electrocute" -> {
                optionalNonnegativeNumber(id, path, effect, "electrocutetime");
                optionalExactNonnegativeInteger(id, path, effect, "shockdamage");
                optionalBool(id, path, effect, "refresh");
                optionalBool(id, path, effect, "bypassinsulation");
                optionalNonnegativeNumber(id, path, effect, "siemenscoefficient");
            }
            case "EyeDamage" -> optionalInteger(id, path, effect, "amount");
            case "Extinguish" -> optionalNumber(id, path, effect, "firestacksadjustment");
            default -> {
                // Unit effects have no subtype-specific fields.
            }
        }
    }

    private static void auditCondition(ResourceLocation id, String path, JsonObject condition) {
        String type = requireString(id, condition, "type", path + ".type");
        Schema schema = CONDITIONS.get(type);
        if (schema == null) fail(id, path + ".type", "unknown condition type '" + type + "'");
        Set<String> allowed = new HashSet<>(Set.of("type"));
        allowed.addAll(schema.fields());
        checkUnknown(id, path, condition, allowed);
        checkRequired(id, path, condition, schema.required());
        switch (type) {
            case "ReagentCondition" -> {
                string(id, path + ".reagent", condition.get("reagent"));
                optionalNumber(id, path, condition, "max");
                optionalNumber(id, path, condition, "min");
                optionalBool(id, path, condition, "inverted");
            }
            case "MobStateCondition" -> {
                string(id, path + ".mobstate", condition.get("mobstate"));
                optionalBool(id, path, condition, "inverted");
            }
            case "MetabolizerTypeCondition" -> {
                stringArray(id, path + ".subtype", array(id, condition, "subtype", path + ".subtype"));
                optionalBool(id, path, condition, "inverted");
            }
            case "TemperatureCondition", "HungerCondition" -> {
                optionalNumber(id, path, condition, "max");
                optionalNumber(id, path, condition, "min");
                optionalBool(id, path, condition, "inverted");
            }
            case "BreathingCondition" -> optionalBool(id, path, condition, "inverted");
            case "InternalsCondition" -> optionalBool(id, path, condition, "inverted");
            case "TagCondition" -> {
                optionalBool(id, path, condition, "inverted");
                string(id, path + ".tag", condition.get("tag"));
            }
            default -> {
                // BreathingCondition has no fields.
            }
        }
    }

    private static void auditPlant(ResourceLocation id, String path, JsonObject plant) {
        String type = requireString(id, plant, "type", path + ".type");
        Schema schema = PLANTS.get(type);
        if (schema == null) fail(id, path + ".type", "unknown plant effect type '" + type + "'");
        Set<String> allowed = new HashSet<>(Set.of("type"));
        allowed.addAll(schema.fields());
        checkUnknown(id, path, plant, allowed);
        checkRequired(id, path, plant, schema.required());
        if (plant.has("amount")) number(id, path + ".amount", plant.get("amount"));
        if (plant.has("probability")) number(id, path + ".probability", plant.get("probability"));
        if (plant.has("minscale")) number(id, path + ".minscale", plant.get("minscale"));
        if (plant.has("randompickbotanyreagent")) string(id, path + ".randompickbotanyreagent", plant.get("randompickbotanyreagent"));
    }

    private static void auditSlipData(ResourceLocation id, String path, JsonObject slip) {
        checkUnknown(id, path, slip, Set.of("requiredslipspeed", "superslippery"));
        requireNumber(id, slip, "requiredslipspeed", path + ".requiredslipspeed");
        optionalBool(id, path, slip, "superslippery");
    }

    private static void auditFootstepSound(ResourceLocation id, String path, JsonObject sound) {
        checkUnknown(id, path, sound, Set.of("collection", "params"));
        requireString(id, sound, "collection", path + ".collection");
        if (sound.has("params")) {
            JsonObject params = object(id, path + ".params", sound.get("params"));
            checkUnknown(id, path + ".params", params, Set.of("volume"));
            requireNumber(id, params, "volume", path + ".params.volume");
        }
    }

    private static void auditTileReactions(ResourceLocation id, JsonArray reactions) {
        Set<String> fields = Set.of("type", "temperaturemultiplier", "cleancost", "entity", "usage",
                "maxontile", "randomoffsetmax", "maxontilewhitelist");
        for (int i = 0; i < reactions.size(); i++) {
            String path = "tilereactions[" + i + "]";
            JsonObject reaction = object(id, path, reactions.get(i));
            checkUnknown(id, path, reaction, fields);
            requireString(id, reaction, "type", path + ".type");
            optionalNumber(id, path, reaction, "temperaturemultiplier");
            optionalNumber(id, path, reaction, "cleancost");
            optionalString(id, path, reaction, "entity");
            optionalNumber(id, path, reaction, "usage");
            if (reaction.has("maxontile")) number(id, path + ".maxontile", reaction.get("maxontile"));
            optionalNumber(id, path, reaction, "randomoffsetmax");
            if (reaction.has("maxontilewhitelist")) {
                JsonObject whitelist = object(id, path + ".maxontilewhitelist", reaction.get("maxontilewhitelist"));
                checkUnknown(id, path + ".maxontilewhitelist", whitelist, Set.of("tags"));
                stringArray(id, path + ".maxontilewhitelist.tags",
                        array(id, whitelist, "tags", path + ".maxontilewhitelist.tags"));
            }
        }
    }

    private static JsonObject object(ResourceLocation id, String path, JsonElement value) {
        if (value == null || !value.isJsonObject()) fail(id, path, "expected object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(ResourceLocation id, JsonObject object, String field) {
        return array(id, object, field, field);
    }

    private static JsonArray array(ResourceLocation id, JsonObject object, String field, String path) {
        requireArray(id, object, field, path);
        return object.getAsJsonArray(field);
    }

    private static void requireArray(ResourceLocation id, JsonObject object, String field, String path) {
        if (!object.has(field) || !object.get(field).isJsonArray()) fail(id, path, "expected array");
    }

    private static void requireObjectField(ResourceLocation id, JsonObject object, String field, String path) {
        if (!object.has(field) || !object.get(field).isJsonObject()) fail(id, path, "expected object");
    }

    private static String requireString(ResourceLocation id, JsonObject object, String field, String path) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isString()) {
            fail(id, path, "expected string");
        }
        return object.get(field).getAsString();
    }

    private static void requireNumber(ResourceLocation id, JsonObject object, String field, String path) {
        if (!object.has(field)) fail(id, path, "missing required field");
        number(id, path, object.get(field));
    }

    private static void checkRequired(ResourceLocation id, String path, JsonObject object, Set<String> required) {
        for (String field : required) if (!object.has(field)) fail(id, path + "." + field, "missing required field");
    }

    private static void checkUnknown(ResourceLocation id, String path, JsonObject object, Set<String> allowed) {
        for (String field : object.keySet()) if (!allowed.contains(field)) fail(id, path + "." + field, "unknown field");
    }

    private static void number(ResourceLocation id, String path, JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            fail(id, path, "expected number");
        }
        double parsed = value.getAsDouble();
        if (!Double.isFinite(parsed) || !Float.isFinite(value.getAsFloat())) {
            fail(id, path, "expected a finite number");
        }
    }

    private static void alertReference(ResourceLocation id, String path, JsonElement value) {
        nonBlankString(id, path, value);
        try {
            ModAlerts.createKeyFromReference(value.getAsString());
        } catch (RuntimeException exception) {
            fail(id, path, exception.getMessage() == null
                    ? "malformed alert reference" : exception.getMessage());
        }
    }

    private static void finitePositiveNumber(ResourceLocation id, String path, JsonElement value) {
        number(id, path, value);
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || !Float.isFinite(value.getAsFloat()) || number <= 0d) {
            fail(id, path, "expected a finite positive number");
        }
    }

    private static void finiteNonnegativeNumber(ResourceLocation id, String path, JsonElement value) {
        number(id, path, value);
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || !Float.isFinite(value.getAsFloat()) || number < 0d) {
            fail(id, path, "expected a finite nonnegative number");
        }
    }

    private static void numericObject(ResourceLocation id, String path, JsonElement value, Set<String> knownKeys) {
        JsonObject object = object(id, path, value);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!knownKeys.contains(entry.getKey())) fail(id, path + "." + entry.getKey(), "unknown key");
            number(id, path + "." + entry.getKey(), entry.getValue());
        }
    }

    private static void numericObject(ResourceLocation id, String path, JsonElement value) {
        JsonObject object = object(id, path, value);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            number(id, path + "." + entry.getKey(), entry.getValue());
        }
    }

    private static void string(ResourceLocation id, String path, JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) fail(id, path, "expected string");
    }

    private static void bool(ResourceLocation id, String path, JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) fail(id, path, "expected boolean");
    }

    private static void optionalNumber(ResourceLocation id, String path, JsonObject object, String field) {
        if (object.has(field)) number(id, path + "." + field, object.get(field));
    }

    private static void optionalNonnegativeNumber(ResourceLocation id, String path, JsonObject object, String field) {
        if (object.has(field)) finiteNonnegativeNumber(id, path + "." + field, object.get(field));
    }

    private static void optionalNullableNonnegativeNumber(ResourceLocation id, String path,
                                                          JsonObject object, String field) {
        if (!object.has(field)) return;
        JsonElement value = object.get(field);
        if (value.isJsonNull()) return;
        finiteNonnegativeNumber(id, path + "." + field, value);
    }

    private static void optionalExactNonnegativeInteger(ResourceLocation id, String path, JsonObject object, String field) {
        if (object.has(field)) {
            exactInteger(id, path + "." + field, object.get(field));
            finiteNonnegativeNumber(id, path + "." + field, object.get(field));
        }
    }

    private static void optionalInteger(ResourceLocation id, String path, JsonObject object, String field) {
        if (object.has(field)) exactInteger(id, path + "." + field, object.get(field));
    }

    private static void optionalString(ResourceLocation id, String path, JsonObject object, String field) {
        if (object.has(field)) string(id, path + "." + field, object.get(field));
    }

    private static void optionalNonBlankString(ResourceLocation id, String path,
                                               JsonObject object, String field) {
        if (object.has(field)) nonBlankString(id, path + "." + field, object.get(field));
    }

    private static void optionalOperation(ResourceLocation id, String path,
                                          JsonObject object, String field) {
        if (!object.has(field)) return;
        string(id, path + "." + field, object.get(field));
        if (StatusEffectOperation.CODEC.parse(JsonOps.INSTANCE, object.get(field)).error().isPresent()) {
            fail(id, path + "." + field, "unknown status effect operation");
        }
    }

    private static void optionalBool(ResourceLocation id, String path, JsonObject object, String field) {
        if (object.has(field)) bool(id, path + "." + field, object.get(field));
    }

    private static void optionalEnum(ResourceLocation id, String path, JsonObject object,
                                     String field, Set<String> values) {
        if (object.has(field)) {
            string(id, path + "." + field, object.get(field));
            if (!values.contains(object.get(field).getAsString())) {
                fail(id, path + "." + field, "unknown enum value");
            }
        }
    }

    private static void nonBlankString(ResourceLocation id, String path, JsonElement value) {
        string(id, path, value);
        if (value.getAsString().isBlank()) fail(id, path, "expected a nonblank string");
    }

    private static void exactInteger(ResourceLocation id, String path, JsonElement value) {
        number(id, path, value);
        double parsed = value.getAsDouble();
        if (parsed != Math.rint(parsed) || parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            fail(id, path, "expected an integer");
        }
    }

    private static void stringFields(ResourceLocation id, JsonObject object, String... fields) {
        for (String field : fields) if (object.has(field)) string(id, field, object.get(field));
    }

    private static void numberFields(ResourceLocation id, JsonObject object, String... fields) {
        for (String field : fields) if (object.has(field)) number(id, field, object.get(field));
    }

    private static void booleanFields(ResourceLocation id, JsonObject object, String... fields) {
        for (String field : fields) if (object.has(field)) bool(id, field, object.get(field));
    }

    private static void stringArrayField(ResourceLocation id, JsonObject object, String field) {
        if (object.has(field)) stringArray(id, field, array(id, object, field));
    }

    private static void stringArray(ResourceLocation id, String path, JsonArray array) {
        for (int i = 0; i < array.size(); i++) string(id, path + "[" + i + "]", array.get(i));
    }

    private static void fail(ResourceLocation id, String path, String message) {
        throw new IllegalArgumentException("Reagent " + id + " at " + path + ": " + message);
    }
}
