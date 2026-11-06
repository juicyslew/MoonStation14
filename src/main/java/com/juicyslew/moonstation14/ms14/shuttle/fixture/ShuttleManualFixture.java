package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.UUID;

/** Manual boarding-friendly variant; automated canonical cabin geometry remains unchanged. */
public final class ShuttleManualFixture {
    public static final int WIDTH = 20, HEIGHT = 7, DEPTH = 13;
    private ShuttleManualFixture() { }

    public static ShuttleCabinFixture.Built build(ServerLevel level, BlockPos origin, UUID engineId,
                                                   ShuttleCabinFixture.HullWriter writer) {
        ShuttleCabinFixture.preflight(level, origin, WIDTH, HEIGHT, DEPTH);
        ShuttleCabinFixture.Built cabin = ShuttleCabinFixture.build(level, origin, engineId);
        writeBay(origin, writer, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState());
        return cabin;
    }

    static void writeBay(BlockPos origin, ShuttleCabinFixture.HullWriter writer, BlockState hull) {
        for (int x = 8; x < WIDTH; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) {
            if (!isBayHullCell(x, y, z)) continue;
            BlockPos pos = origin.offset(x, y, z);
            BlockState state = x == 14 && y == 6 && z == 6 ? Blocks.SEA_LANTERN.defaultBlockState() : hull;
            if (!writer.set(pos, state))
                throw new IllegalStateException("manual shuttle bay hull write failed at " + pos);
        }
    }

    /** Safely retrofit the authored manual bay ceiling on an already-live Sable frame. */
    public static boolean ensureBayLight(ServerLevel level, ServerSubLevel sub) {
        if (level == null || sub == null || level.isClientSide || !level.getServer().isSameThread()
                || sub.isRemoved() || sub.getLevel() != level
                || SubLevelContainer.getContainer(level).getSubLevel(sub.getUniqueId()) != sub) return false;
        var plot = sub.getPlot();
        if (plot == null) return false;
        BlockPos storage = plot.getCenterBlock();
        BlockPos target = storage.offset(14, 6, 6);
        BlockPos left = target.west(), right = target.east();
        if (!plot.contains(new net.minecraft.world.level.ChunkPos(target))
                || !plot.contains(new net.minecraft.world.level.ChunkPos(left))
                || !plot.contains(new net.minecraft.world.level.ChunkPos(right))) return false;
        if (!loaded(level, target) || !loaded(level, left) || !loaded(level, right)) return false;
        BlockState state = level.getBlockState(target);
        if (state.is(Blocks.SEA_LANTERN)) return true;
        if (!state.is(ModBlocks.STEEL_WALL_BLOCK.get())
                || !level.getBlockState(left).is(ModBlocks.STEEL_WALL_BLOCK.get())
                || !level.getBlockState(right).is(ModBlocks.STEEL_WALL_BLOCK.get())) return false;
        return level.setBlock(target, Blocks.SEA_LANTERN.defaultBlockState(), 3);
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) instanceof LevelChunk;
    }

    static boolean isBayHullCell(int x, int y, int z) {
        if (x < 8 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return false;
        boolean shell = x == 8 || x == WIDTH - 1 || y == 0 || y == HEIGHT - 1 || z == 0 || z == DEPTH - 1;
        return shell && !(z == 0 && x >= 12 && x <= 14 && y >= 1 && y <= 3);
    }
}
