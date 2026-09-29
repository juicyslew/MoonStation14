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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(2.5, data.movement().orElseThrow().walkSpeed());
        assertEquals(List.of(ResourceLocation.parse("minecraft:player"), ResourceLocation.parse("minecraft:villager")),
                data.hostEntityTypes());
        assertEquals(List.of("left", "right"), data.hands());
        var thermal = data.thermal().orElseThrow();
        assertEquals(70.0, thermal.massKg());
        assertEquals(42.0, thermal.specificHeatJoulesPerKgKelvin());
        assertEquals(310.0, thermal.currentKelvin());
        assertEquals(70.0 * 42.0, thermal.toProfile().bodyHeatCapacityJoulesPerKelvin());
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
    void legacySlipOnlyAndPigPrototypeRemainTypedAndRoundTrip() throws IOException {
        JsonObject legacy = JsonParser.parseString("""
                {"slip_data":{"can_receive_stun":true,"no_slip":false,"standing_eligible":true,
                 "prone_eligible":true,"reactive_groups":[],"reactive_methods":[]}}
                """).getAsJsonObject();
        CharacterData oldData = CharacterData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(Optional.empty(), oldData.movement());
        assertEquals(Optional.empty(), oldData.thermal());
        assertTrue(oldData.hostEntityTypes().isEmpty());
        assertTrue(oldData.hands().isEmpty());
        assertEquals(oldData, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, oldData).getOrThrow()).getOrThrow());
        assertEquals(oldData, new CharacterData(oldData.slipData()));

        JsonObject pigJson = readResource("data/moonstation14/moonstation14/character/pig.json");
        CharacterData pig = CharacterData.CODEC.parse(JsonOps.INSTANCE, pigJson).getOrThrow();
        assertEquals(4.0, pig.movement().orElseThrow().walkSpeed());
        assertNotEquals(2.5, pig.movement().orElseThrow().walkSpeed());
        assertEquals(List.of(ResourceLocation.parse("minecraft:pig")), pig.hostEntityTypes());
        assertTrue(pig.hands().isEmpty(), "pig prototypes do not acquire implicit hands");
        assertFalse(pig.slipData().canReceiveStun());
        assertTrue(pig.slipData().noSlip());
        assertFalse(pig.slipData().standingEligible());
        assertFalse(pig.slipData().proneEligible());
        assertTrue(pig.slipData().reactiveGroups().isEmpty());
        assertTrue(pig.slipData().reactiveMethods().isEmpty());
        assertEquals(pig, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                CharacterData.CODEC.encodeStart(JsonOps.INSTANCE, pig).getOrThrow()).getOrThrow());
    }

    @Test
    void thermalPolicyIsStrictAndOptional() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        JsonObject thermal = valid.getAsJsonObject("thermal");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutThermal(valid)).getOrThrow().thermal().isEmpty());

        for (JsonObject invalid : List.of(
                nonFiniteThermal(valid),
                withThermalField(valid, "mass_kg", "-1"),
                withThermalField(valid, "unknown", "1"),
                withoutThermalField(valid, "damage_cap"),
                withThermalField(valid, "current_kelvin", "325"),
                withThermalField(valid, "heat_damage_threshold_kelvin", "250"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), invalid.toString());
        }
        assertTrue(thermal.get("heat_damage_threshold_kelvin").getAsDouble()
                > thermal.get("cold_damage_threshold_kelvin").getAsDouble());
    }

    @Test
    void rejectsInvalidMovementAndHostMappings() throws IOException {
        JsonObject valid = readResource(RESOURCE);
        for (JsonObject invalid : List.of(
                replaceMovement(valid, "mode", "\"airborne\""),
                replaceMovement(valid, "acceleration", "-1"),
                nonFiniteMovement(valid),
                withMovement(valid, "unexpected", "1"),
                hosts(valid, "[\"not a resource id\"]"),
                hosts(valid, "[\"minecraft:pig\",\"minecraft:pig\"]"),
                hosts(withoutMovement(valid), "[\"minecraft:pig\"]"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), invalid.toString());
        }
    }

    @Test
    void hostLookupIsIndexedPerPublishedCatalogAndDuplicateClaimsRejectReload() throws IOException {
        JsonObject human = readResource(RESOURCE);
        JsonObject pig = readResource("data/moonstation14/moonstation14/character/pig.json");
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(
                ModCharacters.HUMAN_ID, human,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "pig"), pig));

        var published = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertEquals(Optional.of(ModCharacters.HUMAN_ID),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:player")));
        assertEquals(Optional.of(ModCharacters.HUMAN_ID),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:villager")));
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "pig")),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:pig")));
        assertEquals(Optional.empty(),
                ModCharacters.characterForHost(published, ResourceLocation.parse("minecraft:zombie")));

        JsonObject conflictingPig = hosts(pig, "[\"minecraft:villager\"]");
        var failure = assertThrows(RuntimeException.class, () -> manager.reload(ModCharacters.CHARACTER_TYPE,
                Map.of(ModCharacters.HUMAN_ID, human,
                        ResourceLocation.fromNamespaceAndPath("moonstation14", "pig"), conflictingPig)));
        assertTrue(failure.getMessage().contains("minecraft:villager"));
        assertTrue(failure.getMessage().contains("moonstation14:human"));
        assertTrue(failure.getMessage().contains("moonstation14:pig"));
        assertEquals(published, manager.snapshot(ModCharacters.CHARACTER_TYPE),
                "a rejected candidate must leave the previously published catalog intact");
    }

    private static JsonObject readResource(String path) throws IOException {
        try (var stream = CharacterPrototypeTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            return JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject withoutMovement(JsonObject source) {
        JsonObject result = source.deepCopy(); result.remove("movement"); return result;
    }

    private static JsonObject hosts(JsonObject source, String json) {
        JsonObject result = source.deepCopy(); result.add("host_entity_types", JsonParser.parseString(json)); return result;
    }

    private static JsonObject withMovement(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("movement").add(field, JsonParser.parseString(value)); return result;
    }

    private static JsonObject replaceMovement(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("movement").add(field, JsonParser.parseString(value)); return result;
    }

    private static JsonObject nonFiniteMovement(JsonObject source) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("movement").addProperty("walk_speed", Double.NaN);
        return result;
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

    @Test
    void handsAreOptionalOrderedCapabilityAndMalformedDeclarationsRejectCodecAndReload() throws IOException {
        JsonObject human = readResource(RESOURCE);
        assertEquals(List.of("left", "right"), CharacterData.CODEC.parse(JsonOps.INSTANCE, human)
                .getOrThrow().hands());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withHands(human, "[]"))
                .getOrThrow().hands().isEmpty());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, withoutHands(human))
                .getOrThrow().hands().isEmpty());

        String tooLong = "a".repeat(com.juicyslew.moonstation14.ms14.hands.HandState.MAX_ID_LENGTH + 1);
        for (JsonObject invalid : List.of(
                withHands(human, "{}"),
                withHands(human, "[1]"),
                withHands(human, "[\"left\",\"left\"]"),
                withHands(human, "[\" \"]"),
                withHands(human, "[\"" + tooLong + "\"]"),
                withHands(human, "[" + String.join(",", java.util.Collections.nCopies(
                        com.juicyslew.moonstation14.ms14.hands.HandState.MAX_HANDS + 1, "\"h\"")) + "]"))) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), invalid.toString());
        }
        JsonObject unknownField = human.deepCopy();
        unknownField.addProperty("hand", "left");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, unknownField).error().isPresent());

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, human));
        for (JsonObject invalid : List.of(withHands(human, "[\"left\",\"left\"]"),
                withHands(human, "[\"" + tooLong + "\"]"))) {
            assertThrows(RuntimeException.class,
                    () -> manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(ModCharacters.HUMAN_ID, invalid)));
        }
    }

    private static JsonObject withHands(JsonObject source, String json) {
        JsonObject result = source.deepCopy(); result.add("hands", JsonParser.parseString(json)); return result;
    }

    private static JsonObject withoutHands(JsonObject source) {
        JsonObject result = source.deepCopy(); result.remove("hands"); return result;
    }

    private static JsonObject withSlip(JsonObject source, String field, boolean value) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("slip_data").addProperty(field, value);
        return result;
    }

    private static JsonObject withoutThermal(JsonObject source) {
        JsonObject result = source.deepCopy(); result.remove("thermal"); return result;
    }

    private static JsonObject withThermalField(JsonObject source, String field, String value) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("thermal").add(field, JsonParser.parseString(value));
        return result;
    }

    private static JsonObject nonFiniteThermal(JsonObject source) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("thermal").addProperty("mass_kg", Double.NaN);
        return result;
    }

    private static JsonObject withoutThermalField(JsonObject source, String field) {
        JsonObject result = source.deepCopy(); result.getAsJsonObject("thermal").remove(field); return result;
    }

    private static JsonObject replace(JsonObject source, String field, String json) {
        JsonObject result = source.deepCopy();
        result.getAsJsonObject("slip_data").add(field, JsonParser.parseString(json));
        return result;
    }
}
