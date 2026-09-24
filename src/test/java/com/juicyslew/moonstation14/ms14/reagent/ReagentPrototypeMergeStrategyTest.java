package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReagentPrototypeMergeStrategyTest {
    @Test
    void mergesStagesAndKeepsWholePropertiesPresenceAware() {
        PrototypeCatalog<JsonObject> catalog = resolve(
                entry("base", "{\"metabolisms\":{\"digestion\":{\"effects\":[\"parent-effect\"],\"metabolites\":{\"water\":1},\"rate\":1},\"bloodstream\":{\"rate\":2}}}"),
                entry("child", "{\"parent\":\"base\",\"metabolisms\":{\"digestion\":{\"rate\":3,\"effects\":[],\"extra\":true}}}")
        );
        JsonObject digestion = stage(catalog, "child", "digestion");
        assertEquals(3, digestion.get("rate").getAsInt());
        assertTrue(digestion.getAsJsonArray("effects").isEmpty());
        assertEquals(1, digestion.getAsJsonObject("metabolites").get("water").getAsInt());
        assertTrue(stage(catalog, "child", "bloodstream").has("rate"));
    }

    @Test
    void emptyMetabolismStillInheritsParentStages() {
        PrototypeCatalog<JsonObject> catalog = resolve(
                entry("base", "{\"metabolisms\":{\"digestion\":{\"effects\":[]}}}"),
                entry("child", "{\"parent\":\"base\",\"metabolisms\":{}}")
        );
        assertTrue(stage(catalog, "child", "digestion").has("effects"));
    }

    @Test
    void multipleParentsUseFirstParentStagePropertiesAndChildPropertiesWin() {
        PrototypeCatalog<JsonObject> catalog = resolve(
                entry("first", "{\"metabolisms\":{\"digestion\":{\"rate\":1,\"effects\":[1]}}}"),
                entry("second", "{\"metabolisms\":{\"digestion\":{\"rate\":2,\"metabolites\":{\"salt\":2}},\"respiration\":{\"rate\":4}}}"),
                entry("child", "{\"parent\":[\"first\",\"second\"],\"metabolisms\":{\"digestion\":{\"rate\":3}}}")
        );
        JsonObject digestion = stage(catalog, "child", "digestion");
        assertEquals(3, digestion.get("rate").getAsInt());
        assertEquals(1, digestion.getAsJsonArray("effects").get(0).getAsInt());
        assertEquals(2, digestion.getAsJsonObject("metabolites").get("salt").getAsInt());
        assertTrue(stage(catalog, "child", "respiration").has("rate"));
    }

    @Test
    void rejectsMalformedMetabolismShapesWithFieldContext() {
        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolve(entry("bad", "{\"metabolisms\":{\"digestion\":{\"effects\":{}}}}")));
        assertTrue(exception.getMessage().contains("digestion.effects"));
    }

    private static JsonObject stage(PrototypeCatalog<JsonObject> catalog, String reagent, String stage) {
        return catalog.get(id(reagent)).getAsJsonObject("metabolisms").getAsJsonObject(stage);
    }

    @SafeVarargs
    private static PrototypeCatalog<JsonObject> resolve(Map.Entry<String, String>... entries) {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            raw.put(id(entry.getKey()), JsonParser.parseString(entry.getValue()).getAsJsonObject());
        }
        return PrototypeResolver.resolve(raw, new ReagentPrototypeMergeStrategy());
    }

    private static Map.Entry<String, String> entry(String id, String json) {
        return Map.entry(id, json);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", value);
    }
}
