package com.juicyslew.moonstation14.ms14.explosion.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;

/** Shared source-position validation without chunk loads; an occupied source may move one face step. */
public final class ExplosionSourceAdmission {
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private ExplosionSourceAdmission() { }
    public static BlockPos resolveOrigin(ServerLevel level, BlockPos position) {
        if (level == null || level.isClientSide() || position == null) return null;
        if (position.getY() < level.getMinBuildHeight() || position.getY() >= level.getMaxBuildHeight()) return null;
        var state = stateIfLoaded(level, position);
        if (state == null) return null;
        if (isOpen(state)) return position.immutable();
        for (int[] face : FACES) {
            BlockPos candidate = position.offset(face[0], face[1], face[2]);
            if (candidate.getY() < level.getMinBuildHeight() || candidate.getY() >= level.getMaxBuildHeight()) continue;
            var adjacent = stateIfLoaded(level, candidate);
            if (adjacent != null && isOpen(adjacent)) return candidate.immutable();
        }
        return null;
    }
    private static net.minecraft.world.level.block.state.BlockState stateIfLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }
    private static boolean isOpen(net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir() && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0;
    }
}
