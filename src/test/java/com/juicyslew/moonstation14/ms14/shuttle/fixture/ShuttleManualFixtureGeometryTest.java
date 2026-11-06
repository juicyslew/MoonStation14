package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShuttleManualFixtureGeometryTest {
    @Test
    void sharedBayWriterProducesOnlyAnnexShellAndFullJumpClearance() {
        BlockPos origin = BlockPos.ZERO;
        Set<BlockPos> written = new HashSet<>();
        ShuttleManualFixture.writeBay(origin, (pos, state) -> {
            assertTrue(state.is(Blocks.STONE));
            return written.add(pos.immutable());
        }, Blocks.STONE.defaultBlockState());

        for (int x = 0; x < ShuttleManualFixture.WIDTH; x++)
            for (int y = 0; y < ShuttleManualFixture.HEIGHT; y++)
                for (int z = 0; z < ShuttleManualFixture.DEPTH; z++)
                    assertEquals(ShuttleManualFixture.isBayHullCell(x, y, z), written.contains(origin.offset(x, y, z)),
                            "live annex writer geometry at " + x + "," + y + "," + z);
        assertTrue(written.stream().allMatch(p -> p.getX() >= 8 && p.getX() <= 19
                && p.getY() >= 0 && p.getY() <= 6 && p.getZ() >= 0 && p.getZ() <= 12));
        assertTrue(written.contains(origin.offset(8, 0, 0)) && written.contains(origin.offset(19, 6, 12)));
        for (int x = 12; x <= 14; x++) for (int y = 1; y <= 3; y++)
            assertFalse(written.contains(origin.offset(x, y, 0)), "boarding opening remains clear");
        for (int x = 9; x <= 18; x++) for (int z = 1; z <= 11; z++) {
            assertTrue(written.contains(origin.offset(x, 0, z)), "bay floor");
            for (int y = 1; y <= 5; y++) assertFalse(written.contains(origin.offset(x, y, z)), "clear bay interior");
            assertTrue(written.contains(origin.offset(x, 6, z)), "bay roof");
        }
        double jumpApex = GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND
                * GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND
                / (2 * GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED);
        assertTrue(1.8 + jumpApex < 5, "5 free blocks clear the existing jump arc plus standing height");
    }
}
