package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.TileReactionData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReagentDataCodecTest {
    @Test
    void primitiveAndListOptionalsRoundTripIncludingDefaultLikeValues() {
        JsonObject json = JsonParser.parseString("""
                {
                  "id":"optional-fields",
                  "recognizable":false,
                  "fizziness":0,
                  "worksonthedead":false,
                  "alloweddepartments":[],
                  "allowedjobs":["Botany"],
                  "friction":0,
                  "standsout":false,
                  "flavorminimum":0,
                  "evaporationspeed":0,
                  "viscosity":0,
                  "absorbent":false
                }
                """).getAsJsonObject();

        ReagentData decoded = ReagentData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        JsonObject encoded = ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow().getAsJsonObject();
        ReagentData roundTripped = ReagentData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(decoded, roundTripped);
        assertEquals(encoded, ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, roundTripped).getOrThrow());
        assertTrue(decoded.recognizable().isPresent());
        assertTrue(decoded.allowedDepartments().isPresent());
        assertEquals(0f, decoded.fizziness().orElseThrow());
        assertEquals(false, decoded.absorbent().orElseThrow());
        assertTrue(encoded.has("alloweddepartments"));
    }

    @Test
    void absentOptionalsRemainAbsent() {
        ReagentData decoded = ReagentData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"no-optionals\"}")).getOrThrow();

        assertTrue(decoded.recognizable().isEmpty());
        assertTrue(decoded.slipData().isEmpty());
        assertTrue(decoded.tileReactions().isEmpty());
        assertFalse(ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow().getAsJsonObject().has("friction"));
    }

    @Test
    void nestedAndTileReactionValuesRoundTripWithoutInterpretingType() {
        JsonObject json = JsonParser.parseString("""
                {
                  "id":"buzzochloricbees-style",
                  "slipData":{"requiredSlipSpeed":3.5,"superSlippery":false},
                  "footstepsound":{"collection":"footstepblood","params":{"volume":6}},
                  "tilereactions":[
                    {"type":"CreateEntityTileReaction { entity: mobbee }", "temperaturemultiplier":1.25,
                     "entity":"mobbee", "usage":2, "maxontile":2, "randomoffsetmax":0.3,
                     "maxontilewhitelist":{"tags":["bee","small"]}},
                    {"type":"CleanTileReaction { }", "cleancost":0}
                  ]
                }
                """).getAsJsonObject();

        ReagentData decoded = ReagentData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        JsonObject encoded = ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow().getAsJsonObject();
        ReagentData roundTripped = ReagentData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(decoded, roundTripped);
        assertEquals(encoded, ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, roundTripped).getOrThrow());
        assertEquals("CreateEntityTileReaction { entity: mobbee }",
                roundTripped.tileReactions().orElseThrow().get(0).type());
        TileReactionData create = roundTripped.tileReactions().orElseThrow().get(0);
        assertEquals(2, create.maxOnTile().orElseThrow());
        assertEquals(java.util.List.of("bee", "small"),
                create.maxOnTileWhitelist().orElseThrow().tags());
        assertEquals(false, roundTripped.slipData().orElseThrow().superSlippery().orElseThrow());
        assertEquals(6f, roundTripped.footstepSound().orElseThrow().params().orElseThrow().volume());
    }
}
