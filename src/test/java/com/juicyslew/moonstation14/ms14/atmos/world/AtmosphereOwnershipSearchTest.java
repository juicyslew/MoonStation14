package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereOwnershipSearchTest {
    @Test
    void underOverhangIsExteriorThroughLoadedSkyPathAndRoofDoesNotSealSideOpening() {
        Set<BlockPos> open = new HashSet<>();
        for (int x = 0; x <= 4; x++) open.add(new BlockPos(x, 0, 0));
        Set<BlockPos> firstSky = Set.of(new BlockPos(4, 0, 0));
        assertEquals(AtmosphereOwnershipSearch.Status.EXTERIOR,
                run(new AtmosphereOwnershipSearch(BlockPos.ZERO), pos -> probe(open, firstSky, Set.of(), pos)).status());

        // A roof blocks the vertical route, but the loaded horizontal side opening still reaches sky.
        open.add(new BlockPos(0, -1, 0));
        open.add(new BlockPos(0, 1, 0));
        Set<BlockPos> sideOpeningSky = Set.of(new BlockPos(4, 0, 0));
        assertEquals(AtmosphereOwnershipSearch.Status.EXTERIOR,
                run(new AtmosphereOwnershipSearch(BlockPos.ZERO), pos -> probe(open, sideOpeningSky, Set.of(), pos)).status());
    }

    @Test
    void closingFinalOpeningProvesFinite() {
        Set<BlockPos> open = Set.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        var result = run(new AtmosphereOwnershipSearch(BlockPos.ZERO), pos ->
                probe(open, Set.of(), Set.of(), pos));
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, result.status());
        assertEquals(open, new HashSet<>(result.visitedOpenCells()));
    }

    @Test
    void finiteClaimsAreBarriersAndClaimedSeedRemainsFinite() {
        BlockPos claimed = new BlockPos(1, 0, 0);
        Set<BlockPos> open = Set.of(BlockPos.ZERO, claimed, new BlockPos(2, 0, 0),
                new BlockPos(0, 0, 1), new BlockPos(1, 0, 1), new BlockPos(2, 0, 1));
        Set<BlockPos> claims = Set.of(claimed);
        var claimedResult = run(new AtmosphereOwnershipSearch(claimed), pos ->
                probe(open, Set.of(), claims, pos));
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, claimedResult.status());
        assertTrue(claimedResult.visitedOpenCells().isEmpty());

        var exterior = run(new AtmosphereOwnershipSearch(BlockPos.ZERO), pos ->
                probe(open, Set.of(new BlockPos(2, 0, 1)), claims, pos));
        assertEquals(AtmosphereOwnershipSearch.Status.EXTERIOR, exterior.status());
        assertFalse(exterior.visitedOpenCells().contains(claimed));
    }

    @Test
    void ownershipSearchHandlesMoreThanEqualizationLimitOverManySteps() {
        Set<BlockPos> longRoom = new HashSet<>();
        for (int x = 0; x < 8_100; x++) longRoom.add(new BlockPos(x, 0, 0));
        var search = new AtmosphereOwnershipSearch(BlockPos.ZERO);
        var probe = (AtmosphereOwnershipSearch.NeighborProbe) pos -> probe(longRoom, Set.of(), Set.of(), pos);
        AtmosphereOwnershipSearch.Snapshot result = null;
        for (int i = 0; i < 1_000_000; i++) {
            result = search.step(probe, 512);
            if (result.status() == AtmosphereOwnershipSearch.Status.IN_PROGRESS)
                assertTrue(result.visitedOpenCells().isEmpty());
            else break;
        }
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, result.status());
        assertEquals(8_100, result.visitedOpenCells().size());
    }

    @Test
    void unloadedBoundaryIsUnknownAndCandidateSaturationNeverClassifiesFinite() {
        var unknown = run(new AtmosphereOwnershipSearch(BlockPos.ZERO), pos ->
                pos.equals(BlockPos.ZERO) ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                        : pos.getX() == 1 && pos.getY() == 0 && pos.getZ() == 0
                        ? AtmosphereOwnershipSearch.ProbeResult.UNKNOWN_UNLOADED
                        : AtmosphereOwnershipSearch.ProbeResult.BLOCKED);
        assertEquals(AtmosphereOwnershipSearch.Status.UNKNOWN, unknown.status());

        var saturated = new AtmosphereOwnershipSearch(BlockPos.ZERO, 2);
        var capped = saturated.step(pos -> pos.equals(BlockPos.ZERO)
                ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                : AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED, 100);
        assertEquals(AtmosphereOwnershipSearch.Status.SATURATED, capped.status());
        assertEquals(2, capped.trackedCandidates());
    }

    @Test
    void stepHonorsStrictWorkBudgetAndTopologyChangeCancels() {
        var search = new AtmosphereOwnershipSearch(BlockPos.ZERO);
        AtomicInteger probes = new AtomicInteger();
        var first = search.step(pos -> {
            probes.incrementAndGet();
            return AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED;
        }, 2);
        assertTrue(probes.get() <= 2);
        assertEquals(AtmosphereOwnershipSearch.Status.IN_PROGRESS, first.status());
        assertEquals(AtmosphereOwnershipSearch.Status.CANCELLED, search.topologyChanged().status());
        assertEquals(AtmosphereOwnershipSearch.Status.CANCELLED,
                search.step(pos -> fail("cancelled search must not probe"), 10).status());
    }

    private static AtmosphereOwnershipSearch.ProbeResult probe(Set<BlockPos> open, Set<BlockPos> sky,
                                                                 Set<BlockPos> claims, BlockPos pos) {
        if (claims.contains(pos)) return AtmosphereOwnershipSearch.ProbeResult.FINITE_CLAIMED;
        if (sky.contains(pos)) return AtmosphereOwnershipSearch.ProbeResult.OPEN_SKY_SEED;
        return open.contains(pos) ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                : AtmosphereOwnershipSearch.ProbeResult.BLOCKED;
    }

    private static AtmosphereOwnershipSearch.Snapshot run(AtmosphereOwnershipSearch search,
                                                            AtmosphereOwnershipSearch.NeighborProbe probe) {
        AtmosphereOwnershipSearch.Snapshot result = null;
        for (int i = 0; i < 1_000_000; i++) {
            result = search.step(probe, 512);
            if (result.status() != AtmosphereOwnershipSearch.Status.IN_PROGRESS) return result;
        }
        fail("Ownership search did not settle");
        return result;
    }
}
