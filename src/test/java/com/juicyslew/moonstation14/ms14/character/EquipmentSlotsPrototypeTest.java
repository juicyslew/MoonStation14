package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentSlotsPrototypeTest {
    private static JsonObject resource(String name) throws Exception {
        try (var stream = EquipmentSlotsPrototypeTest.class.getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/" + name + ".json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    @Test void shippedPolicyIsExplicitOrderedAndImmutable() throws Exception {
        var human = CharacterData.CODEC.parse(JsonOps.INSTANCE, resource("human")).getOrThrow();
        var pig = CharacterData.CODEC.parse(JsonOps.INSTANCE, resource("pig")).getOrThrow();
        var slots = human.component(EquipmentSlotsPrototypeComponent.class).orElseThrow();
        assertEquals(List.of("belt", "back"), slots.slots());
        assertThrows(UnsupportedOperationException.class, () -> slots.slots().clear());
        assertTrue(pig.component(EquipmentSlotsPrototypeComponent.class).isEmpty());
        assertTrue(new CharacterData().component(EquipmentSlotsPrototypeComponent.class).isEmpty());
        assertEquals(slots, CharacterComponentRegistry.CODEC.parse(JsonOps.INSTANCE,
                CharacterComponentRegistry.CODEC.encodeStart(JsonOps.INSTANCE, slots).getOrThrow()).getOrThrow());
        assertThrows(IllegalArgumentException.class, () -> new EquipmentSlotsPrototypeComponent(List.of("belt", "belt")));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentSlotsPrototypeComponent(List.of("head")));
    }

    @Test void strictCodecRejectsLegacyMalformedDuplicateAndUnreviewedSlots() {
        for (String value : List.of("null", "{}", "[]")) {
            for (String field : List.of("equipment", "equipment_slots")) {
                var input = json("{}");
                input.add(field, JsonParser.parseString(value));
                assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, input).error().orElseThrow()
                        .message().contains("$." + field));
            }
        }
        for (String value : List.of("null", "{}", "[1]", "[\"head\"]", "[\"Belt\"]",
                "[\"belt\",\"belt\"]", "[\"belt\",\"back\",\"belt\"]")) {
            var input = json("{\"components\":[{\"type\":\"EquipmentSlots\"} ]}");
            input.getAsJsonArray("components").get(0).getAsJsonObject().add("slots", JsonParser.parseString(value));
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, input).error().isPresent(), value);
        }
        for (String value : List.of("{\"type\":\"EquipmentSlots\"}",
                "{\"type\":\"EquipmentSlots\",\"slots\":[],\"extra\":true}")) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, json("{\"components\":[" + value + "]}"))
                    .error().isPresent(), value);
        }
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, json("{\"components\":["
                + "{\"type\":\"EquipmentSlots\",\"slots\":[\"belt\"]},"
                + "{\"type\":\"EquipmentSlots\",\"slots\":[\"back\"]}]}")).error().isPresent());
    }

    @Test void inheritanceAuditsFragmentsAndRawAbstractLegacyFields() {
        var parentId = ResourceLocation.parse("moonstation14:equipment_parent");
        var childId = ResourceLocation.parse("moonstation14:equipment_child");
        var parent = json("{\"abstract\":true,\"components\":[{\"type\":\"EquipmentSlots\",\"slots\":[\"belt\"]}]}");
        var child = json("{\"parent\":\"equipment_parent\",\"components\":[{\"type\":\"EquipmentSlots\",\"slots\":[\"back\"]}]}");
        var manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(parentId, parent, childId, child));
        assertEquals(List.of("back"), manager.snapshot(ModCharacters.CHARACTER_TYPE).get(childId)
                .component(EquipmentSlotsPrototypeComponent.class).orElseThrow().slots());
        for (String invalid : List.of("[\"head\"]", "[\"belt\",\"belt\"]", "null")) {
            var brokenParent = parent.deepCopy();
            brokenParent.getAsJsonArray("components").get(0).getAsJsonObject().add("slots", JsonParser.parseString(invalid));
            assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                    Map.of(parentId, brokenParent, childId, child))), invalid);
        }
        var fragment = json("{\"type\":\"EquipmentSlots\"}");
        CharacterSchemaAudit.auditComponentFragment(fragment, "$.components[0]");
        fragment.addProperty("extra", true);
        assertThrows(IllegalArgumentException.class,
                () -> CharacterSchemaAudit.auditComponentFragment(fragment, "$.components[0]"));
        for (String field : List.of("equipment", "equipment_slots")) {
            var brokenParent = parent.deepCopy();
            brokenParent.add(field, JsonParser.parseString("null"));
            var error = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                    Map.of(parentId, brokenParent, childId, child))));
            assertTrue(error.getMessage().contains("$." + field), error.getMessage());
        }
    }
}
