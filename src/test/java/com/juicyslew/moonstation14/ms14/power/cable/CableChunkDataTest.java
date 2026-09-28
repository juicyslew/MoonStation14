package com.juicyslew.moonstation14.ms14.power.cable;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CableChunkDataTest {
    @Test void tiersAreIndependentPerFaceAndRoundTrip() {
        CableChunkData data = new CableChunkData();
        for (Direction face : Direction.values()) assertTrue(data.put(2, -17, 4, face, CableTier.HV));
        assertTrue(data.put(2, -17, 4, Direction.UP, CableTier.MV));
        assertTrue(data.put(2, -17, 4, Direction.UP, CableTier.APC));
        assertFalse(data.put(2, -17, 4, Direction.UP, CableTier.APC));
        assertEquals(8, data.size());
        var encoded = CableChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        CableChunkData decoded = CableChunkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(data.snapshot(), decoded.snapshot());
        assertNull(decoded.get(2, -17, 4, Direction.UP));
        assertTrue(decoded.contains(2, -17, 4, Direction.UP, CableTier.MV));
        assertTrue(decoded.remove(2, -17, 4, Direction.UP, CableTier.MV));
        assertTrue(decoded.contains(2, -17, 4, Direction.UP, CableTier.APC));
    }

    @Test void legacyUpgradeAndDuplicateRulesAreVersionSpecific() {
        var legacy = com.google.gson.JsonParser.parseString("""
                {"version":1,"records":[{"x":1,"y":3,"z":2,"face":"UP","tier":"hv","host":"minecraft:stone"}]}
                """);
        CableChunkData upgraded = CableChunkData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        var upgradedJson = CableChunkData.CODEC.encodeStart(JsonOps.INSTANCE, upgraded).getOrThrow();
        assertEquals(2, upgradedJson.getAsJsonObject().get("version").getAsInt());
        assertEquals(CableTier.HV, CableChunkData.CODEC.parse(JsonOps.INSTANCE, upgradedJson).getOrThrow().get(1, 3, 2, Direction.UP));
        var duplicate = com.google.gson.JsonParser.parseString("""
                {"version":1,"records":[
                  {"x":1,"y":3,"z":2,"face":"UP","tier":"hv"},
                  {"x":1,"y":3,"z":2,"face":"UP","tier":"mv"}]}
                """);
        assertTrue(CableChunkData.CODEC.parse(JsonOps.INSTANCE, duplicate).error().isPresent());
        var v2 = com.google.gson.JsonParser.parseString("""
                {"version":2,"records":[
                  {"x":1,"y":3,"z":2,"face":"UP","tier":"hv"},
                  {"x":1,"y":3,"z":2,"face":"UP","tier":"mv"},
                  {"x":1,"y":3,"z":2,"face":"UP","tier":"apc"}]}
                """);
        assertEquals(3, CableChunkData.CODEC.parse(JsonOps.INSTANCE, v2).getOrThrow().size());
        var wrongVersion = com.google.gson.JsonParser.parseString("{\"version\":4,\"records\":[]}");
        assertTrue(CableChunkData.CODEC.parse(JsonOps.INSTANCE, wrongVersion).error().isPresent());
    }

    @Test void chunkStorageIsBoundedAndHostRemovalClearsAllFaces() {
        CableChunkData data = new CableChunkData();
        for (Direction face : Direction.values()) assertTrue(data.put(0, 0, 0, face, CableTier.MV));
        for (int n = 6; n < CableChunkData.MAX_RECORDS; n++) {
            assertTrue(data.put(n & 15, n, (n >> 4) & 15, Direction.values()[n % 6], CableTier.MV));
        }
        assertFalse(data.put(15, 10000, 15, Direction.UP, CableTier.HV));
        assertEquals(0, data.removeHost(15, 100, 15));
        assertEquals(6, data.removeHost(0, 0, 0));
        assertEquals(CableChunkData.MAX_RECORDS - 6, data.size());
    }
}
