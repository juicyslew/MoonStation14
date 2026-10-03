package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipeChunkDataTest {
    private static final UUID ID = UUID.fromString("015e9a8e-1dbf-42c0-92fa-c440b73f1bc1");
    private static PipeChunkData.Record exposed(int x, int y, int z) {
        UUID id = UUID.nameUUIDFromBytes((x + ":" + y + ":" + z).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new PipeChunkData.Record(x, y, z, id, PipeTopology.Kind.EXPOSED,
                PipeChunkData.Record.portMask(List.of(Direction.NORTH, Direction.SOUTH)), 1.25, "", "");
    }

    @Test void jsonRoundTripAndNegativeY() {
        PipeChunkData data = new PipeChunkData();
        assertTrue(data.add(exposed(4, -64, 2)));
        JsonObject json = PipeChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        PipeChunkData decoded = PipeChunkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(data.snapshot(), decoded.snapshot());
        assertEquals(-64, decoded.snapshot().getFirst().y());
    }

    @Test void migratesLegacyAndPersistsIndependentLayers() {
        String legacy = "{\"version\":1,\"records\":[{\"x\":4,\"y\":-64,\"z\":2,\"id\":\"" + ID
                + "\",\"kind\":\"EXPOSED\",\"ports\":12,\"volume\":1.25}]}";
        PipeChunkData migrated = PipeChunkData.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(legacy)).getOrThrow();
        assertEquals(PipeTopology.Layer.PRIMARY, migrated.snapshot().getFirst().layer());
        PipeChunkData lanes = new PipeChunkData();
        assertTrue(lanes.add(exposed(4, -64, 2)));
        assertTrue(lanes.add(new PipeChunkData.Record(4, -64, 2, UUID.randomUUID(), PipeTopology.Kind.EXPOSED,
                10, 2, "", "", PipeTopology.Layer.SECONDARY)));
        assertTrue(lanes.add(new PipeChunkData.Record(4, -64, 2, UUID.randomUUID(), PipeTopology.Kind.EXPOSED,
                10, 3, "", "", PipeTopology.Layer.TERTIARY)));
        var encoded = PipeChunkData.CODEC.encodeStart(JsonOps.INSTANCE, lanes).getOrThrow().getAsJsonObject();
        assertTrue(encoded.getAsJsonArray("records").get(1).getAsJsonObject().has("layer"));
        PipeChunkData copy = PipeChunkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(lanes.snapshot(), copy.snapshot());
        assertTrue(copy.remove(4, -64, 2, PipeTopology.Layer.SECONDARY));
        assertNotNull(copy.get(4, -64, 2, PipeTopology.Layer.PRIMARY));
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                legacy.replace("\"kind\":", "\"layer\":\"FOURTH\",\"kind\":"))).error().isPresent());
    }

    @Test void schemaVersionControlsLayerPresenceAndUniqueness() {
        String primary = recordJson(encoded(exposed(4, -64, 2)));
        JsonObject v2Primary = encoded(exposed(4, -64, 2));
        assertEquals("PRIMARY", v2Primary.getAsJsonArray("records").get(0).getAsJsonObject().get("layer").getAsString());
        assertEquals(PipeTopology.Layer.PRIMARY, PipeChunkData.CODEC.parse(JsonOps.INSTANCE, v2Primary).getOrThrow().snapshot().getFirst().layer());
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, document(2, primary.replace(",\"layer\":\"PRIMARY\"", ""))).error().isPresent());
        String explicitTertiary = primary.replace("\"layer\":\"PRIMARY\",", "\"layer\":\"TERTIARY\",");
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, document(1, explicitTertiary)).error().isPresent());

        String secondary = primary.replace("\"layer\":\"PRIMARY\"", "\"layer\":\"SECONDARY\"")
                .replaceFirst("\"id\":\"[^\"]+\"", "\"id\":\"" + UUID.randomUUID() + "\"");
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, document(2, primary + "," + primary)).error().isPresent());
        assertEquals(2, PipeChunkData.CODEC.parse(JsonOps.INSTANCE, document(2, primary + "," + secondary)).getOrThrow().size());
    }

    @Test void rejectsMalformedAndDuplicateDataAtomically() {
        JsonObject valid = encoded(exposed(1, 2, 3));
        JsonObject duplicate = new JsonObject(); duplicate.addProperty("version", 1);
        duplicate.add("records", com.google.gson.JsonParser.parseString("[" + recordJson(valid) + "," + recordJson(valid) + "]"));
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, duplicate).error().isPresent());
        for (String patch : List.of("\"version\":99", "\"ports\":64", "\"volume\":0", "\"volume\":1e999",
                "\"kind\":\"UNKNOWN\"", "\"x\":16")) {
            JsonObject malformed = encoded(exposed(1, 2, 3));
            String input = malformed.toString();
            if (patch.startsWith("\"version")) input = input.replace("\"version\":" + PipeChunkData.SCHEMA_VERSION, patch);
            else input = input.replaceFirst("\"(ports|volume)\":(-?\\d+(?:\\.\\d+)?(?:E[-+]?\\d+)?)", patch);
            assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(input)).error().isPresent(), patch);
        }
        JsonObject oversized = new JsonObject(); oversized.addProperty("version", 1);
        List<com.google.gson.JsonElement> records = new ArrayList<>();
        for (int i = 0; i <= PipeChunkData.MAX_RECORDS; i++) records.add(com.google.gson.JsonParser.parseString(recordJson(encoded(exposed(i & 15, i >> 4, (i >> 8) & 15)))));
        var array = new com.google.gson.JsonArray(); records.forEach(array::add); oversized.add("records", array);
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, oversized).error().isPresent());
    }

    @Test void separatesWallValidationAndSnapshotsAreImmutableDeterministic() {
        int opposite = PipeChunkData.Record.portMask(List.of(Direction.EAST, Direction.WEST));
        assertThrows(IllegalArgumentException.class, () -> new PipeChunkData.Record(0, 0, 0, ID,
                PipeTopology.Kind.STRAIGHT_SEALED_WALL, PipeChunkData.Record.portMask(List.of(Direction.EAST, Direction.NORTH)), 1, "minecraft:stone", "inc-1"));
        PipeChunkData data = new PipeChunkData();
        assertTrue(data.add(new PipeChunkData.Record(2, 0, 1, ID, PipeTopology.Kind.STRAIGHT_SEALED_WALL,
                opposite, 1, "minecraft:stone", "74d9a515-73a9-4b8d-a414-829acf2161ee")));
        assertTrue(data.add(exposed(1, 100, 0)));
        List<PipeChunkData.Record> snapshot = data.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(exposed(3, 0, 0)));
        assertEquals(1, snapshot.getFirst().x());
        assertTrue(data.remove(1, 100, 0));
        assertEquals(2, snapshot.size());
    }

    @Test void validatesWallHostHintsAndUniqueIds() {
        int opposite = PipeChunkData.Record.portMask(List.of(Direction.EAST, Direction.WEST));
        assertThrows(IllegalArgumentException.class, () -> new PipeChunkData.Record(0, 0, 0, ID,
                PipeTopology.Kind.STRAIGHT_SEALED_WALL, opposite, 1, "", ""));
        UUID incarnation = UUID.fromString("74d9a515-73a9-4b8d-a414-829acf2161ee");
        var wall = new PipeChunkData.Record(0, 0, 0, ID, PipeTopology.Kind.STRAIGHT_SEALED_WALL,
                opposite, 1, "minecraft:stone", incarnation.toString());
        PipeChunkData data = new PipeChunkData();
        assertTrue(data.add(wall));
        assertFalse(data.add(new PipeChunkData.Record(1, 0, 0, ID, PipeTopology.Kind.EXPOSED,
                opposite, 1, "", "")));
        assertEquals(List.of(wall), PipeChunkData.CODEC.parse(JsonOps.INSTANCE,
                PipeChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow().snapshot());

        for (String host : List.of(" ", "bad host", "a".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> new PipeChunkData.Record(0, 0, 0, ID,
                    PipeTopology.Kind.STRAIGHT_SEALED_WALL, opposite, 1, host, incarnation.toString()));
        }
        assertThrows(IllegalArgumentException.class, () -> new PipeChunkData.Record(0, 0, 0, ID,
                PipeTopology.Kind.STRAIGHT_SEALED_WALL, opposite, 1, "minecraft:stone", "inc-1"));
    }

    @Test void codecRejectsDuplicateIdsAtDifferentCoordinatesAndBadWallHints() {
        JsonObject record = encoded(exposed(1, 2, 3));
        JsonObject duplicate = new JsonObject(); duplicate.addProperty("version", 1);
        duplicate.add("records", com.google.gson.JsonParser.parseString("[" + recordJson(record) + "," +
                recordJson(encoded(new PipeChunkData.Record(2, 2, 3, exposed(1, 2, 3).id(), PipeTopology.Kind.EXPOSED,
                        exposed(1, 2, 3).ports(), 1.25, "", ""))) + "]"));
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, duplicate).error().isPresent());
        JsonObject wall = encoded(new PipeChunkData.Record(1, 2, 3, ID, PipeTopology.Kind.STRAIGHT_SEALED_WALL,
                PipeChunkData.Record.portMask(List.of(Direction.EAST, Direction.WEST)), 1,
                "minecraft:stone", UUID.randomUUID().toString()));
        String raw = recordJson(wall).replace("\"host\":\"minecraft:stone\"", "\"host\":\" \"");
        JsonObject malformed = new JsonObject(); malformed.addProperty("version", 1);
        malformed.add("records", com.google.gson.JsonParser.parseString("[" + raw + "]"));
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent());
    }

    @Test void malformedCodecRecordsReturnErrorsWithoutEscaping() {
        JsonObject wall = encoded(new PipeChunkData.Record(1, 2, 3, ID, PipeTopology.Kind.STRAIGHT_SEALED_WALL,
                PipeChunkData.Record.portMask(List.of(Direction.EAST, Direction.WEST)), 1,
                "minecraft:stone", UUID.randomUUID().toString()));
        String validWall = recordJson(wall);
        for (String raw : List.of(
                validWall.replace(",\"host\":\"minecraft:stone\"", ""),
                validWall.replace("\"host\":\"minecraft:stone\"", "\"host\":\"\""),
                validWall.replaceFirst("\"host_incarnation\":\"[^\"]+\"", "\"host_incarnation\":\"inc-1\""),
                validWall.replaceFirst("\"ports\":\\d+", "\"ports\":5"),
                validWall.replaceFirst("\"ports\":\\d+", "\"ports\":64"))) {
            assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, document(1, raw)).error().isPresent(), raw);
        }

        JsonObject valid = encoded(exposed(1, 2, 3));
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, document(99, recordJson(valid))).error().isPresent());
        String duplicate = recordJson(valid);
        assertTrue(PipeChunkData.CODEC.parse(JsonOps.INSTANCE, document(1, duplicate + "," + duplicate)).error().isPresent());
    }

    private static JsonObject document(int version, String records) {
        return com.google.gson.JsonParser.parseString("{\"version\":" + version + ",\"records\":[" + records + "]}").getAsJsonObject();
    }

    private static JsonObject encoded(PipeChunkData.Record record) {
        PipeChunkData data = new PipeChunkData(); assertTrue(data.add(record));
        return PipeChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
    }
    private static String recordJson(JsonObject data) { return data.getAsJsonArray("records").get(0).toString(); }
}
