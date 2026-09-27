package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereActivationTest {
    @Test
    void cheapActivationAddsOnlyLocalSevenCellStencil() {
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        BlockPos origin = new BlockPos(4, 20, 9);

        AtmosphereService.enqueueLocalStencil(queue, origin);

        assertEquals(7, queue.queued.size());
        assertTrue(queue.queued.contains(origin));
        assertTrue(queue.queued.contains(origin.above()));
        assertTrue(queue.queued.contains(origin.below()));
    }

    @Test
    void topologyColumnSelectionIgnoresOtherLocalXzOverrides() {
        BlockPos changed = new BlockPos(35, 64, -18);

        assertTrue(AtmosphereService.isSameVerticalColumn(new AtmosphereChunkData.CellPosition(3, 10, 14), changed));
        assertFalse(AtmosphereService.isSameVerticalColumn(new AtmosphereChunkData.CellPosition(4, 10, 14), changed));
        assertFalse(AtmosphereService.isSameVerticalColumn(new AtmosphereChunkData.CellPosition(3, 10, 13), changed));
    }
}
