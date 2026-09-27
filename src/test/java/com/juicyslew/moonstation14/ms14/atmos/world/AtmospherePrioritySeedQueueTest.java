package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AtmospherePrioritySeedQueueTest {
    @Test
    void newMutationSeedPrecedesAnOldFifoBacklog() {
        AtmosphereService.WorkQueue oldWork = new AtmosphereService.WorkQueue();
        for (int i = 0; i < 8192; i++) assertTrue(oldWork.offer(new BlockPos(i, 64, 0)));
        BlockPos pumpCell = new BlockPos(4, 64, 4);
        AtmosphereService.PrioritySeedQueue priority = new AtmosphereService.PrioritySeedQueue();
        assertTrue(priority.offer(pumpCell));

        assertEquals(pumpCell, AtmosphereService.selectNextSeed(priority, oldWork, 0).seed());
        assertEquals(new BlockPos(0, 64, 0), oldWork.poll());
    }

    @Test
    void deduplicatesUrgentSeedsAndPeriodicallyRotatesToFifo() {
        AtmosphereService.PrioritySeedQueue priority = new AtmosphereService.PrioritySeedQueue();
        AtmosphereService.WorkQueue oldWork = new AtmosphereService.WorkQueue();
        BlockPos seed = new BlockPos(1, 64, 1);
        assertTrue(priority.offer(seed));
        assertFalse(priority.offer(seed));
        for (int i = 0; i < 8; i++) priority.offer(new BlockPos(20 + i, 64, 1));
        BlockPos old = new BlockPos(100, 64, 0);
        oldWork.offer(old);

        int streak = 0;
        for (int i = 0; i < 4; i++) {
            AtmosphereService.SeedSelection selected = AtmosphereService.selectNextSeed(priority, oldWork, streak);
            assertNotNull(selected.seed());
            assertNotEquals(old, selected.seed());
            streak = selected.priorityStreak();
        }
        assertEquals(old, AtmosphereService.selectNextSeed(priority, oldWork, streak).seed());
    }

    @Test
    void chunkOverflowIsPromotedAndEventuallySelectedAlongsideNewWrites() {
        AtmosphereService.PrioritySeedQueue priority = new AtmosphereService.PrioritySeedQueue();
        Set<BlockPos> overflow = new HashSet<>();
        for (int i = 0; i < AtmosphereService.PRIORITY_SEED_CAPACITY + 3; i++) {
            BlockPos pos = new BlockPos(i * 16, 64, 0);
            priority.offer(pos);
            if (i >= AtmosphereService.PRIORITY_SEED_CAPACITY) overflow.add(pos);
        }

        assertEquals(3, priority.overflowChunkCount());
        Set<BlockPos> selected = new HashSet<>();
        for (int i = 0; i < AtmosphereService.PRIORITY_SEED_CAPACITY + 4; i++) {
            if (i == AtmosphereService.PRIORITY_SEED_CAPACITY) {
                BlockPos laterWrite = new BlockPos(100_000, 64, 0);
                assertTrue(priority.offer(laterWrite));
                overflow.add(laterWrite);
            }
            BlockPos seed = priority.poll();
            assertNotNull(seed);
            selected.add(seed);
        }
        assertTrue(selected.containsAll(overflow));
        assertTrue(priority.isEmpty());
    }

    @Test
    void unloadedRepresentativeWaitsWithoutSpinningAndReturnsAfterReload() {
        AtomicBoolean loaded = new AtomicBoolean(true);
        BlockPos seed = new BlockPos(48, 64, 0);
        AtmosphereService.PrioritySeedQueue priority = new AtmosphereService.PrioritySeedQueue(ignored -> loaded.get());
        assertTrue(priority.offer(seed));

        loaded.set(false);
        assertFalse(priority.hasPending());
        assertNull(priority.poll());
        assertEquals(1, priority.overflowChunkCount());

        loaded.set(true);
        assertTrue(priority.hasPending());
        assertEquals(seed, priority.poll());
    }

    @Test
    void unloadLifecycleCleanupCanBeReactivatedFromLoadedChunkState() {
        AtmosphereService.PrioritySeedQueue priority = new AtmosphereService.PrioritySeedQueue();
        BlockPos persistedMutation = new BlockPos(64, 64, 0);
        priority.offer(persistedMutation);
        priority.removeChunk(new ChunkPos(persistedMutation));
        assertTrue(priority.isEmpty());

        // Chunk load re-seeds from persisted atmosphere data in AtmosphereService.onChunkLoad.
        assertTrue(priority.offer(persistedMutation));
        assertEquals(persistedMutation, priority.poll());
    }
}
