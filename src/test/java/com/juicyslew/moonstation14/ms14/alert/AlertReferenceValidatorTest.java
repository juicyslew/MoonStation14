package com.juicyslew.moonstation14.ms14.alert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.alert.prototype.AlertReferenceValidator;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertReferenceValidatorTest {
    @Test
    void reportsSourceAndExactPathForMissingAlert() {
        ResourceLocation source = id("sample");
        JsonObject reagent = JsonParser.parseString("""
                {"metabolisms":{"digestion":{"effects":[
                  {"type":"AdjustAlert","alerttype":"missing"}
                ]}}}
                """).getAsJsonObject();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> AlertReferenceValidator.validate(candidate(source, reagent, Map.of())));
        assertTrue(failure.getMessage().contains("moonstation14:sample"));
        assertTrue(failure.getMessage().contains("metabolisms.digestion.effects[0].alerttype"));
        assertTrue(failure.getMessage().contains("unresolved alert reference"));
    }

    @Test
    void rejectsCaseDriftButAcceptsUnqualifiedCanonicalReference() {
        JsonObject reagent = JsonParser.parseString("""
                {"metabolisms":{"digestion":{"effects":[
                  {"type":"AdjustAlert","alerttype":"toxins"}
                ]}}}
                """).getAsJsonObject();
        Map<ResourceLocation, JsonObject> alerts = Map.of(id("toxins"), new JsonObject());
        AlertReferenceValidator.validate(candidate(id("sample"), reagent, alerts));

        reagent.getAsJsonObject("metabolisms").getAsJsonObject("digestion")
                .getAsJsonArray("effects").get(0).getAsJsonObject()
                .addProperty("alerttype", "Toxins");
        assertThrows(IllegalArgumentException.class,
                () -> AlertReferenceValidator.validate(candidate(id("sample"), reagent, alerts)));
    }

    private static Map<ResourceLocation, Map<ResourceLocation, JsonObject>> candidate(
            ResourceLocation source, JsonObject reagent, Map<ResourceLocation, JsonObject> alerts) {
        Map<ResourceLocation, JsonObject> reagents = new LinkedHashMap<>();
        reagents.put(source, reagent);
        return Map.of(ModReagents.REAGENT_TYPE.typeId(), reagents,
                ModAlerts.ALERT_TYPE.typeId(), alerts);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
