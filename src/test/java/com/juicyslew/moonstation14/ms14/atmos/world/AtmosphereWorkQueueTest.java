package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereWorkQueueTest {
    @Test
    void deduplicatesPositionsAndRemovesDeduplicationOnPoll() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        BlockPos pos = new BlockPos(2, 64, -4);
        assertTrue(queue.offer(pos));
        assertFalse(queue.offer(pos));
        assertEquals(pos, queue.poll());
        assertTrue(queue.offer(pos));
    }

    @Test
    void overflowIsRetainedInDeterministicFifoOrder() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        int total = AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY + 3;
        for (int i = 0; i < total; i++) assertTrue(queue.offer(new BlockPos(i, 64, 0)));
        assertEquals(total, queue.queued.size());
        assertEquals(3, queue.overflow.size());

        for (int i = 0; i < total; i++) assertEquals(new BlockPos(i, 64, 0), queue.poll());
        assertTrue(queue.isEmpty());
        assertTrue(queue.offer(new BlockPos(total, 64, 0)));
        assertEquals(new BlockPos(total, 64, 0), queue.poll());
    }

    @Test
    void overflowDeduplicatesAndCanAcceptNewWorkWhileDraining() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        int capacity = AtmosphereService.WorkQueue.HOT_QUEUE_CAPACITY;
        for (int i = 0; i < capacity + 1; i++) queue.offer(new BlockPos(i, 64, 0));
        assertFalse(queue.offer(new BlockPos(capacity, 64, 0)));

        for (int i = 0; i < capacity; i++) assertEquals(new BlockPos(i, 64, 0), queue.poll());
        assertEquals(new BlockPos(capacity, 64, 0), queue.poll());
        assertTrue(queue.offer(new BlockPos(capacity + 1, 64, 0)));
        assertEquals(new BlockPos(capacity + 1, 64, 0), queue.poll());
        assertTrue(queue.isEmpty());
    }
}
