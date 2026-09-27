package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereWorkQueueTest {
    @Test
    void atmosphereRunsEveryTwoGameTicksOnlyWhenEnabled() {
        assertEquals(0.1, AtmosphereService.atmosphereStepSeconds, 1.0e-12);
        assertTrue(AtmosphereService.isTickDue(true, 0));
        assertFalse(AtmosphereService.isTickDue(true, 1));
        assertTrue(AtmosphereService.isTickDue(true, 2));
        assertFalse(AtmosphereService.isTickDue(true, 3));
        assertTrue(AtmosphereService.isTickDue(true, 4));
        for (long gameTime = 0; gameTime <= 4; gameTime++)
            assertFalse(AtmosphereService.isTickDue(false, gameTime), "disabled at game time " + gameTime);
    }

    @Test
    void perStepBudgetsAreHalvedAndPreserveThePriorPerSecondAllowance() {
        assertEquals(64, AtmosphereService.MAX_PAIR_EDGES_PER_TICK);
        assertEquals(256, AtmosphereService.MAX_CELLS_INSPECTED_PER_TICK);
        assertEquals(384, AtmosphereService.MAX_DISCOVERY_PROBES_PER_TICK);
        assertEquals(128, AtmosphereService.MAX_OWNERSHIP_CLAIMS_PER_TICK);
        assertEquals(64, AtmosphereService.MAX_DIRTY_CURSOR_STEPS_PER_TICK);

        assertEquals(128 * 5, AtmosphereService.MAX_PAIR_EDGES_PER_TICK * 10);
        assertEquals(512 * 5, AtmosphereService.MAX_CELLS_INSPECTED_PER_TICK * 10);
        assertEquals(768 * 5, AtmosphereService.MAX_DISCOVERY_PROBES_PER_TICK * 10);
        assertEquals(256 * 5, AtmosphereService.MAX_OWNERSHIP_CLAIMS_PER_TICK * 10);
        assertEquals(128 * 5, AtmosphereService.MAX_DIRTY_CURSOR_STEPS_PER_TICK * 10);
    }

    @Test
    void lindaSourceAdmissionStaysWithinPairBudgetAndLeavesNextSourceQueued() {
        assertEquals(10, AtmosphereService.LINDA_MAX_ACTIVE_CELLS);
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < AtmosphereService.LINDA_MAX_ACTIVE_CELLS + 1; i++) {
            BlockPos source = new BlockPos(i, 64, 0);
            candidates.add(source);
            assertTrue(queue.offer(source));
        }

        int processed = 0;
        while (processed < AtmosphereService.LINDA_MAX_ACTIVE_CELLS && !queue.isEmpty()) {
            assertEquals(candidates.get(processed), queue.poll());
            processed++;
        }
        int sixFaceAttempts = processed * net.minecraft.core.Direction.values().length;
        assertEquals(10, processed);
        assertTrue(sixFaceAttempts <= AtmosphereService.MAX_PAIR_EDGES_PER_TICK,
                "all six faces open still fit the pair-edge budget");
        assertEquals(1, queue.pending.size());
        assertEquals(candidates.get(10), queue.poll(), "the 11th active source is retained for the next due step");
    }

    @Test
    void smallerDiscoveryBudgetStillCompletesSmallRoomInAReasonableNumberOfSteps() {
        java.util.Set<BlockPos> room = new java.util.HashSet<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 3; z++)
            room.add(new BlockPos(x, y, z));
        BoundedRegionDiscovery discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        BoundedRegionDiscovery.Snapshot result = null;
        int dueSteps = 0;
        for (; dueSteps < 4; dueSteps++) {
            result = discovery.step(pos -> room.contains(pos)
                    ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                    : BoundedRegionDiscovery.ProbeResult.BLOCKED,
                    AtmosphereService.MAX_DISCOVERY_PROBES_PER_TICK);
            if (result.status() == BoundedRegionDiscovery.Status.COMPLETE) break;
        }
        assertNotNull(result);
        assertEquals(BoundedRegionDiscovery.Status.COMPLETE, result.status());
        assertEquals(48, result.cells().size());
        assertTrue(dueSteps < 4);
    }

    @Test
    void excitedGroupsKeepTheirTwelveGameTickCycle() {
        var firstStage = AtmosphereService.excitedCycleSchedule(4, 12, false);
        var secondStage = AtmosphereService.excitedCycleSchedule(8, firstStage.nextDue(), false);
        assertFalse(firstStage.advance());
        assertFalse(secondStage.advance());
        assertEquals(12, secondStage.nextDue());
        var due = AtmosphereService.excitedCycleSchedule(12, secondStage.nextDue(), false);
        assertTrue(due.advance());
        assertEquals(24, due.nextDue());
    }

    @Test
    void deduplicatesHotPositionsAndRemovesDeduplicationOnPoll() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        BlockPos pos = new BlockPos(2, 64, -4);
        assertTrue(queue.offer(pos));
        assertFalse(queue.offer(pos));
        assertEquals(pos, queue.poll());
        assertTrue(queue.offer(pos));
    }

    @Test
    void urgentBreachCellJumpsOldFifoBacklogAndCanBeRescheduled() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        for (int i = 0; i < AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY; i++) {
            queue.offer(new BlockPos(i, 64, 0));
        }
        BlockPos breachAdjacent = new BlockPos(0, 70, 0);
        assertTrue(queue.offerUrgent(breachAdjacent));
        assertEquals(breachAdjacent, queue.poll());
        assertTrue(queue.offerUrgent(breachAdjacent));
        assertEquals(breachAdjacent, queue.poll());
        // The untouched FIFO continues to make progress once the bounded urgent quota is idle.
        assertEquals(new BlockPos(0, 64, 0), queue.poll());
    }

    @Test
    void saturatedUrgentLaneFallsBackToBoundedLoadedChunkWork() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        for (int i = 0; i < AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY; i++) {
            int chunkX = i % 2 == 0 ? 0 : 1;
            queue.offer(new BlockPos(chunkX * 16, i, 0));
        }
        for (int i = 0; i < 256; i++) {
            assertTrue(queue.offerUrgent(new BlockPos(64, i, 0)));
        }

        BlockPos alreadyHot = new BlockPos(0, 0, 0);
        BlockPos alreadyUrgent = new BlockPos(64, 0, 0);
        assertTrue(queue.offerUrgent(alreadyHot));
        assertTrue(queue.offerUrgent(alreadyUrgent));
        assertTrue(queue.pending.contains(alreadyHot));

        BlockPos overflowBreach = new BlockPos(32, 70, 0);
        assertTrue(queue.offerUrgent(overflowBreach));
        assertEquals(overflowBreach, queue.nextDirtyChunk().seed(),
                "the loaded overpressure cell is retained as ordinary chunk-coalesced work");
        assertEquals(AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY, queue.pending.size());
        assertEquals(AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY + 256, queue.queued.size());
        assertEquals(1, queue.dirtyChunks.size());
    }

    @Test
    void continuouslyRequeuedUrgentCellsYieldToRegularFifoWithinQuota() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        for (int i = 0; i < 256; i++) assertTrue(queue.offerUrgent(new BlockPos(64, i, 0)));
        BlockPos ordinary = new BlockPos(0, 64, 0);
        assertTrue(queue.offer(ordinary));

        for (int i = 0; i < 8; i++) {
            BlockPos urgent = queue.poll();
            assertTrue(queue.offerUrgent(urgent));
        }
        assertEquals(ordinary, queue.poll(), "urgent requeues must not starve ordinary work");
        assertEquals(0, queue.pending.size());
        assertEquals(256, queue.queued.size());
    }

    @Test
    void coalescesOneHundredThousandOverflowsIntoTwoDirtyChunks() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        int capacity = AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY;
        for (int i = 0; i < 100_000; i++) {
            int chunkX = i % 2 == 0 ? 0 : 1;
            queue.offer(new BlockPos(chunkX * 16, i, 0));
        }

        assertEquals(capacity, queue.pending.size());
        assertEquals(capacity, queue.queued.size());
        assertEquals(2, queue.dirtyChunks.size());
        assertFalse(queue.offer(new BlockPos(0, 100_000, 0)));
        assertEquals(2, queue.dirtyChunks.size());
    }

    @Test
    void dirtyChunkTraversalIsFairAndRetainsRepresentativeSeed() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        for (int i = 0; i < AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY; i++)
            queue.offer(new BlockPos(i, 64, 0));
        BlockPos first = new BlockPos(0, 90_000, 0);
        BlockPos second = new BlockPos(16, 90_001, 0);
        queue.offer(first);
        queue.offer(second);

        var one = queue.nextDirtyChunk();
        var two = queue.nextDirtyChunk();
        assertNotNull(one);
        assertNotNull(two);
        assertNotEquals(one.chunk(), two.chunk());
        assertTrue(java.util.Set.of(first, second).contains(one.seed()));
        assertTrue(java.util.Set.of(first, second).contains(two.seed()));
    }

    @Test
    void cursorFindsBothMeaningfulGradientsAndResumesAfterMutation() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture gradientA = ambient.withGasDelta(GasType.OXYGEN, 1.0);
        GasMixture gradientB = ambient.withGasDelta(GasType.NITROGEN, 1.0);
        data.put(1, -64, 1, gradientA, ambient);
        data.put(1, 320, 1, gradientB, ambient);

        var first = data.nextAfter(null).orElseThrow();
        assertEquals(-64, first.getKey().y());
        // An insertion after the cursor is visible during this pass without retaining an iterator.
        data.put(1, 0, 1, gradientA, ambient);
        var second = data.nextAfter(first.getKey()).orElseThrow();
        var third = data.nextAfter(second.getKey()).orElseThrow();
        assertEquals(0, second.getKey().y());
        assertEquals(320, third.getKey().y());
        assertEquals(gradientA, data.get(1, -64, 1));
        assertEquals(gradientB, data.get(1, 320, 1));
        assertTrue(data.nextAfter(third.getKey()).isEmpty());

        // A newly inserted earlier coordinate is intentionally picked up by the next full pass.
        data.put(0, -100, 0, gradientB, ambient);
        assertEquals(0, data.nextAfter(null).orElseThrow().getKey().x());
    }

    @Test
    void mutationsDoNotRestartMonotonicPassAndDirtyChunksStayRoundRobin() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        AtmosphereChunkData data = new AtmosphereChunkData();
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture gradient = ambient.withGasDelta(GasType.OXYGEN, 1.0);
        for (int y = 0; y < 20; y++) data.put(0, y, 0, gradient, ambient);

        BlockPos firstSeed = new BlockPos(0, 0, 0);
        BlockPos secondSeed = new BlockPos(16, 0, 0);
        queue.markDirtyChunk(new net.minecraft.world.level.ChunkPos(0, 0), firstSeed);
        queue.markDirtyChunk(new net.minecraft.world.level.ChunkPos(1, 0), secondSeed);
        AtmosphereService.WorkQueue.DirtyChunk first = queue.dirtyChunks.get(new net.minecraft.world.level.ChunkPos(0, 0));

        // Each dirty chunk gets a turn in order; a write on every turn must not rewind the cursor.
        assertEquals(new net.minecraft.world.level.ChunkPos(0, 0), queue.nextDirtyChunk().chunk());
        assertEquals(new net.minecraft.world.level.ChunkPos(1, 0), queue.nextDirtyChunk().chunk());
        int visited = 0;
        boolean restartedAtEnd = false;
        while (!restartedAtEnd) {
            AtmosphereService.WorkQueue.DirtyChunk dirty = queue.nextDirtyChunk();
            if (dirty.chunk().x == 0) {
                var next = data.nextAfter(dirty.cursor());
                if (next.isEmpty()) {
                    queue.finishDirty(dirty);
                    restartedAtEnd = dirty.cursor() == null;
                    continue;
                }
                dirty.advance(next.get().getKey());
                visited++;
                queue.noteChunkMutation(new BlockPos(0, next.get().getKey().y(), 0));
            } else {
                queue.noteChunkMutation(new BlockPos(16, visited, 0));
            }
        }

        assertEquals(20, visited);
        assertNull(first.cursor());
        assertEquals(first.version(), first.passVersion());
        assertTrue(queue.dirtyChunks.containsKey(first.chunk()));
        assertTrue(queue.dirtyChunks.containsKey(new net.minecraft.world.level.ChunkPos(1, 0)));
    }
}
