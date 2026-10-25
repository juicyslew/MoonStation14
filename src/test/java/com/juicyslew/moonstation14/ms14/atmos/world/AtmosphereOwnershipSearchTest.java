package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereOwnershipSearchTest {
    @Test
    void trustedSetupBudgetIsSeparateAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> AtmosphereService.trustedSetupCandidateBudget(0));
        assertEquals(7 * 1, AtmosphereService.trustedSetupCandidateBudget(1));
        assertEquals(7 * 1024, AtmosphereService.trustedSetupCandidateBudget(1024));
        assertEquals(7 * 5047, AtmosphereService.trustedSetupCandidateBudget(5047));
        assertEquals(7 * 8192, AtmosphereService.trustedSetupCandidateBudget(8192));
        assertThrows(IllegalArgumentException.class, () -> AtmosphereService.trustedSetupCandidateBudget(8193));
        assertEquals(1024, AtmosphereService.MAX_FINITE_ROOM_ENDPOINT_PROBES);
        assertEquals(8 * 8192, 8 * AtmosphereService.MAX_TRUSTED_SETUP_CELLS);
    }

    @Test
    void boundedSearchProvesAuthoredRoomLargerThanNormalProbeLimit() {
        Set<BlockPos> room = new HashSet<>();
        for (int x = 0; x < 5047; x++) room.add(new BlockPos(x, 0, 0));
        var probe = (AtmosphereOwnershipSearch.NeighborProbe) pos -> probe(room, Set.of(), Set.of(), pos);
        var search = new AtmosphereOwnershipSearch(BlockPos.ZERO,
                AtmosphereService.trustedSetupCandidateBudget(room.size()));
        int calls = 0;
        AtmosphereOwnershipSearch.Snapshot result;
        do { calls++; result = search.step(probe, 1); }
        while (result.status() == AtmosphereOwnershipSearch.Status.IN_PROGRESS);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, result.status());
        assertEquals(5047, result.visitedOpenCells().size());
        assertTrue(calls <= 8 * 5047);
    }

    @Test
    void connectedBotanySizedRoomsExceedOldCandidateCapButFitPerCellBound() {
        Set<BlockPos> room = new HashSet<>();
        BlockPos main = BlockPos.ZERO;
        for (BlockPos origin : SmallBotanyFixtureLayout.roomOrigins(main))
            for (int x = 1; x < SmallBotanyFixtureLayout.WIDTH - 1; x++)
                for (int y = 1; y < SmallBotanyFixtureLayout.HEIGHT - 1; y++)
                    for (int z = 1; z < SmallBotanyFixtureLayout.DEPTH - 1; z++)
                        room.add(origin.offset(x, y, z));
        SmallBotanyFixtureLayout.connectorPlan(main).stream().filter(SmallBotanyFixtureLayout.ConnectorCell::opening)
                .map(SmallBotanyFixtureLayout.ConnectorCell::pos).forEach(room::add);
        assertEquals(Set.of(23, 24, 48, 49), SmallBotanyFixtureLayout.connectorPlan(main).stream()
                .filter(cell -> !cell.opening() && cell.pos().getZ() == 5 && cell.pos().getY() == 0)
                .map(cell -> cell.pos().getX()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(22, 23, 24, 25, 47, 48, 49, 50), SmallBotanyFixtureLayout.connectorPlan(main).stream()
                .filter(SmallBotanyFixtureLayout.ConnectorCell::opening).filter(cell -> cell.pos().getZ() == 5 && cell.pos().getY() == 1)
                .map(cell -> cell.pos().getX()).collect(java.util.stream.Collectors.toSet()));
        var probe = (AtmosphereOwnershipSearch.NeighborProbe) pos -> probe(room, Set.of(), Set.of(), pos);
        BlockPos seed = room.iterator().next();
        var oldLimit = run(new AtmosphereOwnershipSearch(seed, 1024), probe);
        assertEquals(AtmosphereOwnershipSearch.Status.SATURATED, oldLimit.status());
        int candidateBudget = AtmosphereService.trustedSetupCandidateBudget(room.size());
        var bounded = new AtmosphereOwnershipSearch(seed, candidateBudget);
        int calls = 0;
        AtmosphereOwnershipSearch.Snapshot result;
        do { calls++; result = bounded.step(probe, 1); }
        while (result.status() == AtmosphereOwnershipSearch.Status.IN_PROGRESS);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, result.status());
        assertEquals(room.size(), result.visitedOpenCells().size());
        assertTrue(result.trackedCandidates() > 1024);
        assertTrue(result.trackedCandidates() <= candidateBudget);
        assertTrue(calls <= 8 * room.size());
    }

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
    void finiteRoomStepCallsFitPerCellBudgetIncludingFinalCompletionCall() {
        Set<BlockPos> room = new HashSet<>();
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 6; y++)
                for (int z = 0; z < 11; z++) room.add(new BlockPos(x, y, z));
        var search = new AtmosphereOwnershipSearch(BlockPos.ZERO, 1024);
        var probe = (AtmosphereOwnershipSearch.NeighborProbe) pos -> probe(room, Set.of(), Set.of(), pos);
        int calls = 0;
        AtmosphereOwnershipSearch.Snapshot result;
        do {
            calls++;
            result = search.step(probe, 1);
        } while (result.status() == AtmosphereOwnershipSearch.Status.IN_PROGRESS);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, result.status());
        assertEquals(264, result.visitedOpenCells().size());
        assertTrue(calls > 1024, "the former total-call cap rejected this proven room");
        assertTrue(calls <= 8 * room.size());
        assertTrue(result.trackedCandidates() <= 1024, "candidate storage limit remains bounded");
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
