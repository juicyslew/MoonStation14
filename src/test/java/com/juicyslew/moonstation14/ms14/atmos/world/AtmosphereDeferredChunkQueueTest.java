package com.juicyslew.moonstation14.ms14.atmos.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereDeferredChunkQueueTest {
    @Test
    void deduplicatesCoordinatesAndAllowsRetryAfterPoll() {
        AtmosphereService.DeferredChunkQueue queue = new AtmosphereService.DeferredChunkQueue();
        ChunkPos pos = new ChunkPos(-2, 7);

        assertTrue(queue.offer(pos));
        assertFalse(queue.offer(new ChunkPos(-2, 7)));
        assertEquals(1, queue.size());
        assertEquals(pos, queue.poll());
        assertTrue(queue.offer(pos));
    }

    @Test
    void drainCanBeBoundedAndNotYetFullChunkCanBeRetriedAtTheTail() {
        AtmosphereService.DeferredChunkQueue queue = new AtmosphereService.DeferredChunkQueue();
        ChunkPos notYetFull = new ChunkPos(0, 0);
        ChunkPos ready = new ChunkPos(1, 0);
        ChunkPos later = new ChunkPos(2, 0);
        queue.offer(notYetFull);
        queue.offer(ready);
        queue.offer(later);

        List<ChunkPos> attempted = new ArrayList<>();
        assertEquals(2, queue.drain(2, pos -> {
            attempted.add(pos);
            return !pos.equals(notYetFull);
        }));
        assertEquals(List.of(notYetFull, ready), attempted);
        assertEquals(2, queue.size());

        attempted.clear();
        assertEquals(2, queue.drain(2, pos -> {
            attempted.add(pos);
            return true;
        }));
        assertEquals(List.of(later, notYetFull), attempted);
        assertTrue(queue.isEmpty());
    }

    @Test
    void disabledServiceDoesNotCreateDeferredWork() {
        AtmosphereService service = AtmosphereService.INSTANCE;
        service.onServerStopped();
        service.deferChunkLoad(null, new ChunkPos(0, 0));
        service.processDeferredChunkLoads(null);
        assertEquals(0, service.deferredChunkCountForTesting());
    }
}
