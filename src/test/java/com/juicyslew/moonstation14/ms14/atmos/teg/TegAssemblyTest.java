package com.juicyslew.moonstation14.ms14.atmos.teg;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TegAssemblyTest {
    private static final BlockPos CORE = new BlockPos(10, 64, -3);

    @Test void northFacingPlacesCirculatorsOnFrontAndBackWithClockwiseRotations() {
        assertEquals(new TegAssembly.Layout(new BlockPos(10, 64, -4), Direction.EAST,
                new BlockPos(10, 64, -2), Direction.WEST), TegAssembly.layout(CORE, Direction.NORTH));
    }

    @Test void eastFacingPlacesCirculatorsOnFrontAndBackWithClockwiseRotations() {
        assertEquals(new TegAssembly.Layout(new BlockPos(11, 64, -3), Direction.SOUTH,
                new BlockPos(9, 64, -3), Direction.NORTH), TegAssembly.layout(CORE, Direction.EAST));
    }

    @Test void southFacingPlacesCirculatorsOnFrontAndBackWithClockwiseRotations() {
        assertEquals(new TegAssembly.Layout(new BlockPos(10, 64, -2), Direction.WEST,
                new BlockPos(10, 64, -4), Direction.EAST), TegAssembly.layout(CORE, Direction.SOUTH));
    }

    @Test void westFacingPlacesCirculatorsOnFrontAndBackWithClockwiseRotations() {
        assertEquals(new TegAssembly.Layout(new BlockPos(9, 64, -3), Direction.NORTH,
                new BlockPos(11, 64, -3), Direction.SOUTH), TegAssembly.layout(CORE, Direction.WEST));
    }

    @Test void rejectsVerticalOrientation() {
        assertThrows(IllegalArgumentException.class, () -> TegAssembly.layout(CORE, Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> TegAssembly.layout(CORE, Direction.DOWN));
    }
}
