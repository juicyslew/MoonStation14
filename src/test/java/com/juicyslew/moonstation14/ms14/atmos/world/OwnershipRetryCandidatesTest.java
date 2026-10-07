package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipRetryCandidatesTest {
    @Test
    void blockedFirstCandidateDoesNotHideDisconnectedTailAndChecksAreBounded() {
        BlockPos blocked = BlockPos.ZERO;
        BlockPos disconnected = new BlockPos(100, 64, 0);
        OwnershipRetryCandidates candidates = new OwnershipRetryCandidates();
        candidates.add(List.of(blocked, disconnected));
        List<BlockPos> inspected = new ArrayList<>();

        assertEquals(1, candidates.drain(1, pos -> { inspected.add(pos); return true; }));
        assertEquals(List.of(blocked), inspected);
        assertFalse(candidates.isEmpty());
        assertEquals(1, candidates.drain(4, pos -> { inspected.add(pos); return true; }));
        assertEquals(List.of(blocked, disconnected), inspected);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void finiteStaleCandidatesAreConsumedWithoutExceedingPerDrainBudget() {
        OwnershipRetryCandidates candidates = new OwnershipRetryCandidates();
        List<BlockPos> positions = List.of(BlockPos.ZERO, new BlockPos(1, 64, 0), new BlockPos(2, 64, 0));
        candidates.add(positions);
        int[] checked = {0};
        assertEquals(2, candidates.drain(2, pos -> { checked[0]++; return true; }));
        assertEquals(2, checked[0]);
        assertEquals(1, candidates.drain(1, pos -> { checked[0]++; return true; }));
        assertEquals(3, checked[0]);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void capacityFailureCountsInspectionAndDefersWithoutBlockingNextCandidate() {
        BlockPos full = BlockPos.ZERO;
        BlockPos later = new BlockPos(100, 64, 0);
        OwnershipRetryCandidates candidates = new OwnershipRetryCandidates();
        candidates.add(List.of(full, later));
        List<BlockPos> accepted = new ArrayList<>();

        assertEquals(2, candidates.drain(2, pos -> {
            if (pos.equals(full)) return false; // queue capacity: retain for a later bounded retry
            accepted.add(pos);
            return true;
        }));
        assertEquals(List.of(later), accepted);
        assertEquals(1, candidates.pendingWorkItems());
        assertEquals(1, candidates.drain(4, ignored -> true));
        assertTrue(candidates.isEmpty());
    }

    @Test
    void unloadedFirstCandidateUsesOrdinaryChunkParkAndDoesNotBlockLoadedTail() {
        BlockPos unloaded = new BlockPos(16, 64, 0);
        BlockPos loadedTail = new BlockPos(100, 64, 0);
        OwnershipRetryCandidates candidates = new OwnershipRetryCandidates();
        candidates.add(List.of(unloaded, loadedTail));
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        List<BlockPos> loadedForFreshSearch = new ArrayList<>();

        assertEquals(2, candidates.drain(2, pos -> {
            if (pos.equals(unloaded)) return work.offer(pos); // normal discovery parks it UNKNOWN
            loadedForFreshSearch.add(pos);
            return true;
        }));
        assertEquals(List.of(loadedTail), loadedForFreshSearch);
        assertTrue(candidates.isEmpty());

        assertEquals(unloaded, work.startNext());
        ChunkPos dependency = new ChunkPos(unloaded);
        work.parkUnknown(unloaded, java.util.Set.of(dependency));
        work.wakeUnknown(dependency); // chunk load reoffers the seed for a fresh proof
        assertEquals(unloaded, work.startNext());
    }

    @Test
    void pendingWorkDiagnosticIncludesRetrySuffixBatchInConstantTime() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos seed = BlockPos.ZERO;
        assertTrue(work.queueClaimPlan(seed, new OwnershipClaimPlan(List.of(seed, seed.above(), seed.east()))));

        work.cancelPendingOwnership();

        assertEquals(1, work.pendingCount(), "one suffix batch is one pending work item regardless of its size");
        work.retryCandidates.drain(8, ignored -> true);
        assertEquals(0, work.pendingCount());
    }
}
