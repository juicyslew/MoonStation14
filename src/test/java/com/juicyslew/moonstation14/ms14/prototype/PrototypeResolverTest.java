package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrototypeResolverTest {
    private static final String NS = "moonstation14";

    @Test
    void resolvesScalarAndNamespacedParentsWithoutMutatingRawJson() {
        Map<ResourceLocation, JsonObject> raw = map(
                entry("base", "{\"inherited\":1}"),
                entry("addon:foreign", "{\"foreign\":true}"),
                entry("child", "{\"id\":\"child\",\"parent\":\"base\",\"own\":2}"),
                entry("addon:child", "{\"parent\":\"addon:foreign\"}")
        );

        PrototypeCatalog<JsonObject> catalog = PrototypeResolver.resolve(raw);

        assertEquals(1, catalog.get(id("child")).get("inherited").getAsInt());
        assertTrue(catalog.get(id("addon:child")).get("foreign").getAsBoolean());
        assertTrue(raw.get(id("child")).has("parent"));
        assertFalse(catalog.get(id("child")).has("parent"));
    }

    @Test
    void usesOrderedMultipleParentPrecedenceAndPresenceAwareOrdinaryFields() {
        Map<ResourceLocation, JsonObject> raw = map(
                entry("first", "{\"shared\":\"first\",\"object\":{\"from\":\"first\"},\"list\":[1]}"),
                entry("second", "{\"shared\":\"second\",\"other\":true,\"object2\":{}}"),
                entry("child", "{\"parent\":[\"first\",\"second\"],\"object\":{},\"list\":[]}")
        );

        JsonObject resolved = PrototypeResolver.resolve(raw).get(id("child"));
        assertEquals("first", resolved.get("shared").getAsString());
        assertTrue(resolved.has("other"));
        assertTrue(resolved.getAsJsonObject("object").entrySet().isEmpty());
        assertTrue(resolved.getAsJsonArray("list").isEmpty());
        assertTrue(resolved.getAsJsonObject("object2").isEmpty());
    }

    @Test
    void resolvesDepthAndExcludesAbstractNodes() {
        Map<ResourceLocation, JsonObject> raw = map(
                entry("root", "{\"abstract\":true,\"rootValue\":1}"),
                entry("middle", "{\"parent\":\"root\",\"middleValue\":2}"),
                entry("leaf", "{\"parent\":\"middle\",\"leafValue\":3}")
        );

        PrototypeCatalog<JsonObject> catalog = PrototypeResolver.resolve(raw);
        assertEquals(2, catalog.size());
        assertFalse(catalog.contains(id("root")));
        assertEquals(1, catalog.get(id("leaf")).get("rootValue").getAsInt());
        assertEquals(2, catalog.get(id("leaf")).get("middleValue").getAsInt());
        assertFalse(catalog.get(id("leaf")).has("abstract"));
    }

    @Test
    void reportsMissingDuplicateEmptyAndMalformedMetadata() {
        assertMessage("Missing parent", map(entry("child", "{\"parent\":\"missing\"}")));
        assertMessage("duplicate parent", map(entry("base", "{}"), entry("child", "{\"parent\":[\"base\",\"base\"]}")));
        assertMessage("empty 'parent' array", map(entry("child", "{\"parent\":[]}")));
        assertMessage("malformed 'parent'", map(entry("child", "{\"parent\":5}")));
        assertMessage("malformed 'parent' array", map(entry("base", "{}"), entry("child", "{\"parent\":[\"base\",false]}")));
        assertMessage("malformed 'abstract'", map(entry("child", "{\"abstract\":\"yes\"}")));
    }

    @Test
    void reportsSelfAndTransitiveCyclesWithDependencyPath() {
        PrototypeResolutionException self = assertThrows(PrototypeResolutionException.class,
                () -> PrototypeResolver.resolve(map(entry("self", "{\"parent\":\"self\"}"))));
        assertTrue(self.getMessage().contains("moonstation14:self -> moonstation14:self"));

        PrototypeResolutionException transitive = assertThrows(PrototypeResolutionException.class,
                () -> PrototypeResolver.resolve(map(
                        entry("a", "{\"parent\":\"b\"}"),
                        entry("b", "{\"parent\":\"c\"}"),
                        entry("c", "{\"parent\":\"a\"}"))));
        assertTrue(transitive.getMessage().contains("moonstation14:a -> moonstation14:b -> moonstation14:c -> moonstation14:a"));
    }

    @Test
    void catalogOwnsItsMapAndExposesReadOnlyViews() {
        Map<ResourceLocation, JsonObject> source = new LinkedHashMap<>();
        source.put(id("one"), object("{\"value\":1}"));
        PrototypeCatalog<JsonObject> catalog = new PrototypeCatalog<>(source);
        source.clear();

        assertEquals(1, catalog.size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.keys().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.values().clear());
    }

    private static void assertMessage(String expected, Map<ResourceLocation, JsonObject> raw) {
        PrototypeResolutionException exception = assertThrows(PrototypeResolutionException.class,
                () -> PrototypeResolver.resolve(raw));
        assertTrue(exception.getMessage().contains(expected), exception.getMessage());
    }

    @SafeVarargs
    private static Map<ResourceLocation, JsonObject> map(Map.Entry<String, String>... entries) {
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            result.put(id(entry.getKey()), object(entry.getValue()));
        }
        return result;
    }

    private static Map.Entry<String, String> entry(String id, String json) {
        return Map.entry(id, json);
    }

    private static ResourceLocation id(String value) {
        int separator = value.indexOf(':');
        return separator < 0
                ? ResourceLocation.fromNamespaceAndPath(NS, value)
                : ResourceLocation.fromNamespaceAndPath(value.substring(0, separator), value.substring(separator + 1));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
