package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterPrototypeTest {
    private static final String RESOURCE = "data/moonstation14/moonstation14/character/human.json";

    @Test
    void humanPrototypeIsTypedAndRoundTripsThroughThePrototypeCatalog() throws IOException {
        JsonObject raw;
        try (var stream = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing resource " + RESOURCE);
            raw = JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow();
        var slip = data.slipData();
        assertTrue(slip.canReceiveStun());
        assertFalse(slip.noSlip());
        assertTrue(slip.standingEligible());
        assertTrue(slip.proneEligible());
        assertEquals(List.of(CharacterData.ReactiveGroup.FLAMMABLE, CharacterData.ReactiveGroup.EXTINGUISH,
                CharacterData.ReactiveGroup.ACIDIC), slip.reactiveGroups());
        assertEquals(List.of(CharacterData.ReactiveMethod.TOUCH), slip.reactiveMethods());
        assertEquals(data, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow());

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, raw));
        assertEquals(data, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(ModCharacters.HUMAN_ID));
        JsonObject encoded = manager.encodePublishedCatalogs()
                .get(ModCharacters.CHARACTER_TYPE.typeId()).get(ModCharacters.HUMAN_ID);
        assertEquals(data, CharacterData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void codecRejectsMissingUnknownNoncanonicalAndSemanticallyInconsistentPolicy() {
        JsonObject valid = JsonParser.parseString("""
                {"slip_data":{"can_receive_stun":true,"no_slip":false,
                 "standing_eligible":true,"prone_eligible":true,
                 "reactive_groups":["flammable","extinguish","acidic"],"reactive_methods":["touch"]}}
                """).getAsJsonObject();
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, valid).result().isPresent());

        for (JsonObject invalid : List.of(
                new JsonObject(),
                withSlip(valid, "extra", true),
                replace(valid, "reactive_methods", "[\"Touch\"]"),
                replace(valid, "reactive_groups", "[\"flammable\",\"flammable\"]"),
                replace(valid, "reactive_groups", "[]"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
        }

        JsonObject missingRequired = valid.deepCopy();
        missingRequired.getAsJsonObject("slip_data").remove("can_receive_stun");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, missingRequired).error().isPresent());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("null")).error().isPresent());
    }

    private static JsonObject withSlip(JsonObject source, String field, boolean value) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("slip_data").addProperty(field, value);
        return result;
    }

    private static JsonObject replace(JsonObject source, String field, String json) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("slip_data").add(field, JsonParser.parseString(json));
        return result;
    }
}
