package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereOwnershipSchedulerTest {
    @Test
    void unrelatedTopologyKeepsProofWhileDependencyChangeInvalidatesIt() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos unrelatedSeed = new BlockPos(32, 64, 0);
        BlockPos activeCoveredGap = new BlockPos(3, 64, 0);
        work.offer(unrelatedSeed);
        work.rememberExteriorProof(activeCoveredGap, java.util.List.of(activeCoveredGap,
                new BlockPos(2, 64, 0), new BlockPos(1, 64, 0)));
        work.invalidateAt(new BlockPos(40, 64, 0));

        assertTrue(work.isVerifiedExterior(activeCoveredGap), "unrelated changes must preserve the verified classification");
        work.invalidateAt(new BlockPos(1, 64, 0));
        assertFalse(work.isVerifiedExterior(activeCoveredGap), "a changed proof dependency must fail closed");
        work.offer(activeCoveredGap);
        assertEquals(unrelatedSeed, work.startNext(), "existing work retains FIFO order");
        work.finishActive();
        assertEquals(activeCoveredGap, work.startNext(), "the affected boundary can be automatically retried");
    }

    @Test
    void overlappingProofDoesNotDiscardEarlierCoveredGap() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos firstGap = new BlockPos(1, 64, 0);
        BlockPos secondGap = new BlockPos(4, 64, 0);
        BlockPos shared = new BlockPos(2, 64, 0);
        work.rememberExteriorProof(firstGap, java.util.List.of(firstGap, shared));
        work.rememberExteriorProof(secondGap, java.util.List.of(secondGap, shared));

        assertTrue(work.isVerifiedExterior(firstGap));
        assertTrue(work.isVerifiedExterior(secondGap));
        assertTrue(work.invalidateAt(new BlockPos(1, 64, 0)).contains(firstGap));
        assertFalse(work.isVerifiedExterior(firstGap));
        assertTrue(work.isVerifiedExterior(secondGap), "invalidating one witness must preserve overlapping independent proof");
    }

    @Test
    void unindexedExteriorMarkerIsNeverTrusted() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos unindexed = new BlockPos(1, 64, 0);
        work.exterior.add(unindexed);

        assertFalse(work.isVerifiedExterior(unindexed), "a cache marker without a live proof must fail closed");
    }

    @Test
    void invalidationRetriesEveryProofAndRoofColumnInvalidatesWitness() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos shared = new BlockPos(5, 64, 5);
        BlockPos firstSeed = new BlockPos(4, 64, 5);
        BlockPos secondSeed = new BlockPos(6, 64, 5);
        work.rememberExteriorProof(firstSeed, java.util.List.of(firstSeed, shared));
        work.rememberExteriorProof(secondSeed, java.util.List.of(secondSeed, shared));

        assertEquals(java.util.Set.of(firstSeed, secondSeed),
                new java.util.HashSet<>(work.invalidateAt(new BlockPos(5, 200, 5))),
                "a roof edit must invalidate and retry every proof in its column");
        assertFalse(work.isVerifiedExterior(firstSeed));
        assertFalse(work.isVerifiedExterior(secondSeed));
    }

    @Test
    void proofRemovalReleasesReverseIndexCapacity() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos dependency = new BlockPos(1, 64, 0);
        work.rememberExteriorProof(first, java.util.List.of(first, dependency));
        assertEquals(2, work.proofReferenceCountForTesting());

        work.invalidateAt(first);
        assertEquals(0, work.proofReferenceCountForTesting(), "removed proof dependencies release capacity");
        BlockPos replacement = new BlockPos(2, 64, 0);
        work.rememberExteriorProof(replacement, java.util.List.of(replacement, dependency));
        assertTrue(work.isVerifiedExterior(replacement), "freed capacity accepts a new proof");
    }

    @Test
    void boundedCacheEvictionRemovesOldReverseDependencies() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        java.util.List<BlockPos> fullCapacity = new java.util.ArrayList<>();
        for (int i = 0; i < AtmosphereService.MAX_EXTERIOR_WITNESS_CHECKS; i++)
            fullCapacity.add(new BlockPos(i, 64, 0));
        BlockPos evictedCell = fullCapacity.get(0);
        work.rememberExteriorProof(evictedCell, fullCapacity);
        BlockPos replacement = new BlockPos(50_000, 64, 0);
        work.rememberExteriorProof(replacement, java.util.List.of(replacement));

        assertFalse(work.isVerifiedExterior(evictedCell), "eviction removes the old cache classification");
        assertTrue(work.isVerifiedExterior(replacement));
        assertEquals(1, work.proofReferenceCountForTesting(), "eviction releases its reverse-index budget");
    }

    @Test
    void inProgressSearchWitnessIsRevalidatedAgainstCurrentTopologyBeforeCaching() {
        java.util.List<BlockPos> witness = java.util.List.of(BlockPos.ZERO, new BlockPos(0, 1, 0));
        assertFalse(AtmosphereService.isExteriorWitnessValid(witness,
                pos -> !pos.equals(BlockPos.ZERO), pos -> pos.equals(new BlockPos(0, 1, 0))),
                "a route closed while search was in progress must not be accepted");
        assertFalse(AtmosphereService.isExteriorWitnessValid(witness, pos -> true, pos -> false),
                "a roof added during search must invalidate its former sky endpoint");
        assertTrue(AtmosphereService.isExteriorWitnessValid(witness, pos -> true,
                pos -> pos.equals(new BlockPos(0, 1, 0))));
    }

    @Test
    void openableTrackerDetectsOpenCloseAndReopenButSuppressesDuplicateNotifications() {
        AtmosphereService.OpenableStateTracker tracker = new AtmosphereService.OpenableStateTracker();
        BlockPos door = new BlockPos(3, 64, 0);

        assertFalse(tracker.observe(door, false), "first-seen closed state is only a baseline");
        assertTrue(tracker.observe(door, true), "closed-to-open must activate topology");
        assertFalse(tracker.observe(door, true), "duplicate open notifications must be ignored");
        assertTrue(tracker.observe(door, false), "open-to-closed must invalidate topology");
        assertFalse(tracker.observe(door, false), "duplicate closed notifications must be ignored");
        assertTrue(tracker.observe(door, true), "reopening must activate topology again");
        assertTrue(tracker.observe(new BlockPos(4, 64, 0), true),
                "a first-observed openable block must activate classification without prior tracking");
    }

    @Test
    void topologyInvalidationCancelsActiveSearchAndPendingFiniteClaimPlan() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos activeSeed = new BlockPos(0, 64, 0);
        BlockPos planSeed = new BlockPos(8, 64, 0);
        BlockPos completedExterior = new BlockPos(20, 64, 0);
        work.rememberExteriorProof(completedExterior, java.util.List.of(completedExterior));
        work.offer(activeSeed);
        assertEquals(activeSeed, work.startNext());
        AtmosphereOwnershipSearch activeSearch = work.activeSearchForTesting();
        var progress = activeSearch.step(pos -> pos.equals(activeSeed)
                ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                : AtmosphereOwnershipSearch.ProbeResult.BLOCKED, 1);
        assertEquals(AtmosphereOwnershipSearch.Status.IN_PROGRESS, progress.status());

        OwnershipClaimPlan plan = new OwnershipClaimPlan(java.util.List.of(planSeed, planSeed.above()));
        assertEquals(1, plan.advance(1, ignored -> true));
        assertTrue(work.queueClaimPlan(planSeed, plan));

        java.util.List<BlockPos> retrySeeds = work.cancelPendingOwnership();

        assertFalse(work.hasActive(), "active search must be cancelled and detached");
        assertEquals(AtmosphereOwnershipSearch.Status.CANCELLED,
                activeSearch.step(ignored -> fail("cancelled search must not probe"), 1).status());
        assertTrue(plan.isCancelled(), "uncommitted finite claim tail must be cancelled");
        assertEquals(0, plan.advance(1, ignored -> fail("cancelled claim plan must not commit stale cells")));
        assertTrue(work.isVerifiedExterior(completedExterior),
                "restarting unfinished work must preserve unrelated completed exterior proofs");
        assertEquals(java.util.Set.of(activeSeed, planSeed), new java.util.HashSet<>(retrySeeds),
                "both interrupted proofs must be scheduled for reclassification");
    }

    @Test
    void unrelatedChunkChurnPreservesActiveSearchAndClaimCursor() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos searchSeed = new BlockPos(0, 64, 0);
        BlockPos planSeed = new BlockPos(32, 64, 0);
        work.offer(searchSeed);
        work.startNext();
        AtmosphereOwnershipSearch search = work.activeSearchForTesting();
        search.step(pos -> AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED, 8);

        OwnershipClaimPlan plan = new OwnershipClaimPlan(java.util.List.of(planSeed, planSeed.above()));
        assertEquals(1, plan.advance(1, ignored -> true));
        assertTrue(work.queueClaimPlan(planSeed, plan));

        for (int i = 0; i < 20; i++)
            assertTrue(work.onChunkLifecycle(new ChunkPos(100 + i, 100)).isEmpty());

        assertSame(search, work.activeSearchForTesting());
        assertFalse(search.step(pos -> AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED, 1).status()
                == AtmosphereOwnershipSearch.Status.CANCELLED);
        assertEquals(1, plan.cursor());
        assertFalse(plan.isCancelled());
    }

    @Test
    void dependentChunkLifecycleRetriesSearchPlanAndInvalidatesExteriorProof() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos seed = new BlockPos(0, 64, 0);
        BlockPos planSeed = new BlockPos(8, 64, 0);
        BlockPos exterior = new BlockPos(48, 64, 0);
        BlockPos proofCell = new BlockPos(49, 64, 0);
        work.offer(seed);
        work.startNext();
        AtmosphereOwnershipSearch search = work.activeSearchForTesting();
        search.step(pos -> AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED, 1);
        OwnershipClaimPlan plan = new OwnershipClaimPlan(java.util.List.of(planSeed, planSeed.above()));
        assertTrue(work.queueClaimPlan(planSeed, plan));
        work.rememberExteriorProof(exterior, java.util.List.of(exterior, proofCell));

        assertEquals(java.util.List.of(seed, planSeed), work.retryForChunkLifecycle(new ChunkPos(0, 0)));
        assertEquals(2, work.queuedSeedCount(), "both invalidated jobs must be requeued");
        assertTrue(search.step(ignored -> fail("dependent search was not cancelled"), 1).status()
                == AtmosphereOwnershipSearch.Status.CANCELLED);
        assertTrue(plan.isCancelled());
        assertEquals(seed, work.startNext());
        work.finishActive();
        assertEquals(planSeed, work.startNext(), "the finite plan seed must not be lost during unload retry");
        work.finishActive();
        assertTrue(work.invalidateChunk(new ChunkPos(3, 0)).contains(exterior));
        assertFalse(work.isVerifiedExterior(exterior), "unload of a proof dependency must fail closed");
    }

    @Test
    void unloadedBoundaryParksSeedWhileAnotherRoomProgressesThenLoadWakesIt() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos unloadedRoomSeed = new BlockPos(0, 64, 0);
        BlockPos smallRoomSeed = new BlockPos(32, 64, 0);
        assertTrue(work.offer(unloadedRoomSeed));
        assertTrue(work.offer(smallRoomSeed));

        assertEquals(unloadedRoomSeed, work.startNext());
        var unknown = new AtmosphereOwnershipSearch(unloadedRoomSeed).step(
                pos -> pos.equals(unloadedRoomSeed)
                        ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                        : AtmosphereOwnershipSearch.ProbeResult.UNKNOWN_UNLOADED,
                100);
        assertEquals(AtmosphereOwnershipSearch.Status.UNKNOWN, unknown.status());
        work.parkUnknown(unloadedRoomSeed);
        work.finishActive();
        assertEquals(1, work.unknownSeedCount());
        assertEquals(0, work.saturatedChunkCount());

        assertEquals(smallRoomSeed, work.startNext());
        var finiteRoom = new AtmosphereOwnershipSearch(smallRoomSeed).step(
                pos -> pos.equals(smallRoomSeed)
                        ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                        : AtmosphereOwnershipSearch.ProbeResult.BLOCKED,
                20);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, finiteRoom.status());
        assertTrue(work.unknownSeedCount() == 1,
                "the unresolved room remains parked rather than blocking or being misclassified");

        work.finishActive();
        work.wakeUnknown(); // Chunk load or topology change makes the paused seed retryable.
        assertEquals(0, work.unknownSeedCount());
        assertEquals(unloadedRoomSeed, work.startNext());
    }

    @Test
    void parkedUnknownSearchWakesOnlyForAProbedUnloadedChunk() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos seed = new BlockPos(0, 64, 0);
        ChunkPos dependency = new ChunkPos(2, 0);
        work.parkUnknown(seed, java.util.Set.of(dependency));

        work.wakeUnknown(new ChunkPos(20, 0));
        assertEquals(1, work.unknownSeedCount());
        assertEquals(0, work.queuedSeedCount());
        work.wakeUnknown(dependency);
        assertEquals(0, work.unknownSeedCount());
        assertEquals(seed, work.startNext());
    }

    @Test
    void saturatedSearchParksItsSeedAndDoesNotBlockTheNextRoom() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos saturatedSeed = new BlockPos(0, 64, 0);
        BlockPos smallRoomSeed = new BlockPos(32, 64, 0);
        assertTrue(work.offer(saturatedSeed));
        assertTrue(work.offer(smallRoomSeed));

        assertEquals(saturatedSeed, work.startNext());
        work.parkSaturated(saturatedSeed);
        assertFalse(work.hasActive());
        assertTrue(work.isSaturated(saturatedSeed));
        assertFalse(work.exterior.contains(saturatedSeed), "saturation is unknown, never exterior");

        assertEquals(smallRoomSeed, work.startNext());
        var finiteRoom = new AtmosphereOwnershipSearch(smallRoomSeed).step(
                pos -> pos.equals(smallRoomSeed)
                        ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                        : AtmosphereOwnershipSearch.ProbeResult.BLOCKED,
                20);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, finiteRoom.status());
        assertEquals(java.util.List.of(smallRoomSeed), finiteRoom.visitedOpenCells());
        assertTrue(work.isSaturated(saturatedSeed), "the large region remains parked, not retried every tick");

        work.finishActive(); // the completed small-room job releases the single active slot
        work.wakeSaturated(); // a later topology/chunk lifecycle event makes the seed retryable
        assertFalse(work.isSaturated(saturatedSeed));
        assertEquals(saturatedSeed, work.startNext());
    }

    @Test
    void skyExposureDoesNotEraseGasFromAnExistingFiniteClaim() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        BlockPos savedCell = BlockPos.ZERO;
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture savedGas = new GasMixture(Map.of(GasType.OXYGEN, 7.0), 315.0);
        data.claimFinite(0, 0, 0);
        data.put(0, 0, 0, savedGas, ambient);

        // A newly sky-exposed neighboring cell is only a boundary sink. The claimed cell's
        // identity and gas remain authoritative; this classifier never converts it to exterior.
        var result = new AtmosphereOwnershipSearch(savedCell).step(
                pos -> pos.equals(savedCell) ? AtmosphereOwnershipSearch.ProbeResult.FINITE_CLAIMED
                        : pos.equals(new BlockPos(0, 1, 0)) ? AtmosphereOwnershipSearch.ProbeResult.OPEN_SKY_SEED
                        : AtmosphereOwnershipSearch.ProbeResult.BLOCKED,
                1);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, result.status());
        assertTrue(data.isFiniteClaimed(0, 0, 0));
        assertEquals(savedGas.gasMoles(), data.get(0, 0, 0).gasMoles());
        assertEquals(savedGas.temperatureKelvin(), data.get(0, 0, 0).temperatureKelvin());
    }
}
