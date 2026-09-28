package com.juicyslew.moonstation14.ms14.power.cable;

import com.juicyslew.moonstation14.ms14.power.cable.client.CableFacePresentation;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualAdjacency;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualClientCache;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualPendingSnapshots;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualGeometry;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualPresentation;
import com.juicyslew.moonstation14.ms14.power.cable.client.CableVisualResyncScheduler;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualPayload;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class CableVisualSyncTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @Test void payloadBoundsAndOnlyDuplicateFullNodesAreRejected() {
        var record = new CableChunkData.Record(1, -20, 2, Direction.UP, CableTier.HV);
        assertThrows(IllegalArgumentException.class, () -> new CableVisualPayload(DIMENSION, 0, 0, 1, true,
                java.util.Collections.nCopies(CableVisualPayload.MAX_RECORDS + 1, record)));
        assertThrows(IllegalArgumentException.class, () -> new CableVisualPayload(DIMENSION, 0, 0, 1, true,
                List.of(record, record)));
        var sharedMv = new CableChunkData.Record(1, -20, 2, Direction.UP, CableTier.MV);
        var sharedApc = new CableChunkData.Record(1, -20, 2, Direction.UP, CableTier.APC);
        var sharedFace = new CableVisualPayload(DIMENSION, 0, 0, 1, true,
                List.of(record, sharedMv, sharedApc));
        assertEquals(List.of(record, sharedMv, sharedApc), sharedFace.records());
        assertThrows(IllegalArgumentException.class, () -> new CableVisualPayload(DIMENSION, 0, 0, 0, true, List.of()));
    }

    @Test void encodedByteEstimateAccountsForAllEightBytesPerRecord() {
        int emptyEstimate = new CableVisualPayload(DIMENSION, 0, 0, 1, true, List.of())
                .estimatedEncodedBytes();
        var record = new CableChunkData.Record(1, -20, 2, Direction.UP, CableTier.HV);
        int oneRecordEstimate = new CableVisualPayload(DIMENSION, 0, 0, 1, true, List.of(record))
                .estimatedEncodedBytes();
        assertTrue(emptyEstimate > 0, "the estimate includes a conservative packet header");
        assertEquals(8, oneRecordEstimate - emptyEstimate,
                "x byte, y int, z byte, face byte, and tier byte total eight bytes");

        List<CableChunkData.Record> full = new java.util.ArrayList<>(CableVisualPayload.MAX_RECORDS);
        for (int i = 0; i < CableVisualPayload.MAX_RECORDS; i++)
            full.add(new CableChunkData.Record(i & 15, i, i >>> 4 & 15, Direction.NORTH, CableTier.MV));
        int maxEstimate = new CableVisualPayload(DIMENSION, 0, 0, 1, true, full).estimatedEncodedBytes();
        assertEquals(CableVisualPayload.MAX_RECORDS * 8, maxEstimate - emptyEstimate,
                "the maximum snapshot estimate includes all eight encoded bytes per record");
    }

    @Test void cacheIgnoresStaleRevisionAndLimitsVisibleRecords() {
        CableVisualClientCache cache = new CableVisualClientCache();
        var first = new CableVisualPayload(DIMENSION, 0, 0, 2, true,
                List.of(new CableChunkData.Record(1, 4, 1, Direction.NORTH, CableTier.MV)));
        var stale = new CableVisualPayload(DIMENSION, 0, 0, 1, true, List.of());
        assertTrue(cache.apply(first));
        assertFalse(cache.apply(stale));
        assertEquals(1, cache.visible(DIMENSION, 1.5, 4.5, 1.5, 16, 1).size());
        assertThrows(IllegalArgumentException.class, () -> cache.visible(DIMENSION, 0, 0, 0, 16,
                CableVisualClientCache.MAX_VISIBLE_RECORDS + 1));
        assertTrue(cache.visible(ResourceLocation.withDefaultNamespace("the_nether"), 1, 4, 1, 16, 1).isEmpty());
    }

    @Test void fullAndEmptySnapshotsReplaceAtomicallyAndNineChunkWindowStaysCached() {
        CableVisualClientCache cache = new CableVisualClientCache();
        List<CableChunkData.Record> full = new java.util.ArrayList<>(CableVisualPayload.MAX_RECORDS);
        for (int i = 0; i < CableVisualPayload.MAX_RECORDS; i++)
            full.add(new CableChunkData.Record(i & 15, i, i >>> 4 & 15, Direction.NORTH, CableTier.MV));
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 0, 0, 10, true, full)));
        assertEquals(CableVisualPayload.MAX_RECORDS, cache.cachedRecordCount());
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 0, 0, 11, true, List.of())));
        assertEquals(0, cache.cachedRecordCount(), "an empty reset snapshot removes prior records");

        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        List<CableVisualResyncScheduler.Chunk> window = new java.util.ArrayList<>(9);
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            window.add(new CableVisualResyncScheduler.Chunk(x, z));
            assertTrue(cache.apply(new CableVisualPayload(DIMENSION, x, z, 12, true, full)));
        }
        assertEquals(9 * CableVisualPayload.MAX_RECORDS, cache.cachedRecordCount());
        for (var chunk : window)
            assertTrue(cache.hasUsableSnapshot(DIMENSION, chunk.x(), chunk.z()));
        assertTrue(scheduler.select(DIMENSION, window,
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 40).isEmpty(),
                "fully cached 3x3 window has no periodic resync candidates");
    }

    @Test void cacheKeepsTenthFullChunkButEvictsOldestWhenRecordCapIsExceeded() {
        CableVisualClientCache cache = new CableVisualClientCache();
        List<CableChunkData.Record> full = new java.util.ArrayList<>(CableVisualPayload.MAX_RECORDS);
        for (int i = 0; i < CableVisualPayload.MAX_RECORDS; i++)
            full.add(new CableChunkData.Record(i & 15, i, i >>> 4 & 15, Direction.NORTH, CableTier.MV));

        for (int chunk = 0; chunk < 10; chunk++)
            assertTrue(cache.apply(new CableVisualPayload(DIMENSION, chunk, 0, 12, true, full)));
        assertEquals(10 * CableVisualPayload.MAX_RECORDS, cache.cachedRecordCount());
        for (int chunk = 0; chunk < 10; chunk++)
            assertTrue(cache.hasUsableSnapshot(DIMENSION, chunk, 0), "tenth full chunk fits with headroom");

        for (int chunk = 10; chunk < 17; chunk++)
            assertTrue(cache.apply(new CableVisualPayload(DIMENSION, chunk, 0, 12, true, full)));
        assertEquals(CableVisualClientCache.MAX_CACHED_RECORDS, cache.cachedRecordCount());
        assertEquals(16, cache.chunkCount());
        assertFalse(cache.hasUsableSnapshot(DIMENSION, 0, 0), "oldest chunk is evicted at the aggregate cap");
        assertTrue(cache.hasUsableSnapshot(DIMENSION, 16, 0), "newest snapshot remains usable");
    }

    @Test void reconnectClearsRevisionHistoryAndOutOfOrderSnapshotsStayRejected() {
        CableVisualClientCache cache = new CableVisualClientCache();
        var newer = new CableVisualPayload(DIMENSION, 2, 3, 9, true, List.of());
        assertTrue(cache.apply(newer));
        assertFalse(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 8, true, List.of())));
        cache.clearDimension();
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 1, true, List.of())),
                "a reconnect starts a new revision stream");
    }

    @Test void initialSnapshotCanArriveAfterWorldBecomesReadyAndUnloadKeepsRevisionFence() {
        CableVisualClientCache cache = new CableVisualClientCache();
        var initial = new CableVisualPayload(DIMENSION, 2, 3, 5, true,
                List.of(new CableChunkData.Record(1, 4, 1, Direction.NORTH, CableTier.MV)));
        assertTrue(cache.apply(initial), "a requested snapshot restores data missed before world readiness");
        cache.removeChunk(DIMENSION, 2, 3);
        assertEquals(0, cache.cachedRecordCount());
        assertFalse(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 4, true, List.of(initial.records().get(0)))),
                "a delayed pre-unload job cannot resurrect stale visuals");
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 6, true, List.of(initial.records().get(0)))),
                "the fresh post-load snapshot replaces the tombstone");
    }

    @Test void cableResyncRequestRejectsOutOfWorldCoordinates() {
        assertThrows(IllegalArgumentException.class, () ->
                new com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualResyncRequest(
                        1_875_001, 0));
        assertDoesNotThrow(() -> new com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualResyncRequest(
                -1_875_000, 1_875_000));
    }

    @Test void missedInitialSnapshotIsRetriedAndAuthoritativeEmptySnapshotSuppressesRequests() {
        CableVisualClientCache cache = new CableVisualClientCache();
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        var chunk = new CableVisualResyncScheduler.Chunk(2, 3);
        assertEquals(List.of(chunk), scheduler.select(DIMENSION, List.of(chunk),
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 1),
                "a missed first chunk-watch packet leaves the loaded chunk eligible for retry");
        assertTrue(scheduler.select(DIMENSION, List.of(chunk), ignored -> false, 30).isEmpty());
        assertEquals(List.of(chunk), scheduler.select(DIMENSION, List.of(chunk), ignored -> false, 31),
                "a denied initial request retries within the short initial timeout");

        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 1, true, List.of())));
        assertTrue(cache.hasUsableSnapshot(DIMENSION, 2, 3), "an authoritative empty snapshot is usable");
        assertTrue(scheduler.select(DIMENSION, List.of(chunk),
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 240).isEmpty(),
                "an empty snapshot must not be mistaken for a missing snapshot");
    }

    @Test void tombstoneIsRetryableAndSelectionIsBoundedFairAndCooledDown() {
        CableVisualClientCache cache = new CableVisualClientCache();
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        List<CableVisualResyncScheduler.Chunk> window = List.of(
                new CableVisualResyncScheduler.Chunk(0, 0), new CableVisualResyncScheduler.Chunk(1, 0),
                new CableVisualResyncScheduler.Chunk(2, 0), new CableVisualResyncScheduler.Chunk(3, 0),
                new CableVisualResyncScheduler.Chunk(4, 0));
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 0, 0, 1, true, List.of())));
        assertTrue(cache.hasUsableSnapshot(DIMENSION, 0, 0));
        cache.removeChunk(DIMENSION, 0, 0);
        assertFalse(cache.hasUsableSnapshot(DIMENSION, 0, 0), "unload changes a known empty snapshot into a tombstone");

        var first = scheduler.select(DIMENSION, window,
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 0);
        var second = scheduler.select(DIMENSION, window,
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 30);
        var third = scheduler.select(DIMENSION, window,
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 60);
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertTrue(third.size() >= 1 && third.size() <= 2);
        assertEquals(5, java.util.stream.Stream.of(first, second, third).flatMap(List::stream).distinct().count(),
                "the cursor visits every missing chunk despite the two-request scan cap");
        assertTrue(scheduler.select(DIMENSION, window,
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 89).size() <= 2,
                "retries remain within the per-scan request bound");
        assertFalse(scheduler.select(DIMENSION, window,
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 240).isEmpty(),
                "a missing snapshot is retried with bounded exponential backoff");
    }

    @Test void dimensionChangeResetsCooldownAndRoundRobinCursor() {
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        List<CableVisualResyncScheduler.Chunk> window = List.of(
                new CableVisualResyncScheduler.Chunk(0, 0), new CableVisualResyncScheduler.Chunk(1, 0),
                new CableVisualResyncScheduler.Chunk(2, 0));
        assertEquals(window.subList(0, 2), scheduler.select(DIMENSION, window, ignored -> false, 10));
        assertEquals(List.of(window.get(0), window.get(1)), scheduler.select(
                ResourceLocation.withDefaultNamespace("the_nether"), window, ignored -> false, 11),
                "a new dimension starts with a clean cursor and no inherited per-chunk cooldown");
    }

    @Test void delayedUnloadFromAnotherDimensionCannotTombstoneCurrentSnapshot() {
        CableVisualClientCache cache = new CableVisualClientCache();
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 9, true,
                List.of(new CableChunkData.Record(1, 4, 1, Direction.NORTH, CableTier.MV)))));
        var nether = ResourceLocation.withDefaultNamespace("the_nether");
        assertTrue(cache.apply(new CableVisualPayload(nether, 2, 3, 1, true, List.of())));

        assertFalse(cache.removeChunk(DIMENSION, 2, 3), "late old-dimension unload is ignored");
        assertTrue(cache.hasUsableSnapshot(nether, 2, 3), "current dimension snapshot remains authoritative");
    }

    @Test void returnedChunkGetsFreshSnapshotAndRetryCooldownIsResetOnlyForThatChunk() {
        CableVisualClientCache cache = new CableVisualClientCache();
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        var chunk = new CableVisualResyncScheduler.Chunk(2, 3);
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 5, true,
                List.of(new CableChunkData.Record(1, 4, 1, Direction.NORTH, CableTier.MV)))));
        assertTrue(scheduler.select(DIMENSION, List.of(chunk), ignored -> false, 10).contains(chunk));
        assertTrue(cache.removeChunk(DIMENSION, 2, 3));
        scheduler.forgetChunk(DIMENSION, 2, 3);
        assertEquals(List.of(chunk), scheduler.select(DIMENSION, List.of(chunk),
                key -> cache.hasUsableSnapshot(DIMENSION, key.x(), key.z()), 11));
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 6, true,
                List.of(new CableChunkData.Record(1, 4, 1, Direction.NORTH, CableTier.MV)))));
        assertTrue(cache.hasUsableSnapshot(DIMENSION, 2, 3), "fresh revision replaces unload tombstone");
        assertFalse(cache.apply(new CableVisualPayload(DIMENSION, 2, 3, 5, true, List.of())),
                "unload retains same-dimension revision fence");
    }

    @Test void unrelatedUnloadsDoNotResetResyncCursorOrCooldownProgress() {
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        List<CableVisualResyncScheduler.Chunk> window = List.of(
                new CableVisualResyncScheduler.Chunk(0, 0), new CableVisualResyncScheduler.Chunk(1, 0),
                new CableVisualResyncScheduler.Chunk(2, 0), new CableVisualResyncScheduler.Chunk(3, 0));
        var first = scheduler.select(DIMENSION, window, ignored -> false, 0);
        for (int i = 0; i < 30; i++) scheduler.forgetChunk(DIMENSION, 100 + i, 100);
        var second = scheduler.select(DIMENSION, window, ignored -> false, 10);
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertEquals(4, java.util.stream.Stream.of(first, second).flatMap(List::stream).distinct().count(),
                "unrelated unload events preserve global cursor progress");
        assertTrue(scheduler.select(DIMENSION, window, ignored -> false, 20).isEmpty(),
                "per-chunk cooldown remains active despite unrelated unloads");
    }

    @Test void loadedChunkPriorityIsBoundedAndSkipsAuthoritativeEmptySnapshots() {
        CableVisualClientCache cache = new CableVisualClientCache();
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        var first = new CableVisualResyncScheduler.Chunk(0, 0);
        var second = new CableVisualResyncScheduler.Chunk(1, 0);
        var third = new CableVisualResyncScheduler.Chunk(2, 0);
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 1, 0, 1, true, List.of())));
        scheduler.prioritize(DIMENSION, first);
        scheduler.prioritize(DIMENSION, second);
        scheduler.prioritize(DIMENSION, third);
        var hasSnapshot = (java.util.function.Predicate<CableVisualResyncScheduler.Chunk>) key ->
                cache.hasUsableSnapshot(DIMENSION, key.x(), key.z());
        assertEquals(List.of(first, third), scheduler.selectPriority(DIMENSION, ignored -> true, hasSnapshot, 20, 2));
        assertTrue(scheduler.selectPriority(DIMENSION, ignored -> true, hasSnapshot, 20, 2).isEmpty(),
                "dispatched candidates are cooled and authoritative empty snapshots are removed");
        assertEquals(List.of(third), scheduler.selectPriority(DIMENSION, ignored -> true, hasSnapshot, 220, 1),
                "loaded candidate retries after the 200 tick cooldown");
    }

    @Test void unloadedPriorityIsDiscardedAndReturnChunkOutranksStaleCandidates() {
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        var returned = new CableVisualResyncScheduler.Chunk(2, 3);
        scheduler.prioritize(DIMENSION, returned); // load before first snapshot
        assertEquals(List.of(returned), scheduler.selectPriority(DIMENSION, ignored -> true, ignored -> false, 1, 2));
        scheduler.forgetChunk(DIMENSION, returned.x(), returned.z()); // unload before response

        var nearby = List.of(returned, new CableVisualResyncScheduler.Chunk(3, 3),
                new CableVisualResyncScheduler.Chunk(4, 3));
        for (var chunk : nearby) scheduler.prioritize(DIMENSION, chunk);
        var loaded = Set.copyOf(nearby);
        var selected = scheduler.selectPriority(DIMENSION, loaded::contains, ignored -> false, 202, 2);
        assertEquals(2, selected.size());
        assertTrue(selected.contains(returned), "the loaded return candidate receives an actual request");
        assertTrue(selected.stream().allMatch(loaded::contains), "unloaded priority entries never consume request slots");
    }

    @Test void priorityQueueIsHardBoundedAndOldDimensionUnloadCannotForgetNewPriority() {
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        var nether = ResourceLocation.withDefaultNamespace("the_nether");
        var current = new CableVisualResyncScheduler.Chunk(7, 9);
        scheduler.prioritize(nether, current);
        scheduler.forgetChunk(DIMENSION, current.x(), current.z());
        assertEquals(List.of(current), scheduler.selectPriority(nether, ignored -> true, ignored -> false, 1, 1),
                "delayed unload from an old dimension does not erase current dimension priority");

        for (int i = 0; i < 5_000; i++) scheduler.prioritize(nether, new CableVisualResyncScheduler.Chunk(i, i));
        assertEquals(CableVisualResyncScheduler.MAX_PRIORITY_ENTRIES, scheduler.prioritySizeForTesting());
        assertTrue(scheduler.selectPriority(nether, ignored -> true, ignored -> false, 10, 2).size() <= 2);
    }

    @Test void earlySnapshotsStageThenApplyOnlyForReadyDimensionAndLatestRevisionWins() {
        CableVisualPendingSnapshots pending = new CableVisualPendingSnapshots();
        CableVisualClientCache cache = new CableVisualClientCache();
        var nether = ResourceLocation.withDefaultNamespace("the_nether");
        var record = new CableChunkData.Record(1, 4, 1, Direction.NORTH, CableTier.MV);
        pending.stage(new CableVisualPayload(DIMENSION, 2, 3, 4, true, List.of(record)));
        pending.stage(new CableVisualPayload(DIMENSION, 2, 3, 3, true, List.of()));
        pending.stage(new CableVisualPayload(nether, 2, 3, 1, true, List.of()));
        assertEquals(2, pending.chunkCount(), "dimension is part of each staged key");
        assertEquals(1, pending.flush(DIMENSION, ignored -> true, cache::apply));
        assertTrue(cache.hasUsableSnapshot(DIMENSION, 2, 3));
        assertEquals(1, cache.cachedRecordCount(), "the stale staged revision did not replace the newest");
        assertEquals(1, pending.chunkCount(), "another dimension is not applied to the active world");
        pending.clear();
        assertEquals(0, pending.chunkCount(), "logout clears staged packets");
    }

    @Test void stagedSnapshotSurvivesLoginResetAndFlushesOnceWorldAndChunkAreReady() {
        CableVisualPendingSnapshots pending = new CableVisualPendingSnapshots();
        CableVisualClientCache cache = new CableVisualClientCache();
        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        var initial = new CableVisualPayload(DIMENSION, 2, 3, 5, true,
                List.of(new CableChunkData.Record(1, 4, 1, Direction.NORTH, CableTier.MV)));
        pending.stage(initial);

        // This is the login reset's cable-only effect: clear old client state, not bounded arrivals.
        cache.clearDimension();
        scheduler.reset();
        assertEquals(1, pending.chunkCount());
        assertEquals(0, pending.flush(DIMENSION, ignored -> false, cache::apply),
                "a staged snapshot waits until its chunk is ready");
        assertEquals(1, pending.flush(DIMENSION, ignored -> true, cache::apply));
        assertTrue(cache.hasUsableSnapshot(DIMENSION, 2, 3));
        assertEquals(0, pending.flush(DIMENSION, ignored -> true, cache::apply),
                "a staged snapshot is applied at most once");
    }

    @Test void dimensionTransitionRetainsOnlyNewDimensionSnapshotsAndLogoutDiscardsThem() {
        CableVisualPendingSnapshots pending = new CableVisualPendingSnapshots();
        var nether = ResourceLocation.withDefaultNamespace("the_nether");
        var end = ResourceLocation.withDefaultNamespace("the_end");
        pending.stage(new CableVisualPayload(DIMENSION, 1, 1, 1, true, List.of()));
        pending.stage(new CableVisualPayload(nether, 1, 1, 2, true, List.of()));
        pending.stage(new CableVisualPayload(end, 1, 1, 3, true, List.of()));

        pending.retainDimension(nether);
        assertEquals(1, pending.chunkCount());
        List<CableVisualPayload> applied = new java.util.ArrayList<>();
        assertEquals(1, pending.flush(nether, ignored -> true, applied::add),
                "the new world's pre-switch snapshot is retained and flushed");
        assertEquals(nether, applied.get(0).dimension());
        pending.stage(new CableVisualPayload(nether, 2, 2, 4, true, List.of()));
        pending.clear(); // full logout reset
        assertEquals(0, pending.chunkCount());
        assertEquals(0, pending.recordCount());
    }

    @Test void pendingStoreAndRetryRateRemainBoundedAcrossTwentyClientsConceptually() {
        CableVisualPendingSnapshots pending = new CableVisualPendingSnapshots();
        for (int i = 0; i < 40; i++) {
            pending.stage(new CableVisualPayload(DIMENSION, i, 0, 1, true,
                    List.of(new CableChunkData.Record(i & 15, 4, 0, Direction.NORTH, CableTier.MV))));
        }
        assertEquals(CableVisualPendingSnapshots.MAX_CHUNKS, pending.chunkCount());
        assertTrue(pending.recordCount() <= CableVisualPendingSnapshots.MAX_RECORDS);

        CableVisualResyncScheduler scheduler = new CableVisualResyncScheduler();
        List<CableVisualResyncScheduler.Chunk> twentyPlayers = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) twentyPlayers.add(new CableVisualResyncScheduler.Chunk(i, 0));
        for (int tick = 0; tick < 10; tick++)
            assertTrue(scheduler.select(DIMENSION, twentyPlayers, ignored -> false, tick).size() <= 2,
                    "shared client scheduler never exceeds two requests per tick");
    }

    @Test void bothTileFinishesConcealOnlyTopFaceCableAndNoneRevealsIt() {
        for (Direction face : Direction.values()) {
            for (var finish : com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock.TileFinish.values()) {
                boolean tiled = finish != com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock.TileFinish.NONE;
                assertEquals(!(face == Direction.UP && tiled), CableFacePresentation.isVisible(face, true, tiled));
            }
            assertTrue(CableFacePresentation.isVisible(face, false, true));
        }
    }

    @Test void cablePaletteIsTierBackedAndApcMatchesSs14Green() {
        assertEquals(new CableVisualPresentation.Rgb(1F, .24F, .12F), CableVisualPresentation.color(CableTier.HV));
        assertEquals(new CableVisualPresentation.Rgb(1F, .78F, .12F), CableVisualPresentation.color(CableTier.MV));
        assertEquals(new CableVisualPresentation.Rgb(0F, 128F / 255F, 0F), CableVisualPresentation.color(CableTier.APC));
        assertEquals(CableVisualPresentation.color(CableTier.APC), CableVisualPresentation.color(CableTier.APC));
    }

    @Test void faceLocalWireAxisIsTangentToEverySurfaceFace() {
        for (Direction face : Direction.values()) {
            float x = CableVisualPresentation.wireAxisX(face);
            float z = CableVisualPresentation.wireAxisZ(face);
            assertEquals(1F, x * x + z * z);
            assertEquals(0F, x * face.getStepX() + z * face.getStepZ());
            assertEquals(face.getAxis() == Direction.Axis.X ? 1F : 0F, z);
        }
        assertEquals(1F, CableVisualPresentation.wireAxisX(Direction.UP));
        assertEquals(0F, CableVisualPresentation.wireAxisZ(Direction.UP));
    }

    @Test void centeredCableCapIsSquareAndSymmetricOnEveryFace() {
        float width = .075F;
        for (Direction face : Direction.values()) {
            List<CableVisualGeometry.Vector3> corners = CableVisualGeometry.centerCorners(face, width);
            assertEquals(4, corners.size(), face.toString());
            for (int axis = 0; axis < 3; axis++) {
                final int component = axis;
                var tangent = corners.stream().mapToDouble(corner -> component(corner, component)).toArray();
                if (axis == face.getAxis().ordinal()) {
                    for (double value : tangent) assertEquals(0, value, 0, face + " normal component");
                } else {
                    assertEquals(-width, java.util.Arrays.stream(tangent).min().orElseThrow(), 0,
                            face + " tangent minimum");
                    assertEquals(width, java.util.Arrays.stream(tangent).max().orElseThrow(), 0,
                            face + " tangent maximum");
                }
            }
        }
    }

    private static float component(CableVisualGeometry.Vector3 vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x();
            case 1 -> vector.y();
            case 2 -> vector.z();
            default -> throw new IllegalArgumentException("unknown axis");
        };
    }

    @Test void adjacencyMaskUsesPowerTopologyAndIsInputOrderStable() {
        var center = new CableFaceNode(new net.minecraft.core.BlockPos(0, 4, 0), Direction.UP, CableTier.HV);
        var east = new CableFaceNode(new net.minecraft.core.BlockPos(1, 4, 0), Direction.UP, CableTier.HV);
        var northWall = new CableFaceNode(new net.minecraft.core.BlockPos(0, 5, 1), Direction.NORTH, CableTier.HV);
        var wrongTier = new CableFaceNode(new net.minecraft.core.BlockPos(-1, 4, 0), Direction.UP, CableTier.MV);
        int mask = CableVisualAdjacency.mask(center, List.of(east, northWall, wrongTier));
        assertTrue(mask != 0);
        assertEquals(mask, CableVisualAdjacency.mask(center, List.of(wrongTier, northWall, east)));
        assertTrue(CableVisualAdjacency.neighbors(center, List.of(east, northWall, wrongTier)).containsAll(List.of(east, northWall)));
        assertFalse(CableVisualAdjacency.neighbors(center, List.of(east, northWall, wrongTier)).contains(wrongTier));
        assertEquals(16, CableVisualAdjacency.MASK_BITS);
    }

    @Test void concealedEndpointSuppressesOnlyItsRenderedJoin() {
        var center = new CableFaceNode(new net.minecraft.core.BlockPos(0, 4, 0), Direction.UP, CableTier.HV);
        var east = new CableFaceNode(new net.minecraft.core.BlockPos(1, 4, 0), Direction.UP, CableTier.HV);
        var north = new CableFaceNode(new net.minecraft.core.BlockPos(0, 4, -1), Direction.UP, CableTier.HV);
        List<CableFaceNode> topology = List.of(east, north);

        assertEquals(2, CableVisualAdjacency.neighbors(center, topology).size(), "visibility does not alter topology");
        List<CableFaceNode> joinsWithoutEast = CableVisualAdjacency.visibleNeighbors(center, Set.of(north));
        assertEquals(List.of(north), joinsWithoutEast);
        List<CableFaceNode> allVisibleJoins = CableVisualAdjacency.visibleNeighbors(center, Set.copyOf(topology));
        assertEquals(2, allVisibleJoins.size());
        assertTrue(allVisibleJoins.containsAll(topology));
    }

    @Test void indexedVisibleAdjacencyUsesAtMostEightPhysicalNeighborsForEveryFace() {
        for (Direction face : Direction.values()) {
            var center = new CableFaceNode(new net.minecraft.core.BlockPos(0, 4, 0), face, CableTier.HV);
            List<CableFaceNode> candidates = CableVisualAdjacency.possibleNeighbors(center);
            assertEquals(PowerTopology.MAX_CABLE_NEIGHBORS, candidates.size(), face.toString());
            assertEquals(candidates.size(), candidates.stream().distinct().count());
            for (CableFaceNode candidate : candidates)
                assertTrue(PowerTopology.cableEdge(center, candidate).isPresent(),
                        "candidate follows coplanar or edge-turn geometry for " + face);
            assertEquals(candidates, CableVisualAdjacency.visibleNeighbors(center, Set.copyOf(candidates)),
                    "indexed lookup preserves stable ordering for " + face);

            // A diagonal tangent step and an edge-turn at any other displacement cannot join.
            var diagonal = new CableFaceNode(center.host().offset(1, 0, 1), face, CableTier.HV);
            assertFalse(candidates.contains(diagonal));
            assertFalse(PowerTopology.cableEdge(center, diagonal).isPresent());
            var sameHostOtherFace = new CableFaceNode(center.host(), face.getOpposite(), CableTier.HV);
            assertFalse(candidates.contains(sameHostOtherFace), "faces on one host cannot turn through the host block");
            assertFalse(PowerTopology.cableEdge(center, sameHostOtherFace).isPresent());
        }
    }

    @Test void topologyGeometryHasNoPhantomSpokesAndUsesPhysicalBoundaries() {
        var floor = new CableFaceNode(new net.minecraft.core.BlockPos(0, 4, 0), Direction.UP, CableTier.HV);
        assertTrue(CableVisualAdjacency.neighbors(floor, List.of()).isEmpty(), "isolated node is center-only");
        for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var neighbor = new CableFaceNode(floor.host().relative(direction), Direction.UP, CableTier.HV);
            assertEquals(List.of(neighbor), CableVisualAdjacency.neighbors(floor, List.of(neighbor)),
                    "only the requested cardinal topology edge creates a floor spoke");
            assertEquals(.5F, Math.abs(CableVisualGeometry.endpoint(floor, neighbor).x()
                    + CableVisualGeometry.endpoint(floor, neighbor).z()), "coplanar endpoint reaches shared tile edge");
        }
        var wall = new CableFaceNode(new net.minecraft.core.BlockPos(0, 4, 0), Direction.NORTH, CableTier.HV);
        var up = new CableFaceNode(new net.minecraft.core.BlockPos(0, 5, 0), Direction.NORTH, CableTier.HV);
        assertEquals(new CableVisualGeometry.Vector3(0, .5F, 0), CableVisualGeometry.endpoint(wall, up));
        var floorTurn = new CableFaceNode(new net.minecraft.core.BlockPos(0, 3, -1), Direction.UP, CableTier.HV);
        assertEquals(new CableVisualGeometry.Vector3(0, -.5F, 0), CableVisualGeometry.endpoint(wall, floorTurn));
        assertThrows(IllegalArgumentException.class, () -> CableVisualGeometry.endpoint(floor,
                new CableFaceNode(floor.host().relative(Direction.UP), Direction.UP, CableTier.HV)));
    }

    @Test void emittedQuadsReachSharedSeamsAndKeepAllTierLanesSeparate() {
        var tiers = List.of(CableTier.HV, CableTier.MV, CableTier.APC);
        for (Direction direction : List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
            for (CableTier tier : tiers) {
                var a = new CableFaceNode(new net.minecraft.core.BlockPos(0, 4, 0), Direction.UP, tier);
                var b = new CableFaceNode(a.host().relative(direction), Direction.UP, tier);
                List<CableVisualGeometry.Vector3> corners = CableVisualGeometry.spokeCorners(a, b, .03F);
                assertEquals(4, corners.size());
                float boundary = switch (direction) {
                    case EAST -> .5F;
                    case WEST -> -.5F;
                    case NORTH -> -.5F;
                    case SOUTH -> .5F;
                    default -> throw new AssertionError();
                };
                int movement = direction.getAxis() == Direction.Axis.X ? 0 : 2;
                for (var corner : corners.subList(1, 3)) assertEquals(boundary, component(corner, movement), .00001F,
                        "quad ends exactly at the shared face boundary");
                List<CableVisualGeometry.Vector3> reciprocal = CableVisualGeometry.spokeCorners(b, a, .03F);
                for (var corner : corners) {
                    if (Math.abs(component(corner, movement) - boundary) < .00001F) {
                        boolean sameWorldCorner = reciprocal.stream().anyMatch(other ->
                                close(a.host().getX() + corner.x(), b.host().getX() + other.x())
                                        && close(a.host().getY() + corner.y(), b.host().getY() + other.y())
                                        && close(a.host().getZ() + corner.z(), b.host().getZ() + other.z()));
                        assertTrue(sameWorldCorner, "reciprocal quad meets at identical seam coordinates");
                    }
                }
            }
        }

        for (Direction face : Direction.values()) {
            List<Double> laneCoordinates = new java.util.ArrayList<>();
            for (CableTier tier : tiers) {
                var node = new CableFaceNode(new net.minecraft.core.BlockPos(0, 0, 0), face, tier);
                var cap = CableVisualGeometry.centerCorners(node, .05F);
                laneCoordinates.add(cap.stream().mapToDouble(c -> component(c, face.getAxis() == Direction.Axis.X
                        ? 1 : face.getAxis() == Direction.Axis.Y ? 0 : 1)).average().orElseThrow());
                for (Direction tangent : Direction.values()) {
                    if (tangent.getAxis() == face.getAxis()) continue;
                    var neighbor = new CableFaceNode(node.host().relative(tangent), face, tier);
                    assertEquals(4, CableVisualGeometry.spokeCorners(node, neighbor, .03F).size());
                }
            }
            assertEquals(3, laneCoordinates.stream().distinct().count(), "all tiers have distinct face-local caps");
        }
    }

    @Test void verticalWallSpokesStayStraightAcrossCoplanarNeighborsAndMeetReciprocally() {
        for (Direction face : List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
            int laneAxis = face.getAxis() == Direction.Axis.X ? 2 : 0;
            for (int dy : List.of(-1, 1)) {
                for (CableTier tier : CableTier.values()) {
                    var host = new net.minecraft.core.BlockPos(3, 8, -2);
                    var node = new CableFaceNode(host, face, tier);
                    var neighbor = new CableFaceNode(host.offset(0, dy, 0), face, tier);
                    List<CableVisualGeometry.Vector3> quad = CableVisualGeometry.spokeCorners(node, neighbor, .03F);
                    List<CableVisualGeometry.Vector3> reciprocal = CableVisualGeometry.spokeCorners(neighbor, node, .03F);
                    float boundary = dy * .5F;
                    float lane = CableVisualGeometry.lane(tier);

                    assertEquals(4, quad.size());
                    assertEquals(4, reciprocal.size());
                    for (var farCorner : quad.subList(1, 3)) {
                        assertEquals(boundary, farCorner.y(), .00001F,
                                face + " vertical spoke ends on the shared Y boundary, dy=" + dy + " " + tier);
                    }
                    assertEquals(lane, (component(quad.get(1), laneAxis)
                            + component(quad.get(2), laneAxis)) / 2F, .00001F,
                            face + " keeps its transverse tier lane at the far end");
                    assertEquals(lane, (component(quad.get(0), laneAxis)
                            + component(quad.get(3), laneAxis)) / 2F, .00001F,
                            face + " keeps its transverse tier lane at the near end");

                    for (var farCorner : quad.subList(1, 3)) {
                        boolean sameWorldCorner = reciprocal.subList(1, 3).stream().anyMatch(other ->
                                close(host.getX() + farCorner.x(), neighbor.host().getX() + other.x())
                                        && close(host.getY() + farCorner.y(), neighbor.host().getY() + other.y())
                                        && close(host.getZ() + farCorner.z(), neighbor.host().getZ() + other.z()));
                        assertTrue(sameWorldCorner, "reciprocal wall spokes align at the world seam for "
                                + face + " dy=" + dy + " " + tier);
                    }
                }
            }
        }
    }

    @Test void turnSpokesReachTheSharedCubeEdgeInEachTierLane() {
        List<CableVisualGeometry.Vector3> sharedEdges = new java.util.ArrayList<>();
        for (CableTier tier : CableTier.values()) {
            var floor = new CableFaceNode(new net.minecraft.core.BlockPos(0, 0, 0), Direction.UP, tier);
            var wall = new CableFaceNode(new net.minecraft.core.BlockPos(0, 1, 1), Direction.NORTH, tier);
            var quad = CableVisualGeometry.spokeCorners(floor, wall, .03F);
            assertEquals(4, quad.size());
            var edge = quad.stream().filter(c -> Math.abs(c.z() - .5F) < .00001F).toList();
            assertTrue(edge.size() >= 1, "emitted quad reaches the shared 3D edge");
            sharedEdges.add(edge.get(0));
        }
        assertEquals(3, sharedEdges.stream().map(CableVisualGeometry.Vector3::x).distinct().count(),
                "each tier turn has a non-overlapping lane along the shared edge");
    }

    @Test void everyOrderedFaceTurnKeepsReciprocalTierLanesAtTheSameWorldEdgePosition() {
        List<CableTier> tiers = List.of(CableTier.HV, CableTier.MV, CableTier.APC);
        int turns = 0;
        for (Direction face : Direction.values()) {
            for (Direction otherFace : Direction.values()) {
                if (face.getAxis() == otherFace.getAxis()) continue;
                turns++;
                var host = new net.minecraft.core.BlockPos(0, 0, 0);
                var delta = new net.minecraft.core.BlockPos(face.getStepX() - otherFace.getStepX(),
                        face.getStepY() - otherFace.getStepY(), face.getStepZ() - otherFace.getStepZ());
                var otherHost = host.offset(delta);
                int edgeAxis = 3 - face.getAxis().ordinal() - otherFace.getAxis().ordinal();
                double[] lanePositions = new double[tiers.size()];
                for (int tierIndex = 0; tierIndex < tiers.size(); tierIndex++) {
                    CableTier tier = tiers.get(tierIndex);
                    var first = new CableFaceNode(host, face, tier);
                    var second = new CableFaceNode(otherHost, otherFace, tier);
                    assertTrue(PowerTopology.cableEdge(first, second).isPresent(), face + " -> " + otherFace);
                    var forward = edgeMidpoint(CableVisualGeometry.spokeCorners(first, second, .03F), host,
                            face, edgeAxis);
                    var reciprocal = edgeMidpoint(CableVisualGeometry.spokeCorners(second, first, .03F), otherHost,
                            otherFace, edgeAxis);
                    assertEquals(forward[edgeAxis], reciprocal[edgeAxis], .00001,
                            "reciprocal lane edge coordinate " + face + " -> " + otherFace + " " + tier);
                    for (int axis = 0; axis < 3; axis++) {
                        if (axis == edgeAxis) continue;
                        // Compare rendered coordinates including each face's .502
                        // normal lift; opposite face lifts can differ by .002 at
                        // the shared boundary, within the raster-safe tolerance.
                        assertEquals(forward[axis], reciprocal[axis], .004,
                                "transverse edge coordinate " + face + " -> " + otherFace + " " + tier);
                    }
                    lanePositions[tierIndex] = forward[edgeAxis];
                }
                for (int i = 0; i < lanePositions.length; i++) for (int j = i + 1; j < lanePositions.length; j++)
                    assertTrue(Math.abs(lanePositions[i] - lanePositions[j]) > .15,
                            "coexisting tier spokes remain separated along the edge for " + face + " -> " + otherFace);
            }
        }
        assertEquals(24, turns, "covers floor/wall, wall/wall, and ceiling/wall in both ordered directions");
    }

    private static double[] edgeMidpoint(List<CableVisualGeometry.Vector3> quad,
                                         net.minecraft.core.BlockPos host, Direction face, int edgeAxis) {
        assertEquals(4, quad.size());
        var a = quad.get(1);
        var b = quad.get(2);
        double[] midpoint = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double normalLift = axis == 0 ? face.getStepX() * .502
                    : axis == 1 ? face.getStepY() * .502 : face.getStepZ() * .502;
            double origin = axis == 0 ? host.getX() : axis == 1 ? host.getY() : host.getZ();
            midpoint[axis] = origin + (component(a, axis) + component(b, axis)) / 2 + normalLift;
        }
        return midpoint;
    }

    @Test void fourWayFloorBranchesStayMaximalAndDoNotJoinAnotherTier() {
        for (CableTier tier : CableTier.values()) {
            var center = new CableFaceNode(new net.minecraft.core.BlockPos(0, 0, 0), Direction.UP, tier);
            List<CableFaceNode> neighbors = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)
                    .stream().map(direction -> new CableFaceNode(center.host().relative(direction), Direction.UP, tier))
                    .toList();
            var otherTier = new CableFaceNode(center.host().relative(Direction.NORTH), Direction.UP,
                    tier == CableTier.HV ? CableTier.MV : CableTier.HV);
            assertEquals(4, CableVisualAdjacency.neighbors(center, neighbors).size());
            assertFalse(PowerTopology.cableEdge(center, otherTier).isPresent(), "tier visuals are not electrical joins");
            for (CableFaceNode neighbor : neighbors) {
                var quad = CableVisualGeometry.spokeCorners(center, neighbor, .03F);
                assertEquals(4, quad.size(), "each of four topology branches emits an uninterrupted quad");
                float lane = CableVisualGeometry.lane(tier);
                assertEquals(lane, CableVisualGeometry.centerCorners(center, .05F).stream()
                        .mapToDouble(c -> c.z()).average().orElseThrow(), .00001F);
            }
        }
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < .00001; }

    @Test void snapshotTopologyCrossesChunkSeamAndIgnoresRenderBudgetAndOtherTiers() {
        CableVisualClientCache cache = new CableVisualClientCache();
        var left = new CableChunkData.Record(15, 4, 3, Direction.UP, CableTier.HV);
        var right = new CableChunkData.Record(0, 4, 3, Direction.UP, CableTier.HV);
        var wrongTier = new CableChunkData.Record(0, 4, 4, Direction.UP, CableTier.MV);
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 0, 0, 1, true, List.of(left))));
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 1, 0, 1, true, List.of(right, wrongTier))));
        var node = new CableFaceNode(new net.minecraft.core.BlockPos(15, 4, 3), Direction.UP, CableTier.HV);
        assertEquals(List.of(new CableFaceNode(new net.minecraft.core.BlockPos(16, 4, 3), Direction.UP, CableTier.HV)),
                cache.topologyNeighbors(node), "topology lookup sees records beyond the visible budget at a chunk seam");
        cache.removeChunk(DIMENSION, 1, 0);
        assertTrue(cache.topologyNeighbors(node).isEmpty(), "unload removes its indexed records immediately");
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 1, 0, 2, true, List.of(right))));
        assertTrue(cache.apply(new CableVisualPayload(DIMENSION, 1, 0, 3, true, List.of())));
        assertTrue(cache.topologyNeighbors(node).isEmpty(), "authoritative empty snapshot clears topology");
    }
}
