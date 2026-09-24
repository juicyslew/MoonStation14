package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectDuration;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectDataCodecTest {
    private static final List<String> ALL_EFFECTS = List.of(
            "{\"type\":\"EvenHealthChange\",\"damage\":{}}",
            "{\"type\":\"HealthChange\",\"damage\":{\"types\":{}}}",
            "{\"type\":\"Vomit\"}",
            "{\"type\":\"Jitter\"}",
            "{\"type\":\"Drunk\"}",
            "{\"type\":\"ModifyBleed\",\"amount\":1}",
            "{\"type\":\"Oxygenate\"}",
            "{\"type\":\"ModifyLungGas\",\"ratios\":{}}",
            "{\"type\":\"AdjustAlert\",\"alerttype\":\"test\",\"clear\":false,\"time\":1}",
            "{\"type\":\"SatiateHunger\"}",
            "{\"type\":\"ModifyBloodLevel\",\"amount\":1}",
            "{\"type\":\"SatiateThirst\"}",
            "{\"type\":\"PopupMessage\",\"subtype\":\"local\",\"messages\":[\"test\"]}",
            "{\"type\":\"Emote\",\"emote\":\"cough\"}",
            "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Test\"}",
            "{\"type\":\"AdjustReagent\",\"reagent\":\"water\",\"amount\":1}",
            "{\"type\":\"CureZombieInfection\"}",
            "{\"type\":\"ArtifactDurabilityRestore\"}",
            "{\"type\":\"ArtifactUnlock\"}",
            "{\"type\":\"GenericStatusEffect\",\"key\":\"Test\"}",
            "{\"type\":\"Flammable\"}",
            "{\"type\":\"Ignite\"}",
            "{\"type\":\"AdjustTemperature\",\"amount\":1}",
            "{\"type\":\"Extinguish\"}",
            "{\"type\":\"MovementSpeedModifier\",\"walkspeedmodifier\":1,\"sprintspeedmodifier\":1}",
            "{\"type\":\"CleanBloodstream\",\"excluded\":\"water\",\"cleanserate\":1}",
            "{\"type\":\"MakeSentient\"}",
            "{\"type\":\"Polymorph\",\"prototype\":\"Test\"}",
            "{\"type\":\"ResetNarcolepsy\"}",
            "{\"type\":\"ModifyKnockdown\"}",
            "{\"type\":\"Electrocute\"}",
            "{\"type\":\"EyeDamage\"}",
            "{\"type\":\"ReduceRotting\",\"seconds\":1}",
            "{\"type\":\"CauseZombieInfection\"}"
    );

    @Test
    void roundTripsNonDefaultCommonFieldsForEveryEffectVariant() {
        assertEquals(34, ALL_EFFECTS.size());

        for (String source : ALL_EFFECTS) {
            JsonObject json = JsonParser.parseString(source).getAsJsonObject();
            json.add("conditions", JsonParser.parseString("[{\"type\":\"BreathingCondition\"}]"));
            json.addProperty("probability", 0.25f);
            json.addProperty("minscale", 0.5f);
            json.addProperty("scaling", false);

            EffectData decoded = decode(json);
            assertEquals(1, decoded.conditions().size(), decoded.type());
            assertEquals(0.25f, decoded.probability(), decoded.type());
            assertEquals(0.5f, decoded.minScale(), decoded.type());
            assertFalse(decoded.scaling(), decoded.type());

            JsonObject encoded = encode(decoded);
            assertEquals(json.get("conditions"), encoded.get("conditions"), decoded.type());
            assertEquals(0.25f, encoded.get("probability").getAsFloat(), decoded.type());
            assertEquals(0.5f, encoded.get("minscale").getAsFloat(), decoded.type());
            assertFalse(encoded.get("scaling").getAsBoolean(), decoded.type());
            assertEquals(decoded, decode(encoded), decoded.type());
        }
    }

    @Test
    void appliesAndOmitsCommonDefaults() {
        EffectData decoded = decode(JsonParser.parseString("{\"type\":\"Jitter\"}"));
        assertEquals(EffectCommonData.DEFAULT, decoded.common());

        JsonObject encoded = encode(decoded);
        assertFalse(encoded.has("conditions"));
        assertFalse(encoded.has("probability"));
        assertFalse(encoded.has("minscale"));
        assertFalse(encoded.has("scaling"));
    }

    @Test
    void oxygenateKeepsItsOwnDispatchType() {
        EffectData decoded = decode(JsonParser.parseString(
                "{\"type\":\"Oxygenate\",\"factor\":1.5}"));
        EffectData.Oxygenate oxygenate = assertInstanceOf(EffectData.Oxygenate.class, decoded);
        assertEquals(1.5f, oxygenate.factor());
        assertEquals("Oxygenate", encode(oxygenate).get("type").getAsString());
    }

    @Test
    void roundTripsZombieInoculationAndCanonicalResistanceField() {
        EffectData.CureZombieInfection cure = assertInstanceOf(
                EffectData.CureZombieInfection.class,
                decode(JsonParser.parseString(
                        "{\"type\":\"CureZombieInfection\",\"innoculate\":true}")));
        assertTrue(cure.innoculate());
        assertTrue(encode(cure).get("innoculate").getAsBoolean());

        JsonObject health = encode(decode(JsonParser.parseString(
                "{\"type\":\"HealthChange\",\"ignoreresistances\":false,"
                        + "\"damage\":{\"types\":{}}}")));
        assertTrue(health.has("ignoreresistances"));
        assertFalse(health.has("ignoreResistances"));
    }

    @Test
    void healthChangeAllowsOmittedDamageTypesAndRejectsStructuralDamage() {
        EffectData.HealthChange empty = assertInstanceOf(EffectData.HealthChange.class,
                decode(JsonParser.parseString(
                        "{\"type\":\"HealthChange\",\"damage\":{}}")));
        assertTrue(empty.damage().types().isEmpty());
        assertEquals(empty, decode(encode(empty)));

        assertThrows(IllegalStateException.class, () -> decode(JsonParser.parseString(
                "{\"type\":\"HealthChange\",\"damage\":{\"types\":{\"structural\":1}}}")));
    }

    @Test
    void collectionComponentsAreDefensivelyCopiedAndRejectNulls() {
        List<com.juicyslew.moonstation14.component.codec.json.ConditionData> conditions = new ArrayList<>();
        EffectCommonData common = new EffectCommonData(conditions, 1f, 0f, true);
        conditions.add(new com.juicyslew.moonstation14.component.codec.json.ConditionData.BreathingCondition());
        assertTrue(common.conditions().isEmpty());
        assertThrows(NullPointerException.class, () -> new EffectCommonData(null, 1f, 0f, true));

        Map<String, Float> damage = new HashMap<>();
        damage.put("brute", 1f);
        EffectData.EvenHealthChange even = new EffectData.EvenHealthChange(common, true, damage);
        damage.put("burn", 2f);
        assertEquals(Map.of("brute", 1f), even.damage());
        assertThrows(NullPointerException.class,
                () -> new EffectData.EvenHealthChange(common, true, null));

        Map<String, Float> types = new HashMap<>();
        types.put("blunt", 1f);
        com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData specifier =
                new com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData(types);
        types.put("burn", 2f);
        assertEquals(Map.of("blunt", 1f), specifier.types());
        assertThrows(NullPointerException.class,
                () -> new com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData(null));

        List<String> messages = new ArrayList<>(List.of("hello"));
        EffectData.PopupMessage popup = new EffectData.PopupMessage(common,
                EffectData.PopupRecipients.Local, EffectData.PopupMethod.PopupEntity,
                EffectData.PopupVisualType.Small, messages);
        messages.add("later");
        assertEquals(List.of("hello"), popup.messages());
        assertThrows(NullPointerException.class,
                () -> new EffectData.PopupMessage(common, EffectData.PopupRecipients.Local,
                        EffectData.PopupMethod.PopupEntity, EffectData.PopupVisualType.Small, null));
        List<String> nullMessage = new ArrayList<>();
        nullMessage.add(null);
        assertThrows(NullPointerException.class,
                () -> new EffectData.PopupMessage(common, EffectData.PopupRecipients.Local,
                        EffectData.PopupMethod.PopupEntity, EffectData.PopupVisualType.Small, nullMessage));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectData.PopupMessage(common, EffectData.PopupRecipients.Local,
                        EffectData.PopupMethod.PopupEntity, EffectData.PopupVisualType.Small, List.of(" ")));
    }

    @Test
    void scopedVariantsUsePinnedDefaults() {
        EffectData.HealthChange health = assertInstanceOf(EffectData.HealthChange.class,
                decode(JsonParser.parseString("{\"type\":\"HealthChange\",\"damage\":{\"types\":{\"blunt\":1}}}")));
        assertTrue(health.ignoreResistances());
        EffectData.SatiateHunger hunger = assertInstanceOf(EffectData.SatiateHunger.class,
                decode(JsonParser.parseString("{\"type\":\"SatiateHunger\"}")));
        assertEquals(1.5f, hunger.factor());
        assertEquals(3f, ((EffectData.Drunk) decode(JsonParser.parseString(
                "{\"type\":\"Drunk\"}"))).boozePower());
        EffectData.Jitter jitter = (EffectData.Jitter) decode(JsonParser.parseString(
                "{\"type\":\"Jitter\"}"));
        assertEquals(10f, jitter.amplitude());
        assertEquals(4f, jitter.frequency());
        assertEquals(2f, jitter.time());
        assertTrue(jitter.refresh());
        assertEquals(-1.5f, ((EffectData.Extinguish) decode(JsonParser.parseString(
                "{\"type\":\"Extinguish\"}"))).fireStacksAdjustment());
        assertEquals(5, ((EffectData.Electrocute) decode(JsonParser.parseString(
                "{\"type\":\"Electrocute\"}"))).shockDamage());
        EffectData.Electrocute electrocute = (EffectData.Electrocute) decode(JsonParser.parseString(
                "{\"type\":\"Electrocute\"}"));
        assertEquals(2f, electrocute.electrocuteTime());
        assertTrue(electrocute.refresh());
        assertTrue(electrocute.bypassInsulation());
        assertEquals(1f, electrocute.siemensCoefficient());
        assertEquals(-1, ((EffectData.EyeDamage) decode(JsonParser.parseString(
                "{\"type\":\"EyeDamage\"}"))).amount());
    }

    @Test
    void satiateThirstUsesPinnedFiniteFactorDefaultAndRoundTrips() {
        EffectData.SatiateThirst omitted = assertInstanceOf(EffectData.SatiateThirst.class,
                decode(JsonParser.parseString("{\"type\":\"SatiateThirst\"}")));
        assertEquals(1.5f, omitted.factor());
        JsonObject omittedEncoded = encode(omitted);
        assertFalse(omittedEncoded.has("factor"));
        assertEquals(omitted, decode(omittedEncoded));

        EffectData.SatiateThirst explicit = assertInstanceOf(EffectData.SatiateThirst.class,
                decode(JsonParser.parseString("{\"type\":\"SatiateThirst\",\"factor\":2.25}")));
        assertEquals(2.25f, explicit.factor());
        assertEquals(explicit, decode(encode(explicit)));

        JsonObject nonFinite = JsonParser.parseString(
                "{\"type\":\"SatiateThirst\"}").getAsJsonObject();
        nonFinite.add("factor", new com.google.gson.JsonPrimitive(Float.POSITIVE_INFINITY));
        assertTrue(EffectData.CODEC.parse(JsonOps.INSTANCE, nonFinite).error().isPresent());
    }

    @Test
    void scopedVariantsRoundTripAllNonDefaultFields() {
        List<String> sources = List.of(
                "{\"type\":\"HealthChange\",\"ignoreresistances\":false,\"damage\":{\"types\":{\"piercing\":-2.5}}}",
                "{\"type\":\"EvenHealthChange\",\"ignoreresistances\":false,\"damage\":{\"brute\":-1,\"burn\":2}}",
                "{\"type\":\"AdjustReagent\",\"reagent\":\"water\",\"amount\":-2.5}",
                "{\"type\":\"SatiateHunger\",\"factor\":-2.5}",
                "{\"type\":\"PopupMessage\",\"subtype\":\"pvs\",\"method\":\"popupcoordinates\",\"visualtype\":\"largecaution\",\"messages\":[\"a\",\"b\"]}",
                "{\"type\":\"Emote\",\"emote\":\"scream\",\"showinguidebook\":true,\"showinchat\":true,\"force\":true}",
                "{\"type\":\"Jitter\",\"amplitude\":12,\"frequency\":7,\"time\":3.5,\"refresh\":false}",
                "{\"type\":\"Drunk\",\"boozepower\":8}",
                "{\"type\":\"Flammable\",\"multiplier\":-0.2,\"multiplieronexisting\":0.4}",
                "{\"type\":\"Ignite\"}",
                "{\"type\":\"Extinguish\",\"firestacksadjustment\":2.5}",
                "{\"type\":\"Electrocute\",\"electrocutetime\":3.5,\"shockdamage\":7,\"refresh\":false,\"bypassinsulation\":false,\"siemenscoefficient\":0.25}",
                "{\"type\":\"EyeDamage\",\"amount\":-7}",
                "{\"type\":\"AdjustAlert\",\"alerttype\":\"test\",\"clear\":true,\"showcooldown\":true,\"time\":4.5}"
        );
        for (String source : sources) {
            EffectData decoded = decode(JsonParser.parseString(source));
            assertEquals(decoded, decode(encode(decoded)), decoded.type());
        }
    }

    @Test
    void scopedCodecsRejectInvalidSemanticValues() {
        assertCodecRejects("{\"type\":\"HealthChange\",\"damage\":{\"types\":{\"unknown\":1}}}");
        assertCodecRejects("{\"type\":\"EvenHealthChange\",\"damage\":{\"unknown\":1}}");
        assertCodecRejects("{\"type\":\"Jitter\",\"amplitude\":-1}");
        assertCodecRejects("{\"type\":\"Electrocute\",\"shockdamage\":1.5}");
        assertCodecRejects("{\"type\":\"Electrocute\",\"siemenscoefficient\":-1}");
        assertCodecRejects("{\"type\":\"EyeDamage\",\"amount\":1.5}");
        assertCodecRejects("{\"type\":\"PopupMessage\",\"subtype\":\"Local\",\"messages\":[\"x\"]}");
        assertCodecRejects("{\"type\":\"PopupMessage\",\"messages\":[]}");
        assertCodecRejects("{\"type\":\"PopupMessage\",\"messages\":[\" \"]}");
        assertCodecRejects("{\"type\":\"Emote\",\"emote\":\"  \"}");
        assertCodecRejects("{\"type\":\"AdjustAlert\",\"alerttype\":\" \"}");
        JsonObject nonFinite = JsonParser.parseString(
                "{\"type\":\"Jitter\",\"amplitude\":1}").getAsJsonObject();
        nonFinite.add("time", new com.google.gson.JsonPrimitive(Float.NaN));
        assertTrue(EffectData.CODEC.parse(JsonOps.INSTANCE, nonFinite).error().isPresent());
    }

    @Test
    void statusDerivedEffectDefaultsMatchTheSchema() {
        EffectData.ModifyStatusEffect modify = assertInstanceOf(EffectData.ModifyStatusEffect.class,
                decode(JsonParser.parseString("{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\"}")));
        assertEquals(2f, modify.time().requireFiniteSeconds());
        assertFalse(modify.time().isPermanent());
        assertEquals(StatusEffectOperation.UPDATE, modify.subType());
        assertEquals(0f, modify.delay());

        EffectData.GenericStatusEffect generic = assertInstanceOf(EffectData.GenericStatusEffect.class,
                decode(JsonParser.parseString("{\"type\":\"GenericStatusEffect\",\"key\":\"legacy\"}")));
        assertEquals("", generic.component());
        assertEquals(2f, generic.time());
        assertEquals(StatusEffectOperation.UPDATE, generic.subType());

        EffectData.MovementSpeedModifier movement = assertInstanceOf(EffectData.MovementSpeedModifier.class,
                decode(JsonParser.parseString("{\"type\":\"MovementSpeedModifier\"}")));
        assertEquals(1f, movement.walkSpeedModifier());
        assertEquals(1f, movement.sprintSpeedModifier());
        assertEquals("ReagentSpeedStatusEffect", movement.effectProto());
        assertEquals(2f, movement.time().requireFiniteSeconds());
        assertEquals(StatusEffectOperation.UPDATE, movement.subType());
        assertEquals(0f, movement.delay());

        EffectData.ModifyKnockdown knockdown = assertInstanceOf(EffectData.ModifyKnockdown.class,
                decode(JsonParser.parseString("{\"type\":\"ModifyKnockdown\"}")));
        assertEquals(2f, knockdown.time().requireFiniteSeconds());
        assertEquals(StatusEffectOperation.UPDATE, knockdown.subType());
        assertEquals(0f, knockdown.delay());
        assertFalse(knockdown.crawling());
        assertFalse(knockdown.drop());
    }

    @Test
    void statusDerivedEffectFullValuesRoundTrip() {
        List<String> sources = List.of(
                "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"time\":7.5,\"subtype\":\"add\",\"delay\":0.25}",
                "{\"type\":\"GenericStatusEffect\",\"key\":\"legacy\",\"component\":\"Marker\",\"subtype\":\"remove\",\"time\":4.5}",
                "{\"type\":\"MovementSpeedModifier\",\"walkspeedmodifier\":0.5,\"sprintspeedmodifier\":1.75,\"effectproto\":\"CustomSpeed\",\"time\":9,\"subtype\":\"set\",\"delay\":1.25}",
                "{\"type\":\"ModifyKnockdown\",\"time\":3,\"subtype\":\"remove\",\"delay\":0.5,\"crawling\":true,\"drop\":true}"
        );

        for (String source : sources) {
            EffectData decoded = decode(JsonParser.parseString(source));
            assertEquals(decoded, decode(encode(decoded)), decoded.type());
        }
    }

    @Test
    void nullableStatusDurationsPreservePermanentAndOmittedSemantics() {
        assertTrue(StatusEffectDuration.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonNull.INSTANCE).getOrThrow().isPermanent());
        List<String> types = List.of("ModifyStatusEffect", "MovementSpeedModifier", "ModifyKnockdown");
        for (String type : types) {
            JsonObject omitted = JsonParser.parseString(switch (type) {
                case "ModifyStatusEffect" -> "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\"}";
                case "MovementSpeedModifier" -> "{\"type\":\"MovementSpeedModifier\"}";
                default -> "{\"type\":\"ModifyKnockdown\"}";
            }).getAsJsonObject();
            EffectData omittedValue = decode(omitted);
            assertEquals(2f, duration(omittedValue).requireFiniteSeconds(), type);
            assertFalse(duration(omittedValue).isPermanent(), type);

            JsonObject permanent = omitted.deepCopy();
            permanent.add("time", com.google.gson.JsonNull.INSTANCE);
            EffectData permanentValue = decode(permanent);
            assertTrue(duration(permanentValue).isPermanent(), type);
            JsonObject encoded = encode(permanentValue);
            assertTrue(encoded.has("time"), type);
            assertTrue(encoded.get("time").isJsonNull(), type);
            assertEquals(permanentValue, decode(encoded), type);
        }
    }

    @Test
    void statusOperationsAreClosedAndTyped() {
        for (String operation : List.of("update", "add", "remove", "set")) {
            EffectData.ModifyStatusEffect effect = assertInstanceOf(EffectData.ModifyStatusEffect.class,
                    decode(JsonParser.parseString("{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"subtype\":\""
                            + operation + "\"}")));
            assertEquals(operation, effect.subType().serializedName());
            assertEquals(effect, decode(encode(effect)));
        }
        for (String invalid : List.of("UPDATE", "Add", "replace", "")) {
            assertCodecRejects("{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"subtype\":\""
                    + invalid + "\"}");
        }
    }

    @Test
    void statusDerivedEffectsRejectInvalidValuesAndTypes() {
        for (String source : List.of(
                "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\" \"}",
                "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"time\":-1}",
                "{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"delay\":-1}",
                "{\"type\":\"GenericStatusEffect\",\"key\":\" \"}",
                "{\"type\":\"GenericStatusEffect\",\"key\":\"legacy\",\"time\":-1}",
                "{\"type\":\"MovementSpeedModifier\",\"walkspeedmodifier\":-1}",
                "{\"type\":\"MovementSpeedModifier\",\"sprintspeedmodifier\":-1}",
                "{\"type\":\"MovementSpeedModifier\",\"effectproto\":\" \"}",
                "{\"type\":\"MovementSpeedModifier\",\"delay\":-1}",
                "{\"type\":\"ModifyKnockdown\",\"time\":-1}",
                "{\"type\":\"ModifyKnockdown\",\"delay\":-1}",
                "{\"type\":\"ModifyKnockdown\",\"crawling\":1}")) {
            assertCodecRejects(source);
        }

        assertCodecRejects("{\"type\":\"ModifyStatusEffect\",\"effectproto\":1}");
        assertCodecRejects("{\"type\":\"GenericStatusEffect\",\"key\":1}");
        assertCodecRejects("{\"type\":\"MovementSpeedModifier\",\"time\":true}");
        assertCodecRejects("{\"type\":\"ModifyKnockdown\",\"drop\":\"true\"}");
        assertCodecRejects("{\"type\":\"ModifyStatusEffect\",\"effectproto\":\"Jitter\",\"time\":1e100}");
    }

    @Test
    void statusEffectDurationIsDefensiveAndEncodesPermanentAsNull() {
        assertThrows(IllegalArgumentException.class, () -> StatusEffectDuration.finite(-1f));
        assertThrows(IllegalArgumentException.class, () -> StatusEffectDuration.finite(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new StatusEffectDuration(true, 1f));
        assertThrows(IllegalStateException.class, () -> StatusEffectDuration.permanent().requireFiniteSeconds());
        assertTrue(StatusEffectDuration.CODEC.encodeStart(JsonOps.INSTANCE, StatusEffectDuration.permanent())
                .getOrThrow().isJsonNull());
        assertEquals(3f, StatusEffectDuration.CODEC.parse(JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive(3f)).getOrThrow().requireFiniteSeconds());
    }

    @Test
    void statusDerivedCanonicalConstructorsRejectInvalidDelayAndNullCommon() {
        for (float delay : new float[]{-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new EffectData.ModifyStatusEffect(
                    EffectCommonData.DEFAULT, "Jitter", StatusEffectDuration.DEFAULT,
                    StatusEffectOperation.UPDATE, delay));
        }

        assertThrows(NullPointerException.class, () -> new EffectData.ModifyStatusEffect(
                null, "Jitter", StatusEffectDuration.DEFAULT, StatusEffectOperation.UPDATE, 0f));
        assertThrows(NullPointerException.class, () -> new EffectData.GenericStatusEffect(
                (EffectCommonData) null, "legacy", "", StatusEffectOperation.UPDATE, 2f));
        assertThrows(NullPointerException.class, () -> new EffectData.MovementSpeedModifier(
                null, 1f, 1f, "ReagentSpeedStatusEffect", StatusEffectDuration.DEFAULT,
                StatusEffectOperation.UPDATE, 0f));
        assertThrows(NullPointerException.class, () -> new EffectData.ModifyKnockdown(
                (EffectCommonData) null, StatusEffectDuration.DEFAULT,
                StatusEffectOperation.UPDATE, 0f, false, false));
    }

    private static StatusEffectDuration duration(EffectData effect) {
        return switch (effect) {
            case EffectData.ModifyStatusEffect value -> value.time();
            case EffectData.MovementSpeedModifier value -> value.time();
            case EffectData.ModifyKnockdown value -> value.time();
            default -> throw new AssertionError("not a duration-bearing effect: " + effect.type());
        };
    }

    private static void assertCodecRejects(String source) {
        var result = EffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(source));
        assertTrue(result.error().isPresent(), source);
    }

    private static EffectData decode(JsonElement json) {
        return EffectData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static JsonObject encode(EffectData effect) {
        return EffectData.CODEC.encodeStart(JsonOps.INSTANCE, effect)
                .getOrThrow()
                .getAsJsonObject();
    }
}
