package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereVisualClientCacheTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private final AtmosphereVisualClientCache cache = new AtmosphereVisualClientCache();

    @Test
    void snapshotIsStagedUntilFinalAndThenDeltaAndZeroAlphaApply() {
        var first = cell(1, 10, 0, 100);
        var second = cell(2, 10, 0, 100);
        assertFalse(cache.apply(packet(5, true, false, first)));
        assertTrue(cache.cells(DIM, 0, 0).isEmpty());
        assertTrue(cache.apply(packet(5, false, true, second)));
        assertEquals(2, cache.cells(DIM, 0, 0).size());
        assertTrue(cache.apply(packet(8, false, true, cell(1, 10, 0, 0))));
        assertEquals(1, cache.cells(DIM, 0, 0).size());
        assertEquals(8, cache.revision(DIM, 0, 0));
    }

    @Test
    void queuedNewerAddsAndRemovalsReplayAfterOlderSnapshotPublishes() {
        var original = cell(0, 10, 0, 100);
        assertFalse(cache.apply(packet(1, true, false, original)));
        assertFalse(cache.apply(packet(2, false, true, cell(1, 10, 0, 120))));
        assertFalse(cache.apply(packet(2, false, true, cell(0, 10, 0, 0))));
        assertTrue(cache.cells(DIM, 0, 0).isEmpty(), "deltas must not mutate the published view while staged");

        assertTrue(cache.apply(packet(1, false, true, cell(2, 10, 0, 80))));
        var cells = cache.cells(DIM, 0, 0);
        assertFalse(cells.containsKey(new AtmosphereVisualClientCache.CellKey(0, 10, 0)));
        assertTrue(cells.containsKey(new AtmosphereVisualClientCache.CellKey(1, 10, 0)));
        assertTrue(cells.containsKey(new AtmosphereVisualClientCache.CellKey(2, 10, 0)));
        assertEquals(2, cache.revision(DIM, 0, 0));
        assertFalse(cache.apply(packet(1, false, true, cell(3, 10, 0, 200))), "old snapshot continuation is stale");
        assertEquals(2, cache.revision(DIM, 0, 0));
    }

    @Test
    void replayCoalescesOutOfOrderUpdatesByCellAndKeepsNewestRevision() {
        cache.apply(packet(1, true, false, cell(0, 0, 0, 50)));
        cache.apply(packet(2, false, true, cell(1, 0, 0, 20)));
        cache.apply(packet(3, false, true, cell(1, 0, 0, 30)));
        cache.apply(packet(2, false, true, cell(1, 0, 0, 10)));
        assertTrue(cache.apply(packet(1, false, true, cell(2, 0, 0, 40))));
        assertEquals(3, cache.revision(DIM, 0, 0));
        assertEquals(30, cache.cells(DIM, 0, 0).get(new AtmosphereVisualClientCache.CellKey(1, 0, 0)).plasmaAlpha());
        assertEquals(40, cache.cells(DIM, 0, 0).get(new AtmosphereVisualClientCache.CellKey(2, 0, 0)).plasmaAlpha());
    }

    @Test
    void overflowingQueuedDeltaEvictsChunkAndAdvertisesResyncInsteadOfLosingGasSilently() {
        cache.apply(packet(1, true, false, cell(0, 0, 0, 80)));
        for (int i = 0; i <= AtmosphereVisualClientCache.MAX_QUEUED_DELTA_CELLS_PER_CHUNK; i++) {
            int y = i < 4096 ? -2048 + i : -2048;
            int z = i < 4096 ? 0 : 1;
            cache.apply(packet(2, false, true, cell(0, y, z, 255)));
        }
        assertTrue(cache.isIncomplete());
        assertEquals(List.of(new AtmosphereVisualClientCache.ChunkKey(DIM, 0, 0)), cache.resyncCandidates(1));
        assertTrue(cache.cells(DIM, 0, 0).isEmpty());
        assertEquals(0, cache.revision(DIM, 0, 0));
    }

    @Test
    void newerResetReplacesStagingAndOldRevisionsAreIgnored() {
        cache.apply(packet(3, true, true, cell(0, 1, 0, 80)));
        assertFalse(cache.apply(packet(4, true, false, cell(1, 1, 0, 90))));
        assertTrue(cache.apply(packet(4, false, true, cell(2, 1, 0, 90))));
        assertEquals(2, cache.cells(DIM, 0, 0).size());
        assertFalse(cache.apply(packet(2, true, true, cell(3, 1, 0, 99))));
    }

    @Test
    void gapIsAcceptedAndUnloadAndClearRemoveRevisionAndCells() {
        cache.apply(packet(2, true, true, cell(0, 1, 0, 80)));
        assertTrue(cache.apply(packet(100, false, true, cell(1, 1, 0, 80))));
        assertEquals(100, cache.revision(DIM, 0, 0));
        cache.unload(DIM, 0, 0);
        assertEquals(0, cache.revision(DIM, 0, 0));
        assertTrue(cache.cells(DIM, 0, 0).isEmpty());
        cache.apply(packet(101, true, true, cell(0, 1, 0, 80)));
        cache.clear();
        assertEquals(0, cache.revision(DIM, 0, 0));
    }

    @Test
    void readOnlyChunkViewIsReusedUntilPublishedDataChanges() {
        cache.apply(packet(1, true, true, cell(0, 1, 0, 80)));
        var first = cache.cells(DIM, 0, 0);
        assertSame(first, cache.cells(DIM, 0, 0));
        cache.apply(packet(2, false, true, cell(1, 1, 0, 80)));
        var updated = cache.cells(DIM, 0, 0);
        assertNotSame(first, updated);
        assertEquals(2, updated.size());
        assertSame(updated, cache.cells(DIM, 0, 0));
    }

    @Test
    void candidateSelectionIsBoundedAndNearestFirst() {
        cache.apply(new AtmosphereVisualPayload(DIM, 0, 0, 1, true, true, List.of(
                cell(0, 1, 0, 255),
                new AtmosphereVisualPayload.VisualCell(1, 1, 0, 0, 255, 0, 0, 0, 0),
                new AtmosphereVisualPayload.VisualCell(2, 1, 0, 0, 120, 0, 0, 0, 0))));
        var selected = cache.visibleCells(DIM, .5, 1.5, .5, 48, 2);
        assertEquals(2, selected.size());
        assertEquals(0.0, selected.get(0).distanceSquared());
        assertEquals(0, selected.get(0).cell().tritiumAlpha(), "the exact-camera plasma cell is nearest");
        assertEquals(255, selected.get(1).cell().tritiumAlpha(), "nearby tritium remains in the bounded set");
        assertEquals(1.5, selected.get(1).x());
        assertEquals(1.5, selected.get(1).y());
        assertEquals(.5, selected.get(1).z());
        assertTrue(cache.visibleCells(DIM, 500, 1.5, .5, 48, 10).isEmpty());
    }

    @Test
    void nearestCellsBeatDistantTritiumAndCameraMovementKeepsNearbyTritium() {
        int nearbyTritiumX = 8;
        int nearbyTritiumZ = 8;
        // Nine nearby chunks exceed the candidate cap while remaining inside the 48-block render radius.
        long revision = 1;
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                List<AtmosphereVisualPayload.VisualCell> cells = new java.util.ArrayList<>();
                for (int x = 0; x < 15; x++) {
                    for (int z = 0; z < 15; z++) {
                        int tritium = chunkX == 0 && chunkZ == 0 && x == 0 && z == 0 ? 255 : 0;
                        cells.add(new AtmosphereVisualPayload.VisualCell(x, 35, z, 255, tritium, 0, 0, 0, 0));
                    }
                }
                if (chunkX == 0 && chunkZ == 0) {
                    cells.add(new AtmosphereVisualPayload.VisualCell(nearbyTritiumX, 0, nearbyTritiumZ,
                            0, 255, 0, 0, 0, 0));
                }
                cache.apply(new AtmosphereVisualPayload(DIM, chunkX, chunkZ, revision++, true, true, cells));
            }
        }

        var first = cache.visibleCells(DIM, 8.5, .5, 8.5, 48, 2000);
        assertEquals(2000, first.size());
        assertTrue(first.stream().anyMatch(candidate -> candidate.cell().y() == 0
                && candidate.cell().localX() == nearbyTritiumX && candidate.cell().localZ() == nearbyTritiumZ));
        var nearestOnly = cache.visibleCells(DIM, 8.5, .5, 8.5, 48, 1);
        assertEquals(1, nearestOnly.size());
        assertEquals(0, nearestOnly.get(0).distanceSquared(), 0.0,
                "bright distant tritium must not outrank the nearest cell");

        var moved = cache.visibleCells(DIM, 9.5, .5, 8.5, 48, 2000);
        assertTrue(moved.stream().anyMatch(candidate -> candidate.cell().y() == 0
                && candidate.cell().localX() == nearbyTritiumX && candidate.cell().localZ() == nearbyTritiumZ));
    }

    @Test
    void candidateTiesUseTritiumThenStableCoordinatesAndRespectTheLimit() {
        cache.apply(packet(1, true, true,
                new AtmosphereVisualPayload.VisualCell(1, 0, 0, 255, 100, 0, 0, 0, 0),
                new AtmosphereVisualPayload.VisualCell(0, 0, 1, 255, 0, 0, 0, 0, 0),
                new AtmosphereVisualPayload.VisualCell(0, 0, 0, 255, 0, 0, 0, 0, 0),
                new AtmosphereVisualPayload.VisualCell(1, 0, 1, 255, 0, 0, 0, 0, 0)));
        var selected = cache.visibleCells(DIM, 1.0, .5, 1.0, 10, 4);
        assertEquals(4, selected.size());
        assertEquals(100, selected.get(0).cell().tritiumAlpha(), "tritium wins only within the equal-distance band");
        assertEquals(1.5, selected.get(0).x());
        assertEquals(.5, selected.get(0).z());
        assertEquals(.5, selected.get(1).x());
        assertEquals(.5, selected.get(1).z());
        assertEquals(.5, selected.get(2).x());
        assertEquals(1.5, selected.get(2).z());
        assertEquals(1.5, selected.get(3).x());
        assertEquals(1.5, selected.get(3).z());
        assertEquals(2, cache.visibleCells(DIM, 1.0, .5, 1.0, 10, 2).size());
    }

    @Test
    void evictionCandidatesAreBoundedAndClearOnSnapshotOrUnload() {
        for (int i = 0; i <= AtmosphereVisualClientCache.MAX_CACHED_CHUNKS; i++) {
            cache.apply(new AtmosphereVisualPayload(DIM, i, 0, 1, true, true, List.of()));
        }
        assertTrue(cache.isIncomplete());
        assertEquals(List.of(new AtmosphereVisualClientCache.ChunkKey(DIM, 0, 0)), cache.resyncCandidates(2));
        assertTrue(cache.resyncCandidates(0).isEmpty());
        assertEquals(1, cache.drainResyncCandidates(1).size());
        assertTrue(cache.isIncomplete(), "queuing a retry must not acknowledge recovery");
        cache.unload(DIM, 1, 0);
        cache.apply(new AtmosphereVisualPayload(DIM, 0, 0, 2, true, true, List.of()));
        assertFalse(cache.isIncomplete(), "publishing the replacement snapshot completes recovery");
        assertTrue(cache.resyncCandidates(2).isEmpty());
    }

    @Test
    void retrySelectionHonorsCallerBoundAndClearResetsIncompleteState() {
        for (int i = 0; i <= AtmosphereVisualClientCache.MAX_CACHED_CHUNKS + 2; i++)
            cache.apply(new AtmosphereVisualPayload(DIM, i, 0, 1, true, true, List.of()));
        assertEquals(2, cache.resyncCandidates(2).size());
        assertEquals(3, cache.resyncCandidates(20).size());
        cache.clear();
        assertFalse(cache.isIncomplete());
        assertTrue(cache.resyncCandidates(2).isEmpty());
    }

    @Test
    void droppedEvictionMarkersKeepWarningUntilClear() {
        int count = AtmosphereVisualClientCache.MAX_CACHED_CHUNKS + 4097;
        for (int i = 0; i < count; i++)
            cache.apply(new AtmosphereVisualPayload(DIM, i, 0, 1, true, true, List.of()));
        assertTrue(cache.isIncomplete());
        assertEquals(4096, cache.resyncCandidates(10_000).size());
        cache.drainResyncCandidates(4096);
        assertTrue(cache.isIncomplete(), "queued candidates cannot clear omitted-key warning");
        cache.clear();
        assertFalse(cache.isIncomplete());
    }

    private static AtmosphereVisualPayload packet(long revision, boolean reset, boolean last,
                                                   AtmosphereVisualPayload.VisualCell... cells) {
        return new AtmosphereVisualPayload(DIM, 0, 0, revision, reset, last, List.of(cells));
    }
    private static AtmosphereVisualPayload.VisualCell cell(int x, int y, int z, int alpha) {
        return new AtmosphereVisualPayload.VisualCell(x, y, z, alpha, 0, 0, 0, 0, 0);
    }
}
