package com.juicyslew.moonstation14.util;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

public class Helpers {
    public static BlockPos findFirstSurfaceBelow(Level level, BlockPos startPos, int maxDistance, Function<BlockState, Boolean> stopCondition, Function<BlockState, Boolean> backOneCondition) {
        BlockPos.MutableBlockPos mutablePos = startPos.mutable();

        for (int i = 0; i < maxDistance; i++) {
            // Move down one block
            mutablePos.move(Direction.DOWN);

            // If we hit the bottom of the world, stop
            if (level.isOutsideBuildHeight(mutablePos)) return null;

            BlockState state = level.getBlockState(mutablePos);

            // Check if this is a "Surface" (not replaceable AND not a puddle)
            // OR if it's already a puddle (which we can merge into)
            if (stopCondition.apply(state)) {
                return mutablePos.immutable(); // Found a puddle, return its pos to merge
            } else if (backOneCondition.apply(state)) {
                return mutablePos.above().immutable(); // Found solid ground, return air above it
            }
        }

        return null; // Hit max distance without finding floor
    }
}
