package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereTopologyTest {
    @Test
    void stateOnlyApproximationHandlesSimpleStaticFixtures() {
        assertTrue(AtmosphereTopology.isPassable(Blocks.AIR.defaultBlockState()));
        assertFalse(AtmosphereTopology.isPassable(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void oakDoorOpenAndClosedStatesHaveExplicitPolicy() {
        assertTrue(isPassable(openState(Blocks.OAK_DOOR.defaultBlockState(), DoorBlock.OPEN), (state, level, pos) -> failCollisionCheck()));
        assertFalse(isPassable(Blocks.OAK_DOOR.defaultBlockState(), (state, level, pos) -> failCollisionCheck()));
    }

    @Test
    void oakTrapdoorOpenAndClosedStatesHaveExplicitPolicy() {
        assertTrue(isPassable(openState(Blocks.OAK_TRAPDOOR.defaultBlockState(), TrapDoorBlock.OPEN), (state, level, pos) -> failCollisionCheck()));
        assertFalse(isPassable(Blocks.OAK_TRAPDOOR.defaultBlockState(), (state, level, pos) -> failCollisionCheck()));
    }

    @Test
    void oakFenceGateOpenAndClosedStatesHaveExplicitPolicy() {
        assertTrue(isPassable(openState(Blocks.OAK_FENCE_GATE.defaultBlockState(), FenceGateBlock.OPEN), (state, level, pos) -> failCollisionCheck()));
        assertFalse(isPassable(Blocks.OAK_FENCE_GATE.defaultBlockState(), (state, level, pos) -> failCollisionCheck()));
    }

    @Test
    void collisionFallbackReceivesTheLevelAndPositionProvidedToItsHelper() {
        LevelReader level = (LevelReader) Proxy.newProxyInstance(
                LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class},
                (proxy, method, arguments) -> null);
        BlockPos pos = new BlockPos(4, 5, 6);
        BlockState state = Blocks.STONE.defaultBlockState();

        assertFalse(AtmosphereTopology.isPassable(state, level, pos, (checkedState, checkedLevel, checkedPos) -> {
            assertSame(state, checkedState);
            assertSame(level, checkedLevel);
            assertSame(pos, checkedPos);
            return true;
        }));
    }

    private static boolean isPassable(BlockState state, AtmosphereTopology.FullCollisionCheck check) {
        return AtmosphereTopology.isPassable(state, null, BlockPos.ZERO, check);
    }

    private static boolean failCollisionCheck() {
        throw new AssertionError("Openable block policy should not query collision shapes");
    }

    private static BlockState openState(BlockState state, net.minecraft.world.level.block.state.properties.BooleanProperty openProperty) {
        return state.setValue(openProperty, true);
    }
}
