package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereVisualServerHooksTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void pendingPositionsDeduplicateAndOverflowFallsBackToSnapshot() {
        var pending = new AtmosphereVisualServerHooks.PendingChanges();
        ChunkPos chunk = new ChunkPos(2, -3);
        for (int i = 0; i < 500; i++) {
            BlockPos pos = new BlockPos(i & 15, i, (i >> 4) & 15);
            pending.offer(chunk, pos);
            pending.offer(chunk, pos);
        }
        assertFalse(pending.isSnapshot(chunk));
        assertEquals(500, pending.take(chunk).size());
        for (int i = 0; i < 10_000; i++) pending.offer(chunk, new BlockPos(i & 15, i, (i >> 4) & 15));
        assertTrue(pending.isSnapshot(chunk));
        assertEquals(0, pending.take(chunk).size());
        assertEquals(chunk, pending.next());
    }

    @Test
    void snapshotsSplitAtCellLimitWithSingleRevisionAndCorrectResetFinalFlags() {
        List<AtmosphereVisualPayload.VisualCell> cells = new ArrayList<>();
        for (int i = 0; i < 600; i++) cells.add(new AtmosphereVisualPayload.VisualCell(
                i & 15, i, (i >> 4) & 15, 255, 0, 0, 0, 0));
        var packets = AtmosphereVisualServerHooks.packetize(DIMENSION, new ChunkPos(-1, 4), 17, cells);
        assertEquals(3, packets.size());
        assertEquals(256, packets.get(0).cells().size());
        assertEquals(88, packets.get(2).cells().size());
        assertTrue(packets.get(0).resetSnapshot());
        assertFalse(packets.get(1).resetSnapshot());
        assertFalse(packets.get(0).finalPacket());
        assertFalse(packets.get(1).finalPacket());
        assertTrue(packets.get(2).finalPacket());
        assertTrue(packets.stream().allMatch(packet -> packet.revision() == 17));
    }

    @Test
    void ordinaryProducerDeltaRetainsOtherGasVisualsAndRemovalClearsOnlyItsCell() {
        AtmosphereVisualClientCache cache = new AtmosphereVisualClientCache();
        ChunkPos chunk = new ChunkPos(0, 0);
        var initial = List.of(cell(0, 10, 0, 255, 0), cell(1, 10, 0, 0, 255), cell(2, 10, 0, 0, 200));
        for (var snapshotPacket : AtmosphereVisualServerHooks.packetize(DIMENSION, chunk, 1, initial))
            cache.apply(snapshotPacket);

        // Equivalent to two gas-producer updates in one chunk: one overlay changes, then that cell is cleared.
        var change = AtmosphereVisualServerHooks.packetizeDelta(DIMENSION, chunk, 2, List.of(cell(0, 10, 0, 100, 0)));
        assertEquals(1, change.size());
        assertFalse(change.get(0).resetSnapshot());
        assertTrue(change.get(0).finalPacket());
        change.forEach(cache::apply);
        assertEquals(3, cache.cells(DIMENSION, 0, 0).size());
        assertEquals(255, cache.cells(DIMENSION, 0, 0).get(new AtmosphereVisualClientCache.CellKey(1, 10, 0)).tritiumAlpha());
        assertEquals(100, cache.cells(DIMENSION, 0, 0).get(new AtmosphereVisualClientCache.CellKey(0, 10, 0)).plasmaAlpha());

        var removal = AtmosphereVisualServerHooks.packetizeDelta(DIMENSION, chunk, 3, List.of(cell(1, 10, 0, 0, 0)));
        assertFalse(removal.get(0).resetSnapshot());
        removal.forEach(cache::apply);
        assertEquals(2, cache.cells(DIMENSION, 0, 0).size());
        assertFalse(cache.cells(DIMENSION, 0, 0).containsKey(new AtmosphereVisualClientCache.CellKey(1, 10, 0)));
        assertTrue(cache.cells(DIMENSION, 0, 0).containsKey(new AtmosphereVisualClientCache.CellKey(2, 10, 0)));
    }

    @Test
    void largeDeltaSplitsIntoFinalNonResetPacketsAndAppliesEveryCell() {
        AtmosphereVisualClientCache cache = new AtmosphereVisualClientCache();
        cache.apply(new AtmosphereVisualPayload(DIMENSION, 0, 0, 1, true, true, List.of()));
        List<AtmosphereVisualPayload.VisualCell> cells = new ArrayList<>();
        for (int y = 0; y < 600; y++) cells.add(cell(0, y, 0, 255, 0));
        var packets = AtmosphereVisualServerHooks.packetizeDelta(DIMENSION, new ChunkPos(0, 0), 2, cells);
        assertEquals(3, packets.size());
        assertEquals(List.of(256, 256, 88), packets.stream().map(packet -> packet.cells().size()).toList());
        assertTrue(packets.stream().allMatch(packet -> !packet.resetSnapshot() && packet.finalPacket()
                && packet.revision() == 2 && packet.cells().size() <= AtmosphereVisualPayload.MAX_CELLS));
        packets.forEach(cache::apply);
        assertEquals(600, cache.cells(DIMENSION, 0, 0).size());
        assertTrue(AtmosphereVisualServerHooks.packetizeDelta(DIMENSION, new ChunkPos(0, 0), 3, List.of()).isEmpty());
    }

    private static AtmosphereVisualPayload.VisualCell cell(int x, int y, int z, int plasma, int tritium) {
        return new AtmosphereVisualPayload.VisualCell(x, y, z, plasma, tritium, 0, 0, 0);
    }

    @Test
    void emptySnapshotIsAnExplicitClearAndOversizedMutationSetCanBeCoalesced() {
        var empty = AtmosphereVisualServerHooks.packetize(DIMENSION, new ChunkPos(0, 0), 1, List.of());
        assertEquals(1, empty.size());
        assertTrue(empty.get(0).resetSnapshot());
        assertTrue(empty.get(0).finalPacket());
        assertTrue(empty.get(0).cells().isEmpty());

        var pending = new AtmosphereVisualServerHooks.PendingChanges();
        ChunkPos chunk = new ChunkPos(0, 0);
        for (int i = 0; i <= AtmosphereVisualServerHooks.MAX_CHANGED_POSITIONS_PER_CHUNK; i++)
            pending.offer(chunk, new BlockPos(i & 15, i, (i >> 4) & 15));
        assertTrue(pending.isSnapshot(chunk));
        pending.snapshot(chunk);
        assertTrue(pending.isSnapshot(chunk));
    }

    @Test
    void watchedDirtyChunksBeyondFormerLimitAreRetained() {
        var pending = new AtmosphereVisualServerHooks.PendingChanges();
        for (int i = 0; i < 1025; i++) assertTrue(pending.offer(new ChunkPos(i, 0), new BlockPos(i * 16, 1, 0)));
        int count = 0;
        while (pending.next() != null) { pending.remove(pending.next()); count++; }
        assertEquals(1025, count);
    }

    @Test
    void sparseStreamingFindsVisibleGasAfterMoreThanTwentyThousandOverrides() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture gas = new GasMixture(Map.of(GasType.TRITIUM, 10.0), 293.15);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -50; y <= 50; y++)
            data.put(x, y, z, gas, GasMixture.vacuum());
        AtmosphereChunkData.CellPosition cursor = null;
        int probes = 0;
        boolean sawLateTritium = false;
        while (true) {
            var next = data.nextAfter(cursor);
            if (next.isEmpty()) break;
            cursor = next.orElseThrow().getKey();
            probes++;
            if (cursor.x() == 15 && cursor.z() == 15 && cursor.y() == 50) {
                var visual = AtmosphereVisualPayload.VisualCell.from(next.orElseThrow().getValue(),
                        new BlockPos(cursor.x(), cursor.y(), cursor.z()));
                sawLateTritium = visual.tritiumAlpha() > 0;
            }
        }
        assertEquals(25_856, probes);
        assertTrue(sawLateTritium);
    }

    @Test
    void resyncAuthorizationIsPerWatcherAndRateLimitedPerPlayer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<UUID> watchers = Set.of(first, second);
        assertTrue(AtmosphereVisualServerHooks.isAuthorizedWatcher(watchers, first));
        assertTrue(AtmosphereVisualServerHooks.isAuthorizedWatcher(watchers, second));
        assertFalse(AtmosphereVisualServerHooks.isAuthorizedWatcher(watchers, UUID.randomUUID()));
        assertFalse(AtmosphereVisualServerHooks.isAuthorizedWatcher(null, first));

        var transfers = new java.util.LinkedHashMap<UUID, AtmosphereVisualServerHooks.SnapshotTransfer>();
        transfers.put(second, new AtmosphereVisualServerHooks.SnapshotTransfer(8));
        AtmosphereVisualServerHooks.queueRequesterSnapshot(transfers, first, 9);
        assertEquals(Set.of(first, second), transfers.keySet());
        assertEquals(9, transfers.get(first).revision);
        assertEquals(8, transfers.get(second).revision);

        var firstLimit = new AtmosphereVisualServerHooks.RequestRateLimit();
        var secondLimit = new AtmosphereVisualServerHooks.RequestRateLimit();
        assertTrue(firstLimit.allow(100));
        assertTrue(firstLimit.allow(101));
        assertFalse(firstLimit.allow(102));
        assertTrue(secondLimit.allow(102));
        assertTrue(firstLimit.allow(200));
        assertTrue(firstLimit.allow(201));
        assertFalse(firstLimit.allow(202));
    }
}
