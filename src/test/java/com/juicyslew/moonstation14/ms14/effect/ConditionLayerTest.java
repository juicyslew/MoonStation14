package com.juicyslew.moonstation14.ms14.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.ConditionData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.ReagentSchemaAudit;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import com.juicyslew.moonstation14.util.enums.MobStateEnum;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionLayerTest {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            ModReagents.REAGENT_REGISTRY_KEY, id("water"));

    @Test
    void allConditionVariantsRoundTripInversionAndOmitDefault() {
        List<String> sources = List.of(
                "{\"type\":\"ReagentCondition\",\"reagent\":\"water\",\"min\":1,\"max\":2,\"inverted\":true}",
                "{\"type\":\"MobStateCondition\",\"mobstate\":\"alive\",\"inverted\":true}",
                "{\"type\":\"MetabolizerTypeCondition\",\"subtype\":[\"human\"],\"inverted\":true}",
                "{\"type\":\"TemperatureCondition\",\"min\":270,\"max\":310,\"inverted\":true}",
                "{\"type\":\"BreathingCondition\",\"inverted\":true}",
                "{\"type\":\"InternalsCondition\",\"inverted\":true}",
                "{\"type\":\"TagCondition\",\"tag\":\"plant\",\"inverted\":true}",
                "{\"type\":\"HungerCondition\",\"min\":1,\"max\":10,\"inverted\":true}");

        for (String source : sources) {
            ConditionData condition = decode(JsonParser.parseString(source));
            assertTrue(condition.inverted(), condition.type());
            assertEquals(condition, decode(encode(condition)), condition.type());
            assertTrue(encode(condition).get("inverted").getAsBoolean(), condition.type());
        }

        for (String type : List.of("ReagentCondition", "MobStateCondition", "MetabolizerTypeCondition",
                "TemperatureCondition", "BreathingCondition", "InternalsCondition", "TagCondition",
                "HungerCondition")) {
            JsonObject source = switch (type) {
                case "ReagentCondition" -> object("{\"type\":\"ReagentCondition\",\"reagent\":\"water\"}");
                case "MobStateCondition" -> object("{\"type\":\"MobStateCondition\",\"mobstate\":\"alive\"}");
                case "MetabolizerTypeCondition" -> object("{\"type\":\"MetabolizerTypeCondition\",\"subtype\":[]}");
                case "TemperatureCondition" -> object("{\"type\":\"TemperatureCondition\"}");
                case "BreathingCondition" -> object("{\"type\":\"BreathingCondition\"}");
                case "InternalsCondition" -> object("{\"type\":\"InternalsCondition\"}");
                case "TagCondition" -> object("{\"type\":\"TagCondition\",\"tag\":\"plant\"}");
                case "HungerCondition" -> object("{\"type\":\"HungerCondition\"}");
                default -> throw new AssertionError(type);
            };
            assertFalse(encode(decode(source)).has("inverted"), type);
        }
    }

    @Test
    void schemaAuditAcceptsInversionAndRejectsWrongTypeAndUnknownFields() {
        for (String type : List.of("ReagentCondition", "MobStateCondition", "MetabolizerTypeCondition",
                "TemperatureCondition", "BreathingCondition", "InternalsCondition", "TagCondition",
                "HungerCondition")) {
            JsonObject condition = conditionFor(type);
            condition.addProperty("inverted", true);
            assertAudit(condition);

            condition.addProperty("inverted", "true");
            assertThrows(IllegalArgumentException.class, () -> audit(condition));

            condition.remove("inverted");
            condition.addProperty("unknown", false);
            assertThrows(IllegalArgumentException.class, () -> audit(condition));
        }
    }

    @Test
    void evaluatesInclusiveBoundsAndExactCapabilities() {
        ConditionContext context = ConditionContext.builder()
                .sourceReagentQuantities(Map.of(WATER, 2f))
                .mobState(MobStateEnum.CRITICAL)
                .metabolizerTypes(Set.of(MetabolizerTypeEnum.ANIMAL))
                .temperature(300f)
                .hunger(5f)
                .breathing(false)
                .internals(true)
                .tags(Set.of("plant"))
                .build();

        assertTrue(ConditionSystem.evaluate(new ConditionData.ReagentCondition(WATER, 2f, 2f), context));
        assertFalse(ConditionSystem.evaluate(new ConditionData.ReagentCondition(WATER, 1.99f, 0f), context));
        assertTrue(ConditionSystem.evaluate(new ConditionData.MobStateCondition(MobStateEnum.CRITICAL), context));
        assertFalse(ConditionSystem.evaluate(new ConditionData.MobStateCondition(MobStateEnum.ALIVE), context));
        assertTrue(ConditionSystem.evaluate(new ConditionData.MetabolizerTypeCondition(
                List.of(MetabolizerTypeEnum.HUMAN, MetabolizerTypeEnum.ANIMAL), false), context));
        assertTrue(ConditionSystem.evaluate(new ConditionData.TemperatureCondition(300f, 300f), context));
        assertTrue(ConditionSystem.evaluate(new ConditionData.HungerCondition(5f, 5f), context));
        assertFalse(ConditionSystem.evaluate(new ConditionData.BreathingCondition(), context));
        assertTrue(ConditionSystem.evaluate(new ConditionData.InternalsCondition(false), context));
        assertTrue(ConditionSystem.evaluate(new ConditionData.TagCondition("plant"), context));
        assertFalse(ConditionSystem.evaluate(new ConditionData.TagCondition("animal"), context));

        ConditionContext oppositeFlags = ConditionContext.builder()
                .sourceReagentQuantities(Map.of())
                .breathing(true).internals(false).tags(Set.of()).build();
        assertTrue(ConditionSystem.evaluate(new ConditionData.ReagentCondition(WATER, 0f, 0f), oppositeFlags));
        assertTrue(ConditionSystem.evaluate(new ConditionData.BreathingCondition(), oppositeFlags));
        assertFalse(ConditionSystem.evaluate(new ConditionData.InternalsCondition(false), oppositeFlags));
        assertFalse(ConditionSystem.evaluate(new ConditionData.TagCondition("plant"), oppositeFlags));
    }

    @Test
    void unavailableCapabilitiesAreRawFalseAndInvertToTrue() {
        ConditionContext unavailable = ConditionContext.unavailable();
        List<ConditionData> conditions = List.of(
                new ConditionData.ReagentCondition(WATER, 1f, 0f, false),
                new ConditionData.MobStateCondition(MobStateEnum.ALIVE, false),
                new ConditionData.MetabolizerTypeCondition(List.of(MetabolizerTypeEnum.HUMAN), false),
                new ConditionData.TemperatureCondition(1f, 0f, false),
                new ConditionData.BreathingCondition(false),
                new ConditionData.InternalsCondition(false),
                new ConditionData.TagCondition(false, "tag"),
                new ConditionData.HungerCondition(1f, 0f, false));
        for (ConditionData condition : conditions) {
            assertFalse(ConditionSystem.evaluate(condition, unavailable), condition.type());
            assertTrue(ConditionSystem.evaluate(invert(condition), unavailable), condition.type());
        }
    }

    @Test
    void nullAndEmptyPassAndConditionsAndShortCircuit() {
        assertTrue(ConditionSystem.allPass(null, ConditionContext.unavailable()));
        assertTrue(ConditionSystem.allPass(List.of(), ConditionContext.unavailable()));

        ConditionContext context = ConditionContext.builder().breathing(true).build();
        assertFalse(ConditionSystem.allPass(List.of(
                new ConditionData.BreathingCondition(),
                new ConditionData.BreathingCondition(true)), context));
        assertFalse(ConditionSystem.allPass(List.of(
                new ConditionData.ReagentCondition(WATER, 1f, 0f),
                new ConditionData.BreathingCondition(true)), context));

        List<ConditionData> shortCircuiting = new AbstractList<>() {
            @Override
            public ConditionData get(int index) {
                if (index == 0) {
                    return new ConditionData.BreathingCondition(true);
                }
                throw new AssertionError("allPass evaluated a condition after the first failure");
            }

            @Override
            public int size() {
                return 2;
            }
        };
        assertFalse(ConditionSystem.allPass(shortCircuiting, context));
    }

    @Test
    void contextDefensivelyCopiesPresentCollections() {
        java.util.HashMap<ResourceKey<ReagentData>, Float> quantities = new java.util.HashMap<>();
        quantities.put(WATER, 1f);
        java.util.HashSet<String> tags = new java.util.HashSet<>();
        tags.add("plant");
        ConditionContext context = ConditionContext.builder()
                .sourceReagentQuantities(quantities).tags(tags).build();
        quantities.clear();
        tags.clear();
        assertTrue(context.sourceReagentQuantities().orElseThrow().containsKey(WATER));
        assertTrue(context.tags().orElseThrow().contains("plant"));
    }

    private static ConditionData invert(ConditionData condition) {
        return switch (condition) {
            case ConditionData.ReagentCondition value -> new ConditionData.ReagentCondition(
                    value.reagent(), value.max(), value.min(), true);
            case ConditionData.MobStateCondition value -> new ConditionData.MobStateCondition(value.mobState(), true);
            case ConditionData.MetabolizerTypeCondition value -> new ConditionData.MetabolizerTypeCondition(
                    value.metabolizerType(), true);
            case ConditionData.TemperatureCondition value -> new ConditionData.TemperatureCondition(
                    value.max(), value.min(), true);
            case ConditionData.BreathingCondition ignored -> new ConditionData.BreathingCondition(true);
            case ConditionData.InternalsCondition ignored -> new ConditionData.InternalsCondition(true);
            case ConditionData.TagCondition value -> new ConditionData.TagCondition(true, value.tag());
            case ConditionData.HungerCondition value -> new ConditionData.HungerCondition(
                    value.max(), value.min(), true);
        };
    }

    private static ConditionData decode(com.google.gson.JsonElement json) {
        return ConditionData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static JsonObject encode(ConditionData condition) {
        return ConditionData.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow().getAsJsonObject();
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonObject conditionFor(String type) {
        return switch (type) {
            case "ReagentCondition" -> object("{\"type\":\"ReagentCondition\",\"reagent\":\"water\"}");
            case "MobStateCondition" -> object("{\"type\":\"MobStateCondition\",\"mobstate\":\"alive\"}");
            case "MetabolizerTypeCondition" -> object("{\"type\":\"MetabolizerTypeCondition\",\"subtype\":[]}");
            case "TemperatureCondition", "HungerCondition" -> object("{\"type\":\"" + type + "\"}");
            case "BreathingCondition", "InternalsCondition" -> object("{\"type\":\"" + type + "\"}");
            case "TagCondition" -> object("{\"type\":\"TagCondition\",\"tag\":\"plant\"}");
            default -> throw new AssertionError(type);
        };
    }

    private static void assertAudit(JsonObject condition) {
        audit(condition);
    }

    private static void audit(JsonObject condition) {
        JsonObject reagent = new JsonObject();
        reagent.addProperty("id", "fixture");
        JsonObject stage = new JsonObject();
        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "Jitter");
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        effect.add("conditions", conditions);
        effects.add(effect);
        stage.add("effects", effects);
        JsonObject metabolisms = new JsonObject();
        metabolisms.add("bloodstream", stage);
        reagent.add("metabolisms", metabolisms);
        ReagentSchemaAudit.audit(id("fixture"), reagent);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
