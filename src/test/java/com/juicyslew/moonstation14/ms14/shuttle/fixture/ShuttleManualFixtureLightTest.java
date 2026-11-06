package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShuttleManualFixtureLightTest {
    @Test
    void authorsSingleFlushEmissiveCeilingBlockWithoutChangingHullGeometry() {
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> written = new HashMap<>();
        BlockPos origin = BlockPos.ZERO;
        ShuttleManualFixture.writeBay(origin, (pos, state) -> written.put(pos.immutable(), state) == null,
                com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
        BlockPos lamp = origin.offset(14, 6, 6);
        assertEquals(Blocks.SEA_LANTERN, written.get(lamp).getBlock());
        assertEquals(15, written.get(lamp).getLightEmission());
        assertEquals(533, written.size());
        assertFalse(ShuttleManualFixture.isBayHullCell(13, 2, 0), "doorway remains open");
        assertFalse(written.containsKey(origin.offset(14, 5, 6)), "interior headroom remains clear");
    }
}
