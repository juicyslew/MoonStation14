package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentSchemaAudit;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReagentCodecSchemaTest {
    @Test
    void oxygenateRoundTripsAndKeepsCommonFields() {
        JsonObject json = JsonParser.parseString("{\"type\":\"Oxygenate\",\"conditions\":[],\"probability\":0.25,\"minscale\":0.4,\"scaling\":false,\"factor\":2}").getAsJsonObject();
        EffectData value = EffectData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("Oxygenate", value.type());
        assertEquals(0.25f, value.probability());
        assertEquals(0.4f, value.minScale());
        assertFalse(value.scaling());
        var encoded = EffectData.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, EffectData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void innoculateAndPreviouslyUnmodeledEmoteFieldsAreRetained() {
        EffectData cure = EffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"CureZombieInfection\",\"innoculate\":true}")).getOrThrow();
        assertTrue(((EffectData.CureZombieInfection) cure).innoculate());
        EffectData emote = EffectData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"Emote\",\"emote\":\"cough\",\"force\":true,\"showinchat\":true}")).getOrThrow();
        assertTrue(((EffectData.Emote) emote).force());
        assertTrue(((EffectData.Emote) emote).showInChat());
    }

    @Test
    void strictAuditRejectsCamelCaseAndAcceptsCanonicalMetabolismRate() {
        JsonObject bad = JsonParser.parseString("{\"id\":\"test\",\"metabolisms\":{\"bloodstream\":{\"metabolismRate\":1}}}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(ResourceLocation.fromNamespaceAndPath("moonstation14", "test"), bad));
        JsonObject good = JsonParser.parseString("{\"id\":\"test\",\"metabolisms\":{\"bloodstream\":{\"metabolismrate\":1}}}").getAsJsonObject();
        assertDoesNotThrow(() -> ReagentSchemaAudit.audit(ResourceLocation.fromNamespaceAndPath("moonstation14", "test"), good));
    }

    @Test
    void strictAuditRejectsUnknownStageAndInvalidExplicitAmountsWithContext() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("moonstation14", "test");
        assertAuditRejects(id, "{\"id\":\"test\",\"metabolisms\":{\"anatomy\":{}}}", "metabolisms.anatomy");
        assertAuditRejects(id, "{\"id\":\"test\",\"metabolisms\":{\"digestion\":{\"metabolismrate\":0}}}", "metabolismrate");
        assertAuditRejects(id, "{\"id\":\"test\",\"metabolisms\":{\"digestion\":{\"metabolismrate\":-1}}}", "metabolismrate");
        assertAuditRejects(id, "{\"id\":\"test\",\"metabolisms\":{\"digestion\":{\"metabolites\":{\"water\":-1}}}}", "metabolites.water");
    }

    @Test
    void metabolismConstructorAndCodecRejectNonFiniteValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new MetabolismData(java.util.List.of(), Map.of(), Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new MetabolismData(java.util.List.of(), Map.of(), Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new MetabolismData(java.util.List.of(), Map.of(), Float.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new MetabolismData(java.util.List.of(), Map.of(
                        com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), Float.NaN), 1f));
        assertThrows(IllegalArgumentException.class,
                () -> new MetabolismData(java.util.List.of(), Map.of(
                        com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), Float.POSITIVE_INFINITY), 1f));

        JsonObject nanRate = JsonParser.parseString("{\"effects\":[],\"metabolismrate\":\"NaN\"}").getAsJsonObject();
        assertTrue(MetabolismData.CODEC.parse(JsonOps.INSTANCE, nanRate).error().isPresent());
        JsonObject infinityRatio = JsonParser.parseString("{\"metabolites\":{\"water\":\"Infinity\"}}").getAsJsonObject();
        assertTrue(MetabolismData.CODEC.parse(JsonOps.INSTANCE, infinityRatio).error().isPresent());
        JsonObject nonFiniteRate = new JsonObject();
        nonFiniteRate.add("metabolismrate", new com.google.gson.JsonPrimitive(Float.NaN));
        assertTrue(MetabolismData.CODEC.parse(JsonOps.INSTANCE, nonFiniteRate).error().isPresent());
        JsonObject nonFiniteRatio = new JsonObject();
        JsonObject ratios = new JsonObject();
        ratios.add("water", new com.google.gson.JsonPrimitive(Float.POSITIVE_INFINITY));
        nonFiniteRatio.add("metabolites", ratios);
        assertTrue(MetabolismData.CODEC.parse(JsonOps.INSTANCE, nonFiniteRatio).error().isPresent());
    }

    @Test
    void metabolismStageCodecRemainsClosedAndCanonical() {
        assertEquals("digestion", MetabolismStage.DIGESTION.serializedName());
        assertTrue(MetabolismStage.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"anatomy\"")).error().isPresent());
    }

    @Test
    void auditAcceptsCanonicalScopedFieldsAndRejectsUnknownEffectFields() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("moonstation14", "test");
        JsonObject reagent = JsonParser.parseString("""
                {"id":"test","metabolisms":{"digestion":{"effects":[
                  {"type":"Jitter","amplitude":1,"frequency":2,"time":3,"refresh":false},
                  {"type":"PopupMessage","subtype":"local","method":"popupentity","visualtype":"small","messages":["hello"]},
                  {"type":"Electrocute","electrocutetime":2,"shockdamage":5,"refresh":true,"bypassinsulation":true,"siemenscoefficient":1},
                  {"type":"AdjustAlert","alerttype":"test","clear":false,"showcooldown":false,"time":0}
                ]}}}
                """).getAsJsonObject();
        assertDoesNotThrow(() -> ReagentSchemaAudit.audit(id, reagent));
        reagent.getAsJsonObject("metabolisms").getAsJsonObject("digestion")
                .getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("amplitudeTypo", 1);
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(id, reagent));
    }

    @Test
    void auditKeepsDamageCodecShapeAtomic() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("moonstation14", "test");
        JsonObject emptyDamage = JsonParser.parseString("""
                {"id":"test","metabolisms":{"digestion":{"effects":[
                  {"type":"HealthChange","damage":{}}
                ]}}}
                """).getAsJsonObject();
        assertDoesNotThrow(() -> ReagentSchemaAudit.audit(id, emptyDamage));

        JsonObject structural = JsonParser.parseString("""
                {"id":"test","metabolisms":{"digestion":{"effects":[
                  {"type":"HealthChange","damage":{"types":{"structural":1}}}
                ]}}}
                """).getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(id, structural));

        JsonObject unknown = JsonParser.parseString("""
                {"id":"test","metabolisms":{"digestion":{"effects":[
                  {"type":"HealthChange","damage":{"typo":1}}
                ]}}}
                """).getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ReagentSchemaAudit.audit(id, unknown));
    }

    private static void assertAuditRejects(ResourceLocation id, String json, String context) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReagentSchemaAudit.audit(id, JsonParser.parseString(json).getAsJsonObject()));
        assertTrue(exception.getMessage().contains(context), exception.getMessage());
    }
}
