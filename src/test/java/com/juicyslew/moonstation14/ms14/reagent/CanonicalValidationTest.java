package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.ReagentReferenceValidator;
import com.juicyslew.moonstation14.component.codec.json.ReagentSchemaAudit;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalValidationTest {
    private static final ResourceLocation FIXTURE_ID = id("fixture");
    private static final ResourceLocation ADDON_FIXTURE_ID = addonId("fixture");

    @Test
    void plantProbabilitiesDecodeAndEncodeWithoutDefaulting() throws IOException {
        PrototypeCatalog<JsonObject> catalog = PrototypeResolver.resolve(
                ReagentResourceSmokeTest.loadResources(), new ReagentPrototypeMergeStrategy());

        assertPlantProbability(catalog, "diethylamine", "PlantAdjustPests", 0.1f);
        assertPlantProbability(catalog, "robustharvest", "PlantAdjustWeeds", 0.025f);
        assertPlantProbability(catalog, "robustharvest", "PlantAdjustPests", 0.025f);
    }

    @Test
    void allConditionSubtypesAcceptOnlyTheirCanonicalFields() {
        Map<String, String> valid = Map.of(
                "ReagentCondition", "{\"reagent\":\"water\",\"max\":10,\"min\":1}",
                "MobStateCondition", "{\"mobstate\":\"Alive\"}",
                "MetabolizerTypeCondition", "{\"subtype\":[\"human\"],\"inverted\":true}",
                "TemperatureCondition", "{\"max\":310,\"min\":270}",
                "BreathingCondition", "{}",
                "InternalsCondition", "{\"inverted\":true}",
                "TagCondition", "{\"inverted\":true,\"tag\":\"plant\"}",
                "HungerCondition", "{\"max\":10,\"min\":1}");

        for (Map.Entry<String, String> entry : valid.entrySet()) {
            JsonObject condition = JsonParser.parseString(entry.getValue()).getAsJsonObject();
            condition.addProperty("type", entry.getKey());
            assertDoesNotThrowAudit(effectWithCondition(condition));
        }
    }

    @Test
    void subtypeInapplicableConditionFieldsAreRejectedWithContext() {
        Map<String, List<String>> forbidden = Map.of(
                "ReagentCondition", List.of("mobstate", "subtype", "tag"),
                "MobStateCondition", List.of("reagent", "subtype", "max", "min", "tag"),
                "MetabolizerTypeCondition", List.of("reagent", "mobstate", "max", "min", "tag"),
                "TemperatureCondition", List.of("reagent", "mobstate", "subtype", "tag"),
                "BreathingCondition", List.of("reagent", "mobstate", "subtype", "max", "min", "tag"),
                "InternalsCondition", List.of("reagent", "mobstate", "subtype", "max", "min", "tag"),
                "TagCondition", List.of("reagent", "mobstate", "subtype", "max", "min"),
                "HungerCondition", List.of("reagent", "mobstate", "subtype", "tag"));

        for (Map.Entry<String, List<String>> entry : forbidden.entrySet()) {
            for (String field : entry.getValue()) {
                JsonObject condition = new JsonObject();
                condition.addProperty("type", entry.getKey());
                if (field.equals("subtype")) condition.addProperty(field, "human");
                else condition.addProperty(field, 1);
                assertContextualFailure(effectWithCondition(condition),
                        "metabolisms.bloodstream.effects[0].conditions[0]." + field);
            }
        }
    }

    @Test
    void statusDerivedSchemasMatchCodecFieldsAndNullableTime() {
        List<String> valid = List.of(
                "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"time\":null,\"subtype\":\"set\",\"delay\":1}",
                "{\"type\":\"GenericStatusEffect\",\"key\":\"legacy\",\"component\":\"Marker\",\"time\":2,\"subtype\":\"add\"}",
                "{\"type\":\"MovementSpeedModifier\",\"walkspeedmodifier\":0.5,\"sprintspeedmodifier\":1.5,\"effectproto\":\"CustomSpeed\",\"time\":null,\"subtype\":\"remove\",\"delay\":0.5}",
                "{\"type\":\"ModifyKnockdown\",\"time\":null,\"subtype\":\"update\",\"delay\":1,\"crawling\":true,\"drop\":true}"
        );
        for (String effect : valid) {
            assertDoesNotThrowAudit(effectWith(JsonParser.parseString(effect).getAsJsonObject()));
        }

        JsonObject genericNullTime = JsonParser.parseString(
                "{\"type\":\"GenericStatusEffect\",\"key\":\"legacy\",\"time\":null}").getAsJsonObject();
        assertContextualFailure(effectWith(genericNullTime),
                "metabolisms.bloodstream.effects[0].time");

        for (String type : List.of("ModifyStatusEffect", "GenericStatusEffect",
                "MovementSpeedModifier", "ModifyKnockdown")) {
            JsonObject wrongTime = new JsonObject();
            wrongTime.addProperty("type", type);
            if (type.equals("ModifyStatusEffect")) wrongTime.addProperty("effectproto", "Jitter");
            if (type.equals("GenericStatusEffect")) wrongTime.addProperty("key", "legacy");
            wrongTime.addProperty("time", "2");
            assertContextualFailure(effectWith(wrongTime),
                    "metabolisms.bloodstream.effects[0].time");

            JsonObject unknown = wrongTime.deepCopy();
            unknown.remove("time");
            unknown.addProperty("unknown", true);
            assertContextualFailure(effectWith(unknown),
                    "metabolisms.bloodstream.effects[0].unknown");
        }
    }

    @Test
    void statusDerivedAuditRejectsInvalidOperationsAndFiniteValues() {
        for (String operation : List.of("UPDATE", "replace", "")) {
            JsonObject effect = JsonParser.parseString(
                    "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"subtype\":\""
                            + operation + "\"}").getAsJsonObject();
            assertContextualFailure(effectWith(effect),
                    "metabolisms.bloodstream.effects[0].subtype");
        }
        for (String source : List.of(
                "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"time\":-1}",
                "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"delay\":-1}",
                "{\"type\":\"MovementSpeedModifier\",\"walkspeedmodifier\":-1}",
                "{\"type\":\"MovementSpeedModifier\",\"sprintspeedmodifier\":-1}",
                "{\"type\":\"ModifyKnockdown\",\"delay\":-1}")) {
            assertContextualFailure(effectWith(JsonParser.parseString(source).getAsJsonObject()),
                    "metabolisms.bloodstream.effects[0]");
        }
    }

    @Test
    void unknownAndMalformedDiscriminatorsAreContextual() {
        JsonObject unknownEffect = effectWithCondition(null);
        unknownEffect.getAsJsonObject("metabolisms").getAsJsonObject("bloodstream")
                .getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("type", "UnknownEffect");
        assertContextualFailure(unknownEffect, "metabolisms.bloodstream.effects[0].type");

        JsonObject malformedEffect = effectWithCondition(null);
        malformedEffect.getAsJsonObject("metabolisms").getAsJsonObject("bloodstream")
                .getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("type", 4);
        assertContextualFailure(malformedEffect, "metabolisms.bloodstream.effects[0].type");

        JsonObject unknownCondition = effectWithCondition(JsonParser.parseString(
                "{\"type\":\"UnknownCondition\"}").getAsJsonObject());
        assertContextualFailure(unknownCondition,
                "metabolisms.bloodstream.effects[0].conditions[0].type");

        JsonObject malformedCondition = effectWithCondition(new JsonObject());
        assertContextualFailure(malformedCondition,
                "metabolisms.bloodstream.effects[0].conditions[0].type");

        JsonObject unknownPlant = baseReagent();
        JsonArray plants = new JsonArray();
        JsonObject plant = new JsonObject();
        plant.addProperty("type", "UnknownPlant");
        plants.add(plant);
        unknownPlant.add("plantmetabolism", plants);
        assertContextualFailure(unknownPlant, "plantmetabolism[0].type");

        JsonObject malformedPlant = baseReagent();
        JsonArray malformedPlants = new JsonArray();
        JsonObject malformedPlantEntry = new JsonObject();
        malformedPlantEntry.addProperty("type", 4);
        malformedPlants.add(malformedPlantEntry);
        malformedPlant.add("plantmetabolism", malformedPlants);
        assertContextualFailure(malformedPlant, "plantmetabolism[0].type");
    }

    @Test
    void malformedEntriesAndNestedShapesFailContextually() {
        JsonObject effectEntry = baseReagent();
        effectEntry.add("metabolisms", stageWith("effects", JsonParser.parseString("[1]").getAsJsonArray()));
        assertContextualFailure(effectEntry, "metabolisms.bloodstream.effects[0]");

        JsonObject conditionEntry = effectWithCondition(null);
        JsonObject effect = conditionEntry.getAsJsonObject("metabolisms").getAsJsonObject("bloodstream")
                .getAsJsonArray("effects").get(0).getAsJsonObject();
        effect.add("conditions", JsonParser.parseString("[1]").getAsJsonArray());
        assertContextualFailure(conditionEntry,
                "metabolisms.bloodstream.effects[0].conditions[0]");

        JsonObject plantEntry = baseReagent();
        plantEntry.add("plantmetabolism", JsonParser.parseString("[1]").getAsJsonArray());
        assertContextualFailure(plantEntry, "plantmetabolism[0]");

        JsonObject nullConditions = effectWithCondition(null);
        nullConditions.getAsJsonObject("metabolisms").getAsJsonObject("bloodstream")
                .getAsJsonArray("effects").get(0).getAsJsonObject().add("conditions", null);
        assertContextualFailure(nullConditions,
                "metabolisms.bloodstream.effects[0].conditions");

        JsonObject badMetaboliteValue = baseReagent();
        JsonObject stage = new JsonObject();
        JsonObject metabolites = new JsonObject();
        metabolites.addProperty("water", "not-a-number");
        stage.add("metabolites", metabolites);
        JsonObject metabolism = new JsonObject();
        metabolism.add("bloodstream", stage);
        badMetaboliteValue.add("metabolisms", metabolism);
        assertContextualFailure(badMetaboliteValue, "metabolisms.bloodstream.metabolites.water");

        JsonObject badMetabolitesShape = baseReagent();
        JsonObject badStage = new JsonObject();
        badStage.add("metabolites", new JsonArray());
        JsonObject badMetabolism = new JsonObject();
        badMetabolism.add("bloodstream", badStage);
        badMetabolitesShape.add("metabolisms", badMetabolism);
        assertContextualFailure(badMetabolitesShape, "metabolisms.bloodstream.metabolites");

        JsonObject badMethods = baseReagent();
        JsonObject reaction = new JsonObject();
        reaction.add("methods", JsonParser.parseString("[1]").getAsJsonArray());
        reaction.add("effects", new JsonArray());
        JsonObject reactive = new JsonObject();
        reactive.add("touch", reaction);
        badMethods.add("reactiveeffects", reactive);
        assertContextualFailure(badMethods, "reactiveeffects.touch.methods[0]");

        JsonObject badReactiveEffects = baseReagent();
        JsonObject badReaction = new JsonObject();
        badReaction.add("methods", JsonParser.parseString("[\"touch\"]").getAsJsonArray());
        badReaction.add("effects", JsonParser.parseString("[1]").getAsJsonArray());
        JsonObject badReactive = new JsonObject();
        badReactive.add("touch", badReaction);
        badReactiveEffects.add("reactiveeffects", badReactive);
        assertContextualFailure(badReactiveEffects, "reactiveeffects.touch.effects[0]");
    }

    @Test
    void allResolvedProductionPrototypesPassAuditReferencesAndTypedRoundTrip() throws IOException {
        Map<ResourceLocation, JsonObject> raw = ReagentResourceSmokeTest.loadResources();
        PrototypeCatalog<JsonObject> catalog = PrototypeResolver.resolve(raw, new ReagentPrototypeMergeStrategy());
        int references = 0;

        assertEquals(411, raw.size());
        assertEquals(406, catalog.size());
        for (ResourceLocation reagentId : catalog.keys()) {
            JsonObject json = catalog.get(reagentId);
            ReagentSchemaAudit.audit(reagentId, json);
            references += ReagentReferenceValidator.validate(reagentId, json, raw);
            ReagentData decoded = ReagentData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            JsonObject encoded = ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow().getAsJsonObject();
            assertEquals(decoded, ReagentData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(), reagentId.toString());
        }
        assertEquals(334, references);
    }

    @Test
    void productionReferenceNegativesIncludeSourceAndPathContext() {
        JsonObject missing = effectWithReference("missing");
        assertReferenceFailure(missing, Map.of(id("known"), baseReagent()),
                "metabolisms.bloodstream.effects[0].reagent");

        JsonObject caseMismatch = effectWithReference("Known");
        assertReferenceFailure(caseMismatch, Map.of(id("known"), baseReagent()),
                "metabolisms.bloodstream.effects[0].reagent");

        Map<ResourceLocation, JsonObject> abstractTarget = new LinkedHashMap<>();
        JsonObject target = baseReagent();
        target.addProperty("id", "known");
        target.addProperty("abstract", true);
        abstractTarget.put(id("known"), target);
        assertReferenceFailure(effectWithReference("known"), abstractTarget,
                "metabolisms.bloodstream.effects[0].reagent");
    }

    @Test
    void reagentReferencesUseRuntimeNamespaceRuleAcrossAllSources() {
        Map<ResourceLocation, JsonObject> moonstation14Water =
                Map.of(id("water"), baseReagent());
        Map<ResourceLocation, JsonObject> addonWater =
                Map.of(addonId("water"), baseReagent());

        assertEquals(4, ReagentReferenceValidator.validate(ADDON_FIXTURE_ID,
                reagentWithReferences("water"), moonstation14Water));
        assertReferenceFailure(ADDON_FIXTURE_ID, reagentWithReferences("water"), addonWater,
                "metabolisms.bloodstream.metabolites.water");
        assertEquals(4, ReagentReferenceValidator.validate(ADDON_FIXTURE_ID,
                reagentWithReferences("addon:water"), addonWater));
    }

    @Test
    void missingCaseAndAbstractDiagnosticsRemainContextualForBothReferenceForms() {
        String path = "metabolisms.bloodstream.effects[0].reagent";

        assertReferenceFailure(ADDON_FIXTURE_ID, effectWithReference("missing"),
                Map.of(id("known"), baseReagent()), path,
                "unresolved reagent reference 'missing'");
        assertReferenceFailure(ADDON_FIXTURE_ID, effectWithReference("addon:missing"),
                Map.of(addonId("known"), baseReagent()), path,
                "unresolved reagent reference 'addon:missing'");

        assertReferenceFailure(ADDON_FIXTURE_ID, effectWithReference("Known"),
                Map.of(id("known"), baseReagent()), path,
                "malformed reagent reference 'Known'");
        assertReferenceFailure(ADDON_FIXTURE_ID, effectWithReference("addon:Known"),
                Map.of(addonId("known"), baseReagent()), path,
                "malformed reagent reference 'addon:Known'");

        Map<ResourceLocation, JsonObject> abstractMoonstation14Water =
                new LinkedHashMap<>();
        JsonObject abstractMoonstation14 = baseReagent();
        abstractMoonstation14.addProperty("abstract", true);
        abstractMoonstation14Water.put(id("water"), abstractMoonstation14);
        assertReferenceFailure(ADDON_FIXTURE_ID, effectWithReference("water"), abstractMoonstation14Water, path,
                "reagent reference targets abstract reagent 'water'");

        Map<ResourceLocation, JsonObject> abstractAddonWater = new LinkedHashMap<>();
        JsonObject abstractAddon = baseReagent();
        abstractAddon.addProperty("abstract", true);
        abstractAddonWater.put(addonId("water"), abstractAddon);
        assertReferenceFailure(ADDON_FIXTURE_ID, effectWithReference("addon:water"), abstractAddonWater, path,
                "reagent reference targets abstract reagent 'addon:water'");
    }

    @Test
    void managerReloadInvokesReagentSchemaAndReferenceValidation() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);

        PrototypeLoadException exception = assertThrows(PrototypeLoadException.class,
                () -> manager.reload(ModReagents.REAGENT_TYPE,
                        Map.of(FIXTURE_ID, effectWithReference("missing"))));

        assertTrue(exception.getMessage().contains(FIXTURE_ID.toString()), exception.getMessage());
        assertTrue(exception.getMessage().contains("metabolisms.bloodstream.effects[0].reagent"),
                exception.getMessage());
    }

    private static void assertPlantProbability(PrototypeCatalog<JsonObject> catalog, String reagent,
                                               String type, float expected) {
        ReagentData decoded = ReagentData.CODEC.parse(JsonOps.INSTANCE, catalog.get(id(reagent))).getOrThrow();
        JsonObject encoded = ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow().getAsJsonObject();
        for (var element : encoded.getAsJsonArray("plantmetabolism")) {
            JsonObject plant = element.getAsJsonObject();
            if (type.equals(plant.get("type").getAsString())) {
                assertEquals(expected, plant.get("probability").getAsFloat(), reagent + ":" + type);
                return;
            }
        }
        throw new AssertionError("Missing plant metabolism " + type + " in " + reagent);
    }

    private static JsonObject effectWithCondition(JsonObject condition) {
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "Jitter");
        if (condition != null) {
            JsonArray conditions = new JsonArray();
            conditions.add(condition);
            effect.add("conditions", conditions);
        }
        JsonObject reagent = baseReagent();
        reagent.add("metabolisms", stageWith("effects", arrayOf(effect)));
        return reagent;
    }

    private static JsonObject effectWith(JsonObject effect) {
        JsonObject reagent = baseReagent();
        reagent.add("metabolisms", stageWith("effects", arrayOf(effect)));
        return reagent;
    }

    private static JsonObject effectWithReference(String reference) {
        return effectWith(adjustReagentEffect(reference));
    }

    private static JsonObject adjustReagentEffect(String reference) {
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "AdjustReagent");
        effect.addProperty("reagent", reference);
        effect.addProperty("amount", 1);
        return effect;
    }

    private static JsonObject reagentWithReferences(String reference) {
        JsonObject stage = new JsonObject();
        JsonObject metabolites = new JsonObject();
        metabolites.addProperty(reference, 1);
        stage.add("metabolites", metabolites);

        JsonArray effects = new JsonArray();
        effects.add(adjustReagentEffect(reference));

        JsonObject cleanBloodstream = new JsonObject();
        cleanBloodstream.addProperty("type", "CleanBloodstream");
        cleanBloodstream.addProperty("excluded", reference);
        effects.add(cleanBloodstream);

        JsonObject condition = new JsonObject();
        condition.addProperty("type", "ReagentCondition");
        condition.addProperty("reagent", reference);
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        JsonObject conditionalEffect = new JsonObject();
        conditionalEffect.addProperty("type", "Jitter");
        conditionalEffect.add("conditions", conditions);
        effects.add(conditionalEffect);
        stage.add("effects", effects);

        JsonObject metabolisms = new JsonObject();
        metabolisms.add("bloodstream", stage);
        JsonObject reagent = baseReagent();
        reagent.add("metabolisms", metabolisms);
        return reagent;
    }

    private static JsonObject baseReagent() {
        JsonObject reagent = new JsonObject();
        reagent.addProperty("id", FIXTURE_ID.getPath());
        return reagent;
    }

    private static JsonObject stageWith(String field, JsonArray value) {
        JsonObject stage = new JsonObject();
        stage.add(field, value);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("bloodstream", stage);
        return metabolisms;
    }

    private static JsonArray arrayOf(JsonObject value) {
        JsonArray array = new JsonArray();
        array.add(value);
        return array;
    }

    private static void assertDoesNotThrowAudit(JsonObject reagent) {
        ReagentSchemaAudit.audit(FIXTURE_ID, reagent);
    }

    private static void assertContextualFailure(JsonObject reagent, String path) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReagentSchemaAudit.audit(FIXTURE_ID, reagent));
        assertTrue(exception.getMessage().contains(FIXTURE_ID.toString()), exception.getMessage());
        assertTrue(exception.getMessage().contains(path), exception.getMessage());
    }

    private static void assertReferenceFailure(JsonObject reagent, Map<ResourceLocation, JsonObject> known,
                                               String path) {
        assertReferenceFailure(FIXTURE_ID, reagent, known, path, null);
    }

    private static void assertReferenceFailure(JsonObject reagent, Map<ResourceLocation, JsonObject> known,
                                               String path, String diagnostic) {
        assertReferenceFailure(FIXTURE_ID, reagent, known, path, diagnostic);
    }

    private static void assertReferenceFailure(ResourceLocation source, JsonObject reagent,
                                               Map<ResourceLocation, JsonObject> known, String path) {
        assertReferenceFailure(source, reagent, known, path, null);
    }

    private static void assertReferenceFailure(ResourceLocation source, JsonObject reagent,
                                               Map<ResourceLocation, JsonObject> known,
                                               String path, String diagnostic) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReagentReferenceValidator.validate(source, reagent, known));
        assertTrue(exception.getMessage().contains(source.toString()), exception.getMessage());
        assertTrue(exception.getMessage().contains(path), exception.getMessage());
        if (diagnostic != null) assertTrue(exception.getMessage().contains(diagnostic), exception.getMessage());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static ResourceLocation addonId(String path) {
        return ResourceLocation.fromNamespaceAndPath("addon", path);
    }
}
