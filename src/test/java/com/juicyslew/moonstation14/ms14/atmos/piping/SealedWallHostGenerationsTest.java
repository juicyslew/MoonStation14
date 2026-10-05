package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.sealedwall.SealedWallHostGenerations;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeChunkData;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SealedWallHostGenerationsTest {
    @Test
    void codecPreservesTombstonesAndVersion() {
        long position = new BlockPos(2, 64, 3).asLong();
        var original = new SealedWallHostGenerations();
        original.register(position, UUID.randomUUID());
        original.invalidate(position);

        var encoded = SealedWallHostGenerations.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(1, encoded.getAsJsonObject().get("version").getAsInt());
        var decoded = SealedWallHostGenerations.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(decoded.hasPersistedState());
        assertTrue(decoded.active(position).isEmpty());
        assertTrue(SealedWallHostGenerations.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()
                .getAsJsonObject().getAsJsonArray("records").get(0).getAsJsonObject()
                .get("state").getAsString().equals("invalidated"));
    }

    @Test
    void codecRejectsMalformedAndDuplicateRecords() {
        assertTrue(SealedWallHostGenerations.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{version:1,records:[{position:1,state:'invalidated',generation:'bad'}]}")).error().isPresent());
        assertTrue(SealedWallHostGenerations.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{version:1,records:[{position:1,state:'invalidated'},{position:1,state:'invalidated'}]}")).error().isPresent());
        assertTrue(SealedWallHostGenerations.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{version:7,records:[]}")).error().isPresent());
    }

    @Test
    void unrelatedInvalidationDoesNotCreateRecordAndCapacityIsBounded() {
        var records = new SealedWallHostGenerations();
        assertFalse(records.invalidateExisting(44));
        assertFalse(records.hasRecord(44));
        assertTrue(records.registerIfAbsent(44, UUID.randomUUID()));
        assertFalse(records.invalidateExisting(45));
        assertTrue(records.hasRecord(44));
        assertFalse(records.hasRecord(45));

        for (int i = 1; i < PipeChunkData.MAX_RECORDS; i++)
            assertTrue(records.registerIfAbsent(10_000L + i, UUID.randomUUID()));
        assertFalse(records.registerIfAbsent(20_000L, UUID.randomUUID()));
        assertFalse(records.register(20_000L, UUID.randomUUID()));
    }

    @Test
    void codecRejectsExcessRecords() {
        var full = new SealedWallHostGenerations();
        for (int i = 0; i < PipeChunkData.MAX_RECORDS; i++) full.register(i, UUID.randomUUID());
        var json = SealedWallHostGenerations.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow();
        var records = json.getAsJsonObject().getAsJsonArray("records");
        records.add(records.get(0).deepCopy());
        assertTrue(SealedWallHostGenerations.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
