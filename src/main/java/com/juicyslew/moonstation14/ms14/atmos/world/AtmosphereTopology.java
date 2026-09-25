package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Conservative cell topology: air and open doors, trapdoors, and fence gates are passable;
 * full-collision blocks and closed doors, trapdoors, and gates are sealed. Other partial shapes
 * are not assumed airtight. Openable blocks use a one-cell approximation, not face-specific
 * geometry: an open block passes gas regardless of its orientation or remaining collision shape.
 */
public final class AtmosphereTopology {
    private AtmosphereTopology() { }

    public static boolean isPassable(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isPassable(state, level, pos, (blockState, reader, blockPos) ->
                blockState.isCollisionShapeFullBlock(reader, blockPos));
    }

    /**
     * State-only approximation for simple static fixtures. Do not use for general world
     * topology: dynamic collision shapes require the actual {@link LevelReader} and position.
     */
    public static boolean isPassable(BlockState state) {
        return isPassable(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                (blockState, reader, blockPos) -> blockState.isCollisionShapeFullBlock(reader, blockPos));
    }

    /**
     * Applies the shared state policy and delegates ordinary collision checks with their world
     * context. Package visibility keeps the collision-check seam testable without fabricating a
     * LevelReader implementation.
     */
    static boolean isPassable(BlockState state, BlockGetter level, BlockPos pos,
                              FullCollisionCheck fullCollisionCheck) {
        if (state.isAir()) return true;

        if (state.getBlock() instanceof DoorBlock) return state.getValue(DoorBlock.OPEN);
        if (state.getBlock() instanceof TrapDoorBlock) return state.getValue(TrapDoorBlock.OPEN);
        if (state.getBlock() instanceof FenceGateBlock) return state.getValue(FenceGateBlock.OPEN);

        return !fullCollisionCheck.isFull(state, level, pos);
    }

    @FunctionalInterface
    interface FullCollisionCheck {
        boolean isFull(BlockState state, BlockGetter level, BlockPos pos);
    }

    public static boolean canExchange(LevelReader level, BlockPos first, BlockPos second) {
        return first.distManhattan(second) == 1 && isPassable(level, first) && isPassable(level, second);
    }
}
