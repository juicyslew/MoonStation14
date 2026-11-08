package com.juicyslew.moonstation14.ms14.power.device;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Explicit support handling for trusted fixture builders; player placement remains block-owned. */
public final class PowerDeviceFixturePlacement {
    private PowerDeviceFixturePlacement() { }

    /**
     * Ensures a fixture APC has a sturdy back wall without replacing existing terrain,
     * devices, floors, or hosts. An air-only fixture cell may be filled with stone.
     */
    public static boolean ensureApcSupport(ServerLevel level, BlockPos apcPos, Direction facing) {
        if (level == null || facing == null || !facing.getAxis().isHorizontal()) return false;
        BlockPos supportPos = apcPos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        if (support.isAir()) {
            if (hasCableRecords(level, supportPos)) return false;
            level.setBlock(supportPos, Blocks.STONE.defaultBlockState(), 3);
            support = level.getBlockState(supportPos);
        }
        return support.isFaceSturdy(level, supportPos, facing);
    }

    public static boolean hasApcSupport(ServerLevel level, BlockPos apcPos, Direction facing) {
        if (level == null || facing == null || !facing.getAxis().isHorizontal()) return false;
        BlockPos supportPos = apcPos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    private static boolean hasCableRecords(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk loaded)) return true;
        CableChunkData data = loaded.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        return data != null && data.snapshot().stream().anyMatch(record -> record.x() == (pos.getX() & 15)
                && record.y() == pos.getY() && record.z() == (pos.getZ() & 15));
    }

    /** Sets an APC only after verifying its backing support; does not touch ports/facing. */
    public static boolean placeApc(ServerLevel level, BlockPos apcPos, Direction facing) {
        if (!ensureApcSupport(level, apcPos, facing)) return false;
        level.setBlock(apcPos, ModBlocks.APC.get().defaultBlockState()
                .setValue(PowerDeviceBlock.FACING, facing), 3);
        return level.getBlockState(apcPos).is(ModBlocks.APC.get())
                && level.getBlockState(apcPos).getValue(PowerDeviceBlock.FACING) == facing;
    }
}

