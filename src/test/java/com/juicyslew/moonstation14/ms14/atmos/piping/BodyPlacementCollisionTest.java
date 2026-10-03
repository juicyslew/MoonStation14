package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyPlacementCollisionTest {
    @Test
    void onlyPositiveVolumeOverlapBlocksPlacement() {
        BlockPos pos = new BlockPos(10, 20, 30);
        var arm = Shapes.box(0, .375, .375, .375, .625, .625);
        assertTrue(BodyPlacementCollision.intersects(arm, pos,
                new AABB(10.1, 20.4, 30.4, 10.2, 20.5, 30.5)));
        assertFalse(BodyPlacementCollision.intersects(arm, pos,
                new AABB(10.375, 20.4, 30.4, 10.5, 20.5, 30.5)));
        assertFalse(BodyPlacementCollision.intersects(arm, pos,
                new AABB(10.1, 20.625, 30.4, 10.2, 20.8, 30.5)));
        assertFalse(BodyPlacementCollision.intersects(arm, pos,
                new AABB(10.1, 20.4, 30.625, 10.2, 20.5, 30.8)));
    }
}
