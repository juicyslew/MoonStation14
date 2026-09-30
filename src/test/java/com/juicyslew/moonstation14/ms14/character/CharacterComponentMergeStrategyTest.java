package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CharacterComponentMergeStrategyTest {
    private static final String FIRST = "[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":0.5,\"heat\":0.1}},\"maxDamage\":200}]";
    private static final String SECOND = "[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"slash\":0.2}},\"maxDamage\":300}]";
    private static final String BLOODSTREAM = "{\"type\":\"Bloodstream\",\"reference_solution\":{\"moonstation14:blood\":300},"
            + "\"metabolism_exclusions\":[\"moonstation14:blood\"],\"max_volume_modifier\":2,\"update_interval_seconds\":3,"
            + "\"bleed_decay_per_update\":0.33,\"max_bleed_rate\":10,"
            + "\"damage_bleed_multipliers\":{\"blunt\":0.08,\"piercing\":0.2,\"slash\":0.25,\"heat\":-0.5},"
            + "\"blood_refresh_per_update\":1,\"bloodloss_threshold_fraction\":0.9,"
            + "\"bloodloss_damage_per_update\":{\"bloodloss\":0.5},\"bloodloss_heal_per_update\":{\"bloodloss\":1},"
            + "\"bloodloss_ignore_resistances\":false,\"bleed_puddle_threshold\":1}";
    private static final String SULFUR_BLOODSTREAM = BLOODSTREAM.replace(
            "\"moonstation14:blood\":300", "\"moonstation14:sulfurblood\":150")
            .replace("[\"moonstation14:blood\"]", "[\"moonstation14:sulfurblood\"]");

    @Test
    void handsFragmentsReplaceOrderedListAndRejectLegacyOnAbstractParents() {
        var resolved = resolve(
                entry("base", "{\"abstract\":true,\"components\":[{\"type\":\"Hands\",\"hands\":[\"left\",\"right\"]}]}"),
                entry("patch", "{\"parent\":\"base\",\"components\":[{\"type\":\"Hands\",\"hands\":[\"third\",\"first\"]}]}"),
                entry("inherited", "{\"parent\":\"base\"}"));
        assertEquals(json("{\"type\":\"Hands\",\"hands\":[\"third\",\"first\"]}"), component(resolved.get(id("patch"))));
        assertEquals(json("{\"type\":\"Hands\",\"hands\":[\"left\",\"right\"]}"), component(resolved.get(id("inherited"))));
        for (String invalid : List.of("null", "[1]", "[\"left\",\"left\"]", "[\" \"]",
                "[\"" + "x".repeat(65) + "\"]", "[" + String.join(",", java.util.Collections.nCopies(17, "\"x\"")) + "]")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> resolve(
                    entry("bad", "{\"abstract\":true,\"components\":[{\"type\":\"Hands\",\"hands\":" + invalid + "}]}")));
            assertTrue(failure.getMessage().contains("$.components[0].hands"), failure.getMessage());
        }
        var legacy = assertThrows(IllegalArgumentException.class, () -> resolve(
                entry("bad", "{\"abstract\":true,\"hands\":[\"left\"]}")));
        assertTrue(legacy.getMessage().contains("$.hands"), legacy.getMessage());
    }

    @Test
    void mixedTypesPutChildBloodstreamBeforeInheritedBarotraumaAndKeepParentOrder() {
        var resolved = resolve(
                entry("pressure", "{\"abstract\":true,\"components\":" + FIRST + "}"),
                entry("blood", "{\"abstract\":true,\"components\":[" + BLOODSTREAM + "]}"),
                entry("child", "{\"parent\":[\"pressure\",\"blood\"],\"components\":[{\"type\":\"Bloodstream\",\"max_bleed_rate\":7}]}"),
                entry("reversed", "{\"parent\":[\"blood\",\"pressure\"]}"),
                entry("omitted", "{\"parent\":[\"pressure\",\"blood\"]}"));

        JsonObject child = resolved.get(id("child"));
        assertComponentTypes(child, "Bloodstream", "Barotrauma");
        JsonObject blood = child.getAsJsonArray("components").get(0).getAsJsonObject();
        assertEquals(7, blood.get("max_bleed_rate").getAsInt());
        assertEquals(json("{\"moonstation14:blood\":300}"), blood.getAsJsonObject("reference_solution"));
        assertEquals(3, blood.get("update_interval_seconds").getAsInt());
        assertInstanceOf(BloodstreamComponent.class, CharacterComponentRegistry.CODEC.parse(JsonOps.INSTANCE, blood).getOrThrow());
        assertEquals(JsonParser.parseString(FIRST).getAsJsonArray().get(0), child.getAsJsonArray("components").get(1));

        assertComponentTypes(resolved.get(id("reversed")), "Bloodstream", "Barotrauma");
        assertComponentTypes(resolved.get(id("omitted")), "Barotrauma", "Bloodstream");
        assertEquals(JsonParser.parseString(FIRST).getAsJsonArray().get(0), resolved.get(id("omitted")).getAsJsonArray("components").get(0));
    }

    @Test
    void mixedParentsFillSameTypeFieldsAndReplaceNestedMapsAtomically() {
        var resolved = resolve(
                entry("first", "{\"abstract\":true,\"components\":[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":0.5}}},"
                        + "{\"type\":\"Bloodstream\",\"reference_solution\":{\"moonstation14:blood\":300},\"metabolism_exclusions\":[\"moonstation14:blood\"],\"max_bleed_rate\":10}]}"),
                entry("second", "{\"abstract\":true,\"components\":[" + SULFUR_BLOODSTREAM
                        + ",{\"type\":\"Barotrauma\",\"maxDamage\":200}]}"),
                entry("child", "{\"parent\":[\"first\",\"second\"],\"components\":[{\"type\":\"Bloodstream\",\"reference_solution\":{\"moonstation14:sulfurblood\":200},\"metabolism_exclusions\":[\"moonstation14:sulfurblood\"],\"max_bleed_rate\":7}]}"),
                entry("inherited", "{\"parent\":[\"first\",\"second\"]}"),
                entry("reversed", "{\"parent\":[\"second\",\"first\"]}"));

        JsonObject child = resolved.get(id("child"));
        assertComponentTypes(child, "Bloodstream", "Barotrauma");
        JsonObject childBlood = component(child);
        assertEquals(json("{\"moonstation14:sulfurblood\":200}"), childBlood.getAsJsonObject("reference_solution"));
        assertEquals(7, childBlood.get("max_bleed_rate").getAsInt());
        assertEquals(3, childBlood.get("update_interval_seconds").getAsInt());
        assertInstanceOf(BloodstreamComponent.class, CharacterComponentRegistry.CODEC.parse(JsonOps.INSTANCE, childBlood).getOrThrow());
        JsonObject childPressure = child.getAsJsonArray("components").get(1).getAsJsonObject();
        assertEquals(json("{\"blunt\":0.5}"), childPressure.getAsJsonObject("damage").getAsJsonObject("types"));
        assertEquals(200, childPressure.get("maxDamage").getAsInt());

        assertComponentTypes(resolved.get(id("inherited")), "Barotrauma", "Bloodstream");
        assertEquals(json("{\"moonstation14:blood\":300}"), resolved.get(id("inherited"))
                .getAsJsonArray("components").get(1).getAsJsonObject().getAsJsonObject("reference_solution"));
        assertComponentTypes(resolved.get(id("reversed")), "Bloodstream", "Barotrauma");
        assertEquals(json("{\"moonstation14:sulfurblood\":150}"), component(resolved.get(id("reversed")))
                .getAsJsonObject("reference_solution"));
        assertEquals(10, component(resolved.get(id("reversed"))).get("max_bleed_rate").getAsInt());
    }

    @Test
    void bloodstreamFragmentsUseFirstParentPerFieldAndAtomicSolutionMaps() {
        var resolved = resolve(
                entry("first", "{\"abstract\":true,\"components\":[{\"type\":\"Bloodstream\",\"reference_solution\":{\"moonstation14:blood\":300},\"max_bleed_rate\":10}]}"),
                entry("second", "{\"abstract\":true,\"components\":[{\"type\":\"Bloodstream\",\"reference_solution\":{\"moonstation14:sulfurblood\":150},\"update_interval_seconds\":3}]}"),
                entry("child", "{\"parent\":[\"first\",\"second\"],\"components\":[{\"type\":\"Bloodstream\",\"reference_solution\":{\"moonstation14:sulfurblood\":200},\"max_bleed_rate\":7}]}"),
                entry("inherited", "{\"parent\":[\"first\",\"second\"]}"));
        JsonObject inherited = component(resolved.get(id("inherited")));
        assertEquals(json("{\"moonstation14:blood\":300}"), inherited.getAsJsonObject("reference_solution"));
        assertEquals(3, inherited.get("update_interval_seconds").getAsInt());
        JsonObject child = component(resolved.get(id("child")));
        assertEquals(json("{\"moonstation14:sulfurblood\":200}"), child.getAsJsonObject("reference_solution"));
        assertEquals(7, child.get("max_bleed_rate").getAsInt());
        assertEquals(3, child.get("update_interval_seconds").getAsInt());
        for (String invalid : List.of("\"reference_solution\":null", "\"bloodloss_damage_per_update\":{\"blunt\":null}",
                "\"remove\":true", "\"unknown\":1")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> resolve(
                    entry("bad", "{\"abstract\":true,\"components\":[{\"type\":\"Bloodstream\"," + invalid + "}]}")));
            assertTrue(failure.getMessage().contains("$.components[0]"), failure.getMessage());
        }
    }

    @Test
    void singleParentPatchEmptyAndAbsentChildInheritWithoutChangingSources() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        raw.put(id("base"), json("{\"abstract\":true,\"components\":" + FIRST + "}"));
        raw.put(id("absent"), json("{\"parent\":\"base\"}"));
        raw.put(id("empty"), json("{\"parent\":\"base\",\"components\":[]}"));
        raw.put(id("patch"), json("{\"parent\":\"base\",\"components\":[{\"type\":\"Barotrauma\",\"maxDamage\":250}]}"));
        JsonObject original = raw.get(id("base")).deepCopy();
        var resolved = PrototypeResolver.resolve(raw, new CharacterComponentMergeStrategy());
        assertNull(resolved.get(id("base")));
        assertEquals(FIRST, resolved.get(id("absent")).getAsJsonArray("components").toString());
        assertEquals(FIRST, resolved.get(id("empty")).getAsJsonArray("components").toString());
        JsonObject patched = component(resolved.get(id("patch")));
        assertEquals(250, patched.get("maxDamage").getAsInt());
        assertEquals(json("{\"blunt\":0.5,\"heat\":0.1}"), patched.getAsJsonObject("damage").getAsJsonObject("types"));
        patched.getAsJsonObject("damage").getAsJsonObject("types").addProperty("blunt", 99);
        assertEquals(original, raw.get(id("base")));
        assertEquals(0.5, component(resolved.get(id("absent"))).getAsJsonObject("damage")
                .getAsJsonObject("types").get("blunt").getAsDouble());
    }

    @Test
    void multipleParentsFillMissingFieldsFirstConflictWinsAndChildPatchOverrides() {
        var resolved = resolve(
                entry("damage", "{\"components\":[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":0.5}}}]}"),
                entry("cap", "{\"components\":[{\"type\":\"Barotrauma\",\"maxDamage\":300}]}"),
                entry("complete", "{\"components\":" + FIRST + "}"),
                entry("inherited", "{\"parent\":[\"damage\",\"cap\"]}"),
                entry("reversed", "{\"parent\":[\"cap\",\"damage\"]}"),
                entry("conflict", "{\"parent\":[\"complete\",\"cap\"]}"),
                entry("reverse_conflict", "{\"parent\":[\"cap\",\"complete\"]}"),
                entry("patched", "{\"parent\":[\"complete\",\"cap\"],\"components\":[{\"type\":\"Barotrauma\",\"maxDamage\":250,\"damage\":{\"types\":{\"piercing\":0.4}}}]}"));
        for (String name : List.of("inherited", "reversed")) {
            JsonObject merged = component(resolved.get(id(name)));
            assertEquals(300, merged.get("maxDamage").getAsInt());
            assertEquals(json("{\"blunt\":0.5}"), merged.getAsJsonObject("damage").getAsJsonObject("types"));
        }
        assertEquals(200, component(resolved.get(id("conflict"))).get("maxDamage").getAsInt());
        assertEquals(300, component(resolved.get(id("reverse_conflict"))).get("maxDamage").getAsInt());
        JsonObject patched = component(resolved.get(id("patched")));
        assertEquals(250, patched.get("maxDamage").getAsInt());
        assertEquals(json("{\"piercing\":0.4}"), patched.getAsJsonObject("damage").getAsJsonObject("types"));
        assertComponentTypes(resolved.get(id("patched")), "Barotrauma");
    }

    @Test
    void partialAbstractFragmentsComposeAndInvalidRawFragmentsCannotBeHidden() {
        var valid = resolve(
                entry("damage", "{\"abstract\":true,\"components\":[{\"type\":\"Barotrauma\",\"damage\":{\"types\":{\"blunt\":0.5}}}]}"),
                entry("cap", "{\"abstract\":true,\"components\":[{\"type\":\"Barotrauma\",\"maxDamage\":300}]}"),
                entry("complete", "{\"parent\":[\"damage\",\"cap\"],\"components\":[{\"type\":\"Barotrauma\",\"maxDamage\":250}]}"));
        assertEquals(250, component(valid.get(id("complete"))).get("maxDamage").getAsInt());
        assertEquals(json("{\"blunt\":0.5}"), component(valid.get(id("complete")))
                .getAsJsonObject("damage").getAsJsonObject("types"));

        for (String[] invalid : List.of(
                new String[]{"\"unexpected\":1", ".unexpected"},
                new String[]{"\"maxDamage\":null", ".maxDamage"},
                new String[]{"\"maxDamage\":\"200\"", ".maxDamage"},
                new String[]{"\"damage\":null", ".damage"},
                new String[]{"\"damage\":{\"types\":{\"Blunt\":1}}", ".damage.types.Blunt"},
                new String[]{"\"damage\":{\"types\":{\"blunt\":-1}}", ".damage.types.blunt"},
                new String[]{"\"damage\":{\"types\":null}", ".damage.types"})) {
            String fragment = "{\"abstract\":true,\"components\":[{\"type\":\"Barotrauma\"," + invalid[0] + "}]}";
            // Even an abstract prototype with no descendants must validate its own raw fragment.
            assertFragmentFailure(invalid, entry("bad", fragment));
            // An override cannot erase an invalid ancestor declaration.
            assertFragmentFailure(invalid, entry("bad", fragment),
                    entry("child", "{\"parent\":\"bad\",\"components\":" + FIRST + "}"));
        }
    }

    @SafeVarargs
    private static void assertFragmentFailure(String[] invalid, Map.Entry<String, String>... entries) {
        var failure = assertThrows(IllegalArgumentException.class, () -> resolve(entries));
        assertTrue(failure.getMessage().contains("moonstation14:bad"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Barotrauma"), failure.getMessage());
        assertTrue(failure.getMessage().contains("$.components[0]" + invalid[1]), failure.getMessage());
    }

    @Test
    void missingComponentInUnrelatedPrototypeAndNewComponentWithoutParents() {
        var resolved = resolve(entry("base", "{\"components\":" + FIRST + "}"),
                entry("unrelated", "{\"components\":[]}"),
                entry("inherited", "{\"parent\":\"base\",\"components\":[]}"),
                entry("added", "{\"components\":" + FIRST + "}"));
        assertTrue(resolved.get(id("unrelated")).getAsJsonArray("components").isEmpty());
        assertEquals(FIRST, resolved.get(id("inherited")).getAsJsonArray("components").toString());
        assertEquals(FIRST, resolved.get(id("added")).getAsJsonArray("components").toString());
    }

    @Test
    void removalKeyIsUnknownFieldInParentAndChildFragments() {
        for (String value : List.of("true", "false", "null", "1")) {
            for (String parent : List.of("", "\"parent\":\"base\",")) {
                var failure = assertThrows(IllegalArgumentException.class, () -> resolve(
                        entry("base", "{\"components\":" + FIRST + "}"),
                        entry("bad", "{" + parent + "\"components\":[{\"type\":\"Barotrauma\",\"remove\":" + value + "}]}")));
                assertTrue(failure.getMessage().contains("moonstation14:bad"), failure.getMessage());
                assertTrue(failure.getMessage().contains("$.components[0].remove"), failure.getMessage());
                assertTrue(failure.getMessage().contains("unknown field"), failure.getMessage());
            }
        }
        var parentFailure = assertThrows(IllegalArgumentException.class, () -> resolve(
                entry("bad", "{\"abstract\":true,\"components\":[{\"type\":\"Barotrauma\",\"remove\":true}]}")));
        assertTrue(parentFailure.getMessage().contains("$.components[0].remove"), parentFailure.getMessage());
        assertTrue(parentFailure.getMessage().contains("unknown field"), parentFailure.getMessage());
    }

    @Test
    void malformedRawArraysFailWithCharacterAndComponentContext() {
        for (String value : List.of("null", "{}", "[null]", "[1]", "[{}]", "[{\"type\":null}]",
                "[{\"type\":\"Unknown\"}]", FIRST.substring(0, FIRST.length() - 1) + "," + FIRST.substring(1),
                "[{\"type\":\"Barotrauma\",\"remove\":false}]",
                "[{\"type\":\"Barotrauma\",\"remove\":true,\"maxDamage\":2}]",
                "[{\"type\":\"Barotrauma\",\"remove\":null}]",
                "[{\"type\":\"Barotrauma\",\"remove\":true}]")) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> resolve(entry("bad", "{\"components\":" + value + "}")), value);
            assertTrue(failure.getMessage().contains("moonstation14:bad"), failure.getMessage());
            assertTrue(failure.getMessage().contains("component"), failure.getMessage());
        }
    }

    @Test
    void defaultResolverStillReplacesArrays() {
        Map<ResourceLocation, JsonObject> raw = Map.of(id("base"), json("{\"components\":" + FIRST + "}"),
                id("child"), json("{\"parent\":\"base\",\"components\":[]}"));
        assertTrue(PrototypeResolver.resolve(raw).get(id("child")).getAsJsonArray("components").isEmpty());
    }

    private static JsonObject component(JsonObject character) {
        return character.getAsJsonArray("components").get(0).getAsJsonObject();
    }

    private static void assertComponentTypes(JsonObject character, String... expected) {
        var components = character.getAsJsonArray("components");
        assertEquals(List.of(expected), components.asList().stream()
                .map(entry -> entry.getAsJsonObject().get("type").getAsString()).toList(),
                "component order and uniqueness");
    }

    @SafeVarargs
    private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<JsonObject> resolve(Map.Entry<String, String>... entries) {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (var entry : entries) raw.put(id(entry.getKey()), json(entry.getValue()));
        return PrototypeResolver.resolve(raw, new CharacterComponentMergeStrategy());
    }

    private static Map.Entry<String, String> entry(String name, String json) { return Map.entry(name, json); }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("moonstation14", name); }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
