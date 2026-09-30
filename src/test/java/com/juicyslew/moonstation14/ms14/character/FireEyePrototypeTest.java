package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.BlindablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.FlammablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FireEyePrototypeTest {
    private static final ResourceLocation PARENT = ResourceLocation.parse("moonstation14:fire_eye_parent");
    private static final ResourceLocation CHILD = ResourceLocation.parse("moonstation14:fire_eye_child");
    private static final ResourceLocation HOST = ResourceLocation.parse("minecraft:villager");

    private static JsonObject json(String source) { return JsonParser.parseString(source).getAsJsonObject(); }

    @Test
    void shippedHumanAndPigEachOptIntoBothWithoutImplicitEnrollment() throws Exception {
        for (String species : new String[]{"human", "pig"}) {
            String path = "data/moonstation14/moonstation14/character/" + species + ".json";
            try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(input, path);
                CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
                assertTrue(data.component(FlammablePrototypeComponent.class).isPresent(), species);
                assertEquals(1.5f, data.component(FlammablePrototypeComponent.class).orElseThrow().damage().types().get("heat"));
                assertTrue(data.component(BlindablePrototypeComponent.class).isPresent(), species);
            }
        }
        assertTrue(new CharacterData().component(FlammablePrototypeComponent.class).isEmpty());
        assertTrue(new CharacterData().component(BlindablePrototypeComponent.class).isEmpty());
    }

    @Test
    void markersAreIndependentStrictInCompleteAndFragmentCodecs() {
        for (String type : new String[]{"Flammable", "Blindable"}) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    json("{\"components\":[{\"type\":\"" + type + "\"}]}")).result().isPresent());
            for (String extra : new String[]{"\"config\":1", "\"config\":null", "\"type\":null"}) {
                JsonObject object = json(extra.equals("\"type\":null") ? "{\"type\":null}"
                        : "{\"type\":\"" + type + "\"," + extra + "}");
                assertThrows(IllegalArgumentException.class,
                        () -> CharacterSchemaAudit.auditComponentFragment(object, "$.components[0]"));
                assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                        json("{\"components\":[" + object + "]}")).result().isEmpty());
            }
        }
        CharacterData fireOnly = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                json("{\"components\":[{\"type\":\"Flammable\"}]}")).getOrThrow();
        CharacterData eyesOnly = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                json("{\"components\":[{\"type\":\"Blindable\"}]}")).getOrThrow();
        assertTrue(fireOnly.component(FlammablePrototypeComponent.class).isPresent());
        assertTrue(fireOnly.component(BlindablePrototypeComponent.class).isEmpty());
        assertTrue(eyesOnly.component(BlindablePrototypeComponent.class).isPresent());
        assertTrue(eyesOnly.component(FlammablePrototypeComponent.class).isEmpty());
    }

    @Test
    void flammableFadeDefaultsValidatesAndRoundTripsThroughInheritance() {
        var defaultData = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                json("{\"components\":[{\"type\":\"Flammable\"}]}" )).getOrThrow();
        var defaultFlammable = defaultData.component(FlammablePrototypeComponent.class).orElseThrow();
        assertEquals(-0.1f, defaultFlammable.firestackFade());
        assertTrue(defaultFlammable.damage().types().isEmpty());
        var defaultEncoded = CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, defaultData).getOrThrow();
        assertEquals(-0.1f, CharacterData.CODEC.parse(JsonOps.INSTANCE, defaultEncoded).getOrThrow()
                .component(FlammablePrototypeComponent.class).orElseThrow().firestackFade());

        for (float fade : new float[]{-2.5f, 0.0f}) {
            var custom = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    json("{\"components\":[{\"type\":\"Flammable\",\"firestack_fade\":" + fade + "}]}")).getOrThrow();
            assertEquals(fade, custom.component(FlammablePrototypeComponent.class).orElseThrow().firestackFade());
        }
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                json("{\"components\":[{\"type\":\"Flammable\",\"firestack_fade\":\"bad\"}]}")).result().isEmpty());
        for (String invalid : new String[]{"0.1", "-10.1", "1e999", "\"bad\""}) {
            JsonObject component = json("{\"type\":\"Flammable\",\"firestack_fade\":" + invalid + "}");
            assertThrows(IllegalArgumentException.class,
                    () -> CharacterSchemaAudit.auditComponentFragment(component, "$.components[0]"));
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    json("{\"components\":[" + component + "]}")).result().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> CharacterSchemaAudit.auditComponentFragment(
                json("{\"type\":\"Flammable\",\"extra\":1}"), "$.components[0]"));
        JsonObject nan = json("{\"type\":\"Flammable\"}");
        nan.add("firestack_fade", new JsonPrimitive(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> CharacterSchemaAudit.auditComponentFragment(nan, "$.components[0]"));

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(PARENT,
                json("{\"abstract\":true,\"components\":[{\"type\":\"Flammable\",\"firestack_fade\":-3.0}]}"),
                CHILD, json("{\"parent\":\"fire_eye_parent\"}")));
        assertEquals(-3.0f, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(CHILD).component(
                FlammablePrototypeComponent.class).orElseThrow().firestackFade());
    }

    @Test
    void flammableDamageValidatesDefaultsAndRoundTripsThroughInheritance() {
        var custom = CharacterData.CODEC.parse(JsonOps.INSTANCE, json("{\"components\":[{\"type\":\"Flammable\",\"damage\":{\"types\":{\"heat\":1.5,\"blunt\":2}}}]}" )).getOrThrow();
        assertEquals(Map.of("heat", 1.5f, "blunt", 2.0f), custom.component(FlammablePrototypeComponent.class).orElseThrow().damage().types());
        var encoded = CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, custom).getOrThrow();
        assertEquals(custom, CharacterData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        for (String invalid : new String[]{"null", "{}", "{\"types\":null}", "{\"types\":{\"heat\":-1}}", "{\"types\":{\"unknown\":1}}", "{\"types\":{\"heat\":1},\"extra\":1}"}) {
            JsonObject component = json("{\"type\":\"Flammable\",\"damage\":" + invalid + "}");
            assertThrows(IllegalArgumentException.class, () -> CharacterSchemaAudit.auditComponentFragment(component, "$.components[0]"));
            if (invalid.contains("-1") || invalid.contains("unknown"))
                assertTrue(FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE, component).error().isPresent(), invalid);
        }
        JsonObject nonfinite = json("{\"type\":\"Flammable\",\"damage\":{\"types\":{}}}");
        nonfinite.getAsJsonObject("damage").getAsJsonObject("types").add("heat", new JsonPrimitive(Double.POSITIVE_INFINITY));
        assertTrue(FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE, nonfinite).error().isPresent());
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(PARENT,
                json("{\"abstract\":true,\"components\":[{\"type\":\"Flammable\",\"damage\":{\"types\":{\"heat\":2}}}]}"),
                CHILD, json("{\"parent\":\"fire_eye_parent\",\"components\":[{\"type\":\"Flammable\",\"damage\":{\"types\":{\"blunt\":3}}}]}")));
        assertEquals(Map.of("blunt", 3.0f), manager.snapshot(ModCharacters.CHARACTER_TYPE).get(CHILD)
                .component(FlammablePrototypeComponent.class).orElseThrow().damage().types());
    }

    @Test
    void flammableComponentCodecReturnsErrorsForInvalidValuesDirectly() {
        for (String invalid : new String[]{
                "{\"type\":\"Flammable\",\"damage\":null}",
                "{\"type\":\"Flammable\",\"damage\":{\"types\":null}}",
                "{\"type\":\"Flammable\",\"damage\":{\"types\":{},\"extra\":1}}",
                "{\"type\":\"Flammable\",\"extra\":1}",
                "{\"type\":\"Blindable\"}",
                "{\"type\":\"Flammable\",\"damage\":{\"types\":{\"unknown\":1}}}",
                "{\"type\":\"Flammable\",\"damage\":{\"types\":{\"heat\":-1}}}"
        }) {
            assertTrue(FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE, json(invalid)).error().isPresent(), invalid);
        }

        for (String invalid : new String[]{"0.1", "1e999", "\"bad\""}) {
            var result = FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE,
                    json("{\"type\":\"Flammable\",\"firestack_fade\":" + invalid + "}"));
            assertTrue(result.error().isPresent(), invalid);
            assertThrows(RuntimeException.class, result::getOrThrow);
        }

        JsonObject infinity = json("{\"type\":\"Flammable\"}");
        infinity.add("firestack_fade", new JsonPrimitive(Double.POSITIVE_INFINITY));
        var infiniteResult = FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE, infinity);
        assertTrue(infiniteResult.error().isPresent());
        assertThrows(RuntimeException.class, infiniteResult::getOrThrow);

        var wrongType = FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE,
                json("{\"type\":\"Blindable\"}"));
        assertTrue(wrongType.error().isPresent());
        assertThrows(RuntimeException.class, wrongType::getOrThrow);

        var defaultValue = FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE,
                json("{\"type\":\"Flammable\"}")).getOrThrow();
        assertEquals(-0.1f, defaultValue.firestackFade());
        var zero = FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE,
                json("{\"type\":\"Flammable\",\"firestack_fade\":0}")).getOrThrow();
        assertEquals(0.0f, zero.firestackFade());
        assertEquals(zero, FlammablePrototypeComponent.CODEC.parse(JsonOps.INSTANCE,
                FlammablePrototypeComponent.CODEC.encodeStart(JsonOps.INSTANCE, zero).getOrThrow()).getOrThrow());
    }

    @Test
    void inheritedMarkersAndCurrentSnapshotHostOwnershipFailClosedOnReload() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        JsonObject parent = json("{\"abstract\":true,\"components\":[{\"type\":\"Flammable\"},{\"type\":\"Blindable\"}]}");
        JsonObject child = json("{\"parent\":\"fire_eye_parent\",\"host_entity_types\":[\"minecraft:villager\"]}");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(PARENT, parent, CHILD, child));
        var old = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        var inherited = CharacterIdentitySystem.resolveForHost(old, CHILD, HOST).orElseThrow();
        assertTrue(inherited.component(FlammablePrototypeComponent.class).isPresent());
        assertTrue(inherited.component(BlindablePrototypeComponent.class).isPresent());
        assertEquals(inherited, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                manager.encodePublishedCatalogs().get(ModCharacters.CHARACTER_TYPE.typeId()).get(CHILD)).getOrThrow());
        assertTrue(CharacterIdentitySystem.resolveForHost(old, CHILD, ResourceLocation.parse("minecraft:pig")).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(old, PARENT, HOST).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(old, null, HOST).isEmpty());

        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(CHILD,
                json("{\"host_entity_types\":[\"minecraft:villager\"],\"components\":[{\"type\":\"Blindable\"}]}")));
        var current = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertTrue(CharacterIdentitySystem.resolveForHost(current, CHILD, HOST).orElseThrow()
                .component(FlammablePrototypeComponent.class).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(current, CHILD, HOST).orElseThrow()
                .component(BlindablePrototypeComponent.class).isPresent());
        assertTrue(inherited.component(FlammablePrototypeComponent.class).isPresent(), "old snapshot remains immutable");
    }
}
