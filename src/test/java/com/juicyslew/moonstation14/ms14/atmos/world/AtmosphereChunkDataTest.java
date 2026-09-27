package com.juicyslew.moonstation14.ms14.atmos.world;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereChunkDataTest {
    @Test
    void absentStateIsSparseAndMutationsOnlyReportChanges() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.breathableAir();
        assertNull(data.get(3, -64, 4));
        assertEquals(0, data.size());
        assertFalse(data.put(3, -64, 4, ambient, GasMixture.breathableAir()));
        GasMixture changed = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 310.0);
        assertTrue(data.put(3, -64, 4, changed, ambient));
        assertFalse(data.put(3, -64, 4, changed, ambient));
        assertEquals(changed.gasMoles(), data.get(3, -64, 4).gasMoles());
        assertTrue(data.put(3, -64, 4, ambient, ambient));
        assertNull(data.get(3, -64, 4));
        assertEquals(0, data.size());
    }

    @Test
    void roundTripsSparseOverridesWithStableGasIdsAndSignedY() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        data.put(15, -20, 0, new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 0.75, GasType.FREZON, 1.25), 290.5), GasMixture.vacuum());
        var encoded = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        String json = encoded.toString();
        assertTrue(json.contains("carbon_dioxide"));
        assertTrue(json.contains("frezon"));
        AtmosphereChunkData decoded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        var restored = decoded.get(15, -20, 0);
        assertEquals(data.get(15, -20, 0).gasMoles(), restored.gasMoles());
        assertEquals(data.get(15, -20, 0).temperatureKelvin(), restored.temperatureKelvin());
        assertEquals(-20, decoded.entries().keySet().iterator().next().y());
    }

    @Test
    void incrementalCursorTraversesLargeSparseMapInDeterministicOrder() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.vacuum();
        GasMixture changed = mixture(300.0);
        for (int y = -2; y < 2; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) data.put(x, y, z, changed, ambient);
            }
        }

        int visited = 0;
        AtmosphereChunkData.CellPosition cursor = null;
        while (true) {
            Optional<Map.Entry<AtmosphereChunkData.CellPosition, GasMixture>> next = data.nextAfter(cursor);
            if (next.isEmpty()) break;
            AtmosphereChunkData.CellPosition position = next.orElseThrow().getKey();
            if (cursor != null) assertTrue(compare(cursor, position) < 0);
            cursor = position;
            visited++;
        }
        assertEquals(1024, visited);
    }

    @Test
    void incrementalCursorRemainsUsableAcrossMutations() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.vacuum();
        GasMixture original = mixture(300.0);
        GasMixture replacement = mixture(320.0);
        data.put(1, -5, 0, original, ambient);
        data.put(2, -5, 0, original, ambient);
        data.put(3, -5, 0, original, ambient);

        var first = data.nextAfter(null).orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> first.setValue(replacement));
        AtmosphereChunkData.CellPosition cursor = first.getKey();
        data.put(0, -5, 0, original, ambient); // Before the cursor: picked up on the next full pass.
        data.put(1, -5, 0, ambient, ambient); // Deleting the cursor cell does not invalidate the cursor.
        data.put(2, -5, 0, replacement, ambient);

        var next = data.nextAfter(cursor).orElseThrow();
        assertEquals(new AtmosphereChunkData.CellPosition(2, -5, 0), next.getKey());
        assertEquals(replacement.temperatureKelvin(), next.getValue().temperatureKelvin());
        assertEquals(new AtmosphereChunkData.CellPosition(3, -5, 0), data.nextAfter(next.getKey()).orElseThrow().getKey());

        var beginning = data.nextAfter(null).orElseThrow();
        assertEquals(new AtmosphereChunkData.CellPosition(0, -5, 0), beginning.getKey());
    }

    @Test
    void cursorOrderingAndCodecRoundTripSupportNegativeY() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.vacuum();
        data.put(15, -20, 0, mixture(290.5), ambient);
        data.put(0, -21, 15, mixture(291.5), ambient);

        var encoded = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        AtmosphereChunkData decoded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        var first = decoded.nextAfter(null).orElseThrow();
        assertEquals(new AtmosphereChunkData.CellPosition(0, -21, 15), first.getKey());
        assertEquals(new AtmosphereChunkData.CellPosition(15, -20, 0), decoded.nextAfter(first.getKey()).orElseThrow().getKey());
        assertTrue(decoded.nextAfter(new AtmosphereChunkData.CellPosition(15, -20, 0)).isEmpty());
    }

    @Test
    void ambientFiniteClaimsPersistIndependentlyWithoutMaterializingGas() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        assertFalse(data.isFiniteClaimed(2, -64, 3));
        assertTrue(data.claimFinite(2, -64, 3));
        assertFalse(data.claimFinite(2, -64, 3));
        assertTrue(data.isFiniteClaimed(2, -64, 3));
        assertNull(data.get(2, -64, 3));
        assertEquals(0, data.size());
        assertEquals(1, data.claimCount());
        assertTrue(data.hasPersistedState());

        var encoded = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        assertEquals(2, encoded.getAsJsonObject().get("version").getAsInt());
        assertTrue(encoded.getAsJsonObject().has("finite_cells"));
        assertFalse(encoded.getAsJsonObject().getAsJsonArray("cells").iterator().hasNext());
        AtmosphereChunkData decoded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertTrue(decoded.isFiniteClaimed(2, -64, 3));
        assertNull(decoded.get(2, -64, 3));
        assertEquals(List.of(new AtmosphereChunkData.CellPosition(2, -64, 3)), decoded.finiteClaimSnapshot());
        assertThrows(UnsupportedOperationException.class,
                () -> decoded.finiteClaimSnapshot().add(new AtmosphereChunkData.CellPosition(0, 0, 0)));
    }

    @Test
    void legacyGasCellsBecomeFiniteClaimsWithoutLosingGas() {
        String legacy = "{version:1,cells:[{x:4,y:-32,z:9,temperature:290,gases:[{id:'oxygen',moles:1.5}]}]}";
        AtmosphereChunkData decoded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(legacy))
                .result().orElseThrow();
        assertTrue(decoded.isFiniteClaimed(4, -32, 9));
        assertEquals(1.5, decoded.get(4, -32, 9).gasMoles().get(GasType.OXYGEN));
        assertTrue(decoded.hasPersistedState());
        var upgraded = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).result().orElseThrow();
        assertEquals(2, upgraded.getAsJsonObject().get("version").getAsInt());
        assertEquals(1, upgraded.getAsJsonObject().getAsJsonArray("finite_cells").size());
    }

    @Test
    void finiteClaimsValidateBatchBeforeMutationAndSortDeterministically() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        assertThrows(IllegalArgumentException.class, () -> data.claimFiniteAll(List.of(
                new AtmosphereChunkData.CellPosition(1, 0, 1),
                new AtmosphereChunkData.CellPosition(16, 0, 1))));
        assertEquals(0, data.claimCount());
        assertTrue(data.claimFiniteAll(List.of(
                new AtmosphereChunkData.CellPosition(1, 0, 2),
                new AtmosphereChunkData.CellPosition(0, -1, 9),
                new AtmosphereChunkData.CellPosition(1, -2, 2))));
        assertEquals(List.of(new AtmosphereChunkData.CellPosition(0, -1, 9),
                new AtmosphereChunkData.CellPosition(1, -2, 2),
                new AtmosphereChunkData.CellPosition(1, 0, 2)), data.finiteClaimSnapshot());
        assertFalse(data.claimFiniteAll(List.of(new AtmosphereChunkData.CellPosition(1, 0, 2))));
    }

    @Test
    void columnSnapshotSelectsOnlyOneColumnAndTracksMutationsAndCodecDecode() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.vacuum();
        GasMixture changed = mixture(300.0);
        for (int y = -40; y <= 40; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) data.put(x, y, z, changed, ambient);
            }
        }

        List<AtmosphereChunkData.CellPosition> column = data.columnSnapshot(7, 11);
        assertEquals(81, column.size());
        assertEquals(new AtmosphereChunkData.CellPosition(7, -40, 11), column.get(0));
        assertEquals(new AtmosphereChunkData.CellPosition(7, 40, 11), column.get(column.size() - 1));
        assertTrue(column.stream().allMatch(cell -> cell.x() == 7 && cell.z() == 11));
        assertThrows(UnsupportedOperationException.class,
                () -> column.add(new AtmosphereChunkData.CellPosition(7, 41, 11)));

        data.put(7, 0, 11, ambient, ambient);
        data.put(7, 41, 11, changed, ambient);
        assertFalse(data.columnSnapshot(7, 11).contains(new AtmosphereChunkData.CellPosition(7, 0, 11)));
        assertEquals(new AtmosphereChunkData.CellPosition(7, 41, 11), data.columnSnapshot(7, 11).get(80));
        assertEquals(81, column.size()); // Previously returned snapshots stay detached.

        var encoded = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        AtmosphereChunkData decoded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(data.columnSnapshot(7, 11), decoded.columnSnapshot(7, 11));
    }

    private static int compare(AtmosphereChunkData.CellPosition left, AtmosphereChunkData.CellPosition right) {
        int x = Integer.compare(left.x(), right.x());
        if (x != 0) return x;
        int z = Integer.compare(left.z(), right.z());
        return z != 0 ? z : Integer.compare(left.y(), right.y());
    }

    private static GasMixture mixture(double temperature) {
        return new GasMixture(Map.of(GasType.OXYGEN, 2.0), temperature);
    }

    @Test
    void codecRejectsUnsupportedVersionsInvalidValuesAndDuplicateCoordinatesOrGasIds() {
        assertInvalid("{version:3,cells:[],finite_cells:[]}");
        assertInvalid("{version:2,cells:[],finite_cells:[{x:0,y:0,z:0},{x:0,y:0,z:0}]}");
        assertInvalid("{version:2,cells:[],finite_cells:[{x:16,y:0,z:0}]}");
        assertInvalid("{version:2,cells:[],finite_cells:[{x:0,y:0,z:-1}]}");
        assertInvalid("{version:2,cells:[],finite_cells:[{x:0,y:2147483648,z:0}]}");
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:-1,gases:[]}]}" );
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:300,gases:[{id:'unknown',moles:1}]}]}" );
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:300,gases:[{id:'oxygen',moles:1},{id:'oxygen',moles:2}]}]}" );
        assertInvalid("{version:1,cells:[{x:0,y:0,z:0,temperature:300,gases:[]},{x:0,y:0,z:0,temperature:300,gases:[]}]}" );
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereChunkData().get(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereChunkData().get(0, 0, 16));
    }

    private static void assertInvalid(String json) {
        assertTrue(AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
    }
}
