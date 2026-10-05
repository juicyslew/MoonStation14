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
    void repeatedClosedOpenableOnlyInvalidatesAnExactWitnessCell() {
        BlockPos proofSeed = new BlockPos(5, 70, 5);
        BlockPos closedHatch = new BlockPos(5, 64, 5);

        AtmosphereService.OwnershipWork aboveHatch = new AtmosphereService.OwnershipWork();
        aboveHatch.rememberExteriorProof(proofSeed, java.util.List.of(proofSeed, proofSeed.below()));
        assertTrue(aboveHatch.hasExteriorDependency(closedHatch), "the column remains a broad topology dependency");
        assertFalse(aboveHatch.hasExteriorWitnessCell(closedHatch));
        AtmosphereService.OpenableStateTracker tracker = new AtmosphereService.OpenableStateTracker();
        assertFalse(tracker.observe(closedHatch, false));
        boolean repeatedClosed = tracker.observe(closedHatch, false);
        assertFalse(AtmosphereService.shouldInvalidateClosedOpenable(false, repeatedClosed,
                aboveHatch.hasExteriorWitnessCell(closedHatch)));
        assertTrue(aboveHatch.isVerifiedExterior(proofSeed), "closed non-witness leaves preserve the proof");

        AtmosphereService.OwnershipWork atHatch = new AtmosphereService.OwnershipWork();
        atHatch.rememberExteriorProof(proofSeed, java.util.List.of(proofSeed, closedHatch));
        AtmosphereService.OpenableStateTracker exactTracker = new AtmosphereService.OpenableStateTracker();
        assertFalse(exactTracker.observe(closedHatch, false));
        boolean exactChanged = exactTracker.observe(closedHatch, false);
        boolean exactInvalidation = AtmosphereService.shouldInvalidateClosedOpenable(false, exactChanged,
                atHatch.hasExteriorWitnessCell(closedHatch));
        assertTrue(exactInvalidation);
        if (exactInvalidation) atHatch.invalidateAt(closedHatch);
        assertFalse(atHatch.isVerifiedExterior(proofSeed));
    }

    @Test
    void actualOpenableStateTransitionStillInvalidatesColumnDependentProof() {
        BlockPos proofSeed = new BlockPos(5, 70, 5);
        BlockPos door = new BlockPos(5, 64, 5);
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        work.rememberExteriorProof(proofSeed, java.util.List.of(proofSeed, proofSeed.below()));
        AtmosphereService.OpenableStateTracker tracker = new AtmosphereService.OpenableStateTracker();
        assertTrue(tracker.observe(door, true), "first-seen open portals trigger topology work");
        boolean changed = tracker.observe(door, false);
        assertTrue(AtmosphereService.shouldInvalidateClosedOpenable(false, changed,
                work.hasExteriorWitnessCell(door)));
        assertTrue(work.invalidateAt(door).contains(proofSeed));
        assertFalse(work.isVerifiedExterior(proofSeed));
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
    void largeCoherentWitnessIsRetainedAndInvalidatedAcrossEveryDependency() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        java.util.List<BlockPos> witness = new java.util.ArrayList<>();
        for (int i = 0; i < 5_000; i++) witness.add(new BlockPos(i, 64, 0));
        BlockPos seed = witness.get(0);
        BlockPos tail = witness.get(witness.size() - 1);

        assertTrue(AtmosphereService.isExteriorWitnessValid(witness, pos -> true, pos -> pos.equals(tail)));
        assertTrue(work.rememberExteriorProof(seed, witness));
        assertTrue(work.isVerifiedExterior(seed));
        assertTrue(work.isVerifiedExterior(tail));
        assertEquals(witness.size(), work.proofReferenceCountForTesting());

        assertTrue(work.invalidateAt(new BlockPos(2_500, 64, 0)).contains(seed));
        assertFalse(work.isVerifiedExterior(seed));
        assertFalse(work.isVerifiedExterior(tail), "all classifications depend on the complete witness");
        assertEquals(0, work.proofReferenceCountForTesting());
    }

    @Test
    void ownershipDiagnosticExplainsQueuedAndRejectedSeedsWithoutChangingCellStatus() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos seed = new BlockPos(7, 64, 9);
        assertTrue(work.offer(seed));
        AtmosphereService.OwnershipDiagnostic queued = work.diagnostic(seed);
        assertEquals("SEED_QUEUED", queued.status());
        assertNull(queued.lastRejectedSeed());
        assertTrue(AtmosphereService.rejectExteriorWitnessForAdmission(work, seed,
                "witness dependency unloaded", 5_001));
        AtmosphereService.OwnershipDiagnostic rejected = work.diagnostic(seed);
        assertEquals("witness dependency unloaded", rejected.lastRejectedReason());
        assertEquals(5_001, rejected.lastRejectedWitnessSize());
        assertEquals("SEED_QUEUED", rejected.status(), "retry remains queued and cell status is unaffected");
    }

    @Test
    void oversizedWitnessIsRejectedByLiveValidationGuard() {
        java.util.List<BlockPos> oversized = java.util.Collections.nCopies(
                AtmosphereService.MAX_EXTERIOR_WITNESS_CHECKS + 1, BlockPos.ZERO);
        assertFalse(AtmosphereService.isExteriorWitnessValid(oversized, pos -> true, pos -> true));
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
        assertEquals(java.util.List.of(activeSeed), retrySeeds,
                "active search seed must be retried; finite plan retries its uncommitted positions");
        java.util.List<BlockPos> tail = new java.util.ArrayList<>();
        assertEquals(1, work.retryCandidates.drain(2, pos -> { tail.add(pos); return true; }));
        assertEquals(java.util.List.of(planSeed.above()), tail,
                "the already-committed prefix must not be retried");
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

        assertEquals(java.util.List.of(seed), work.retryForChunkLifecycle(new ChunkPos(0, 0)));
        assertEquals(1, work.queuedSeedCount(), "active interrupted search is requeued");
        assertTrue(search.step(ignored -> fail("dependent search was not cancelled"), 1).status()
                == AtmosphereOwnershipSearch.Status.CANCELLED);
        assertTrue(plan.isCancelled());
        assertEquals(seed, work.startNext());
        work.finishActive();
        java.util.List<BlockPos> retryTail = new java.util.ArrayList<>();
        assertEquals(2, work.retryCandidates.drain(2, pos -> { retryTail.add(pos); return true; }));
        assertEquals(java.util.List.of(planSeed, planSeed.above()), retryTail,
                "the finite plan tail must be retried as fresh candidates");
        retryTail.forEach(work::offer);
        assertEquals(planSeed, work.startNext());
        work.finishActive();
        assertTrue(work.invalidateChunk(new ChunkPos(3, 0)).contains(exterior));
        assertFalse(work.isVerifiedExterior(exterior), "unload of a proof dependency must fail closed");
    }

    @Test
    void staleFiniteHeadAndRetryCandidatesShareBudgetWithoutQueueRescanStarvation() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos staleFinite = BlockPos.ZERO;
        BlockPos stillUnknown = new BlockPos(1, 64, 0);
        work.offer(staleFinite);
        work.offer(stillUnknown);
        for (int i = 0; i < 500; i++) work.offer(new BlockPos(1000 + i, 64, 0));
        work.retryCandidates.add(java.util.List.of(new BlockPos(10, 64, 0), new BlockPos(100, 64, 0),
                new BlockPos(200, 64, 0)));

        int retryChecks = work.retryCandidates.drain(2, pos -> true);
        int seedChecks = work.startNextSkippingClassified(4 - retryChecks, pos -> pos.equals(staleFinite));

        assertEquals(2, retryChecks);
        assertEquals(2, seedChecks, "drop the stale head and stop after activating the first fresh seed");
        assertTrue(work.hasActive());
        assertEquals(500, work.queuedSeedCount(), "hundreds of nonfinite queued seeds stay queued for later");
        assertFalse(work.retryCandidates.isEmpty(), "bounded drain leaves remaining candidates pending");
    }

    @Test
    void queuedSeedsCoveredByValidExteriorProofAreSkippedWithoutDuplicateProof() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos covered = new BlockPos(1, 64, 0);
        BlockPos finalSeed = new BlockPos(2, 64, 0);
        java.util.List<BlockPos> witness = java.util.List.of(first, covered, finalSeed);
        assertTrue(work.rememberExteriorProof(first, witness));
        int proofRefs = work.proofReferenceCountForTesting();
        work.offer(covered);
        work.offer(finalSeed);

        int checks = work.startNextSkippingClassified(2,
                seed -> loadedPassable(seed, witness) && work.isVerifiedExterior(seed));

        assertEquals(2, checks);
        assertFalse(work.hasActive(), "covered queued seeds need no redundant BFS");
        assertEquals(0, work.queuedSeedCount());
        assertEquals(proofRefs, work.proofReferenceCountForTesting());
        assertEquals(1, work.proofs.size());
        witness.forEach(pos -> assertTrue(work.isVerifiedExterior(pos)));
    }

    @Test
    void invalidatedOrUnloadedProofSeedIsRetriedAndUnclassifiedSeedsStartSearch() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos seed = new BlockPos(0, 64, 0);
        assertTrue(work.rememberExteriorProof(seed, java.util.List.of(seed, seed.above())));
        work.offer(seed);
        java.util.List<BlockPos> retries = work.invalidateChunk(new ChunkPos(seed));
        retries.forEach(work::offer);

        int checks = work.startNextSkippingClassified(1,
                pos -> loadedPassable(pos, java.util.List.of(seed, seed.above())) && work.isVerifiedExterior(pos));

        assertEquals(1, checks);
        assertEquals(seed, work.startNext(), "invalidated proof is unknown and must be searched again");
    }

    @Test
    void exteriorCacheDoesNotSkipUnloadedOrBlockedSeeds() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos seed = BlockPos.ZERO;
        assertTrue(work.rememberExteriorProof(seed, java.util.List.of(seed, seed.above())));
        work.offer(seed);

        int checks = work.startNextSkippingClassified(1, pos -> false);

        assertEquals(1, checks);
        assertEquals(seed, work.startNext(), "normal search must handle unloaded/blocked candidates");
        assertTrue(work.isVerifiedExterior(seed), "the existing proof is not modified by a rejected skip predicate");
    }

    private static boolean loadedPassable(BlockPos pos, java.util.List<BlockPos> loadedPassable) {
        return loadedPassable.contains(pos);
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
