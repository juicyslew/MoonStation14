package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlipSpatialGateTest {
    @Test
    void intersectionUsesVirtualSs14TriggerAndMaximumNormalizedOverlap() {
        BlockPos blockPos = BlockPos.ZERO;
        AABB puddleShape = new AABB(.0625, 0, .0625, .9375, .25, .9375);
        assertEquals(1d, SlipSystem.intersectionRatio(new AABB(.2, 0, .2, .8, 2, .8),
                puddleShape, blockPos), 1e-9);
        assertEquals(0d, SlipSystem.intersectionRatio(new AABB(1, 0, 0, 2, .25, 1),
                puddleShape, blockPos), 1e-9);
        assertEquals(0d, SlipSystem.intersectionRatio(new AABB(.2, .3, .2, .8, 2, .8),
                puddleShape, blockPos), 1e-9);
    }

    @Test
    void glancingOverlapFallsBelowThresholdAndSourceAreaRatioCanDominate() {
        BlockPos blockPos = BlockPos.ZERO;
        AABB puddleShape = new AABB(.0625, 0, .0625, .9375, .25, .9375);
        double glancingRatio = SlipSystem.intersectionRatio(
                new AABB(.85, 0, .2, 1.2, 2, .8), puddleShape, blockPos);
        assertTrue(glancingRatio < .3d, "glancing contact must remain below the pinned 0.3 gate");

        // The large target overlaps 0.24 area: 0.25 of its own area, but 0.375
        // of the virtual source area. SS14 takes the larger normalized ratio.
        assertEquals(.375d, SlipSystem.intersectionRatio(
                new AABB(.5, 0, .2, 2.1, 2, .8), puddleShape, blockPos), 1e-9);
    }
}
