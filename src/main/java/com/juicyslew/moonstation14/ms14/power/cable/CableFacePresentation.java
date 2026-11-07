package com.juicyslew.moonstation14.ms14.power.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared cable-host visibility rule used by rendering and authoritative cutting. */
public final class CableFacePresentation {
    private CableFacePresentation() { }

    public static boolean isVisible(Direction face, boolean stationFloor, boolean tileFinish) {
        return !(stationFloor && tileFinish && face == Direction.UP);
    }

    public static boolean isVisible(Level level, BlockPos pos, Direction face) {
        if (level == null || pos == null || face == null || !level.hasChunkAt(pos)) return false;
        BlockState host = level.getBlockState(pos);
        if (host.isAir() || host.getCollisionShape(level, pos).isEmpty()
                || !host.isFaceSturdy(level, pos, face)) return false;
        boolean floor = host.getBlock() instanceof com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
        boolean tileFinish = floor && host.getValue(
                com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock.TILE_FINISH)
                != com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock.TileFinish.NONE;
        return isVisible(face, floor, tileFinish);
    }
}
