package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/** Bounded, no-force-load block access shared by authoritative body queries. */
public final class LoadedBlockGetter implements BlockGetter {
    public static final class UnknownChunk extends RuntimeException { }

    private final net.minecraft.world.level.Level level;
    @FunctionalInterface private interface ChunkRead { @Nullable LevelChunk get(int x, int z); }
    private final ChunkRead chunkRead;
    private final int minChunkX;
    private final int maxChunkX;
    private final int minChunkZ;
    private final int maxChunkZ;
    private boolean requireFullChunks;
    @Nullable private final FrameReadDomain frameDomain;

    private record FrameReadDomain(dev.ryanhcode.sable.sublevel.plot.LevelPlot plot,
                                   AABB storageBounds, int maxChunks, Set<Long> touchedChunks) { }

    public LoadedBlockGetter(ServerLevel level, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        this.level = level;
        this.chunkRead = (x, z) -> level.getChunkSource().getChunkNow(x, z);
        this.minChunkX = minChunkX;
        this.maxChunkX = maxChunkX;
        this.minChunkZ = minChunkZ;
        this.maxChunkZ = maxChunkZ;
        this.frameDomain = null;
    }

    private LoadedBlockGetter(ServerLevel level, dev.ryanhcode.sable.sublevel.plot.LevelPlot plot,
                              AABB storageBounds, int maxChunks) {
        this.level = level;
        this.chunkRead = (x, z) -> level.getChunkSource().getChunkNow(x, z);
        this.minChunkX = Integer.MIN_VALUE;
        this.maxChunkX = Integer.MAX_VALUE;
        this.minChunkZ = Integer.MIN_VALUE;
        this.maxChunkZ = Integer.MAX_VALUE;
        this.frameDomain = new FrameReadDomain(plot, storageBounds, maxChunks, new HashSet<>());
    }

    private LoadedBlockGetter(net.minecraft.world.level.Level level, int minX, int maxX, int minZ, int maxZ,
                              dev.ryanhcode.sable.sublevel.plot.LevelPlot plot, AABB bounds, int maxChunks) {
        this.level = level; this.minChunkX = minX; this.maxChunkX = maxX; this.minChunkZ = minZ; this.maxChunkZ = maxZ;
        this.frameDomain = plot == null ? null : new FrameReadDomain(plot, bounds, maxChunks, new HashSet<>());
        this.chunkRead = plot == null
                ? (x,z) -> level.getChunkSource().getChunk(x,z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false)
                    instanceof LevelChunk chunk ? chunk : null
                : (x,z) -> plot.getChunk(plot.toLocal(new ChunkPos(x,z)));
    }

    public static LoadedBlockGetter clientAlongWithChunkMargin(net.minecraft.world.level.Level level, Vec3 from, Vec3 to,
            double margin, int maxChunks, int chunkMargin) {
        int minX=(BlockPos.containing(Math.min(from.x,to.x)-margin,from.y,from.z).getX()>>4)-chunkMargin;
        int maxX=(BlockPos.containing(Math.max(from.x,to.x)+margin,from.y,from.z).getX()>>4)+chunkMargin;
        int minZ=(BlockPos.containing(from.x,from.y,Math.min(from.z,to.z)-margin).getZ()>>4)-chunkMargin;
        int maxZ=(BlockPos.containing(from.x,from.y,Math.max(from.z,to.z)+margin).getZ()>>4)+chunkMargin;
        if ((long)(maxX-minX+1)*(maxZ-minZ+1)>maxChunks) throw new UnknownChunk();
        return new LoadedBlockGetter(level,minX,maxX,minZ,maxZ,null,null,maxChunks);
    }
    public static LoadedBlockGetter clientForFrame(net.minecraft.world.level.Level level,
            dev.ryanhcode.sable.sublevel.plot.LevelPlot plot, AABB bounds, int maxChunks) {
        return new LoadedBlockGetter(level,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,plot,bounds,maxChunks);
    }

    /** Lazy storage access: unowned/out-of-domain positions are known empty; missing owned chunks are unknown. */
    public static LoadedBlockGetter forFrame(ServerLevel level,
                                              dev.ryanhcode.sable.sublevel.plot.LevelPlot plot,
                                              AABB storageBounds, int maxChunks) {
        return new LoadedBlockGetter(level, plot, storageBounds, maxChunks);
    }

    public static LoadedBlockGetter forFullFrame(ServerLevel level,
            dev.ryanhcode.sable.sublevel.plot.LevelPlot plot, AABB storageBounds, int maxChunks) {
        LoadedBlockGetter getter = new LoadedBlockGetter(level, plot, storageBounds, maxChunks);
        getter.requireFullChunks = true;
        return getter;
    }


    public static LoadedBlockGetter along(ServerLevel level, Vec3 from, Vec3 to, double margin, int maxChunks) {
        return along(level, from, to, margin, maxChunks, 0);
    }

    public static LoadedBlockGetter alongWithChunkMargin(ServerLevel level, Vec3 from, Vec3 to,
                                                          double margin, int maxChunks, int chunkMargin) {
        return along(level, from, to, margin, maxChunks, chunkMargin);
    }

    /** Bounded view used by vanilla block collision iteration; each requested chunk must be fully admitted. */
    public static CollisionGetter collisionView(ServerLevel level, Vec3 from, Vec3 to,
                                                 double margin, int maxChunks, int chunkMargin) {
        return collisionView((net.minecraft.world.level.Level) level, from, to, margin, maxChunks, chunkMargin);
    }

    public static CollisionGetter collisionView(net.minecraft.world.level.Level level, Vec3 from, Vec3 to,
                                                 double margin, int maxChunks, int chunkMargin) {
        LoadedBlockGetter blocks = level instanceof ServerLevel server
                ? alongWithChunkMargin(server, from, to, margin, maxChunks, chunkMargin)
                : clientAlongWithChunkMargin(level, from, to, margin, maxChunks, chunkMargin);
        if (level instanceof ServerLevel) blocks.requireFullChunks = true;
        for (int x = blocks.minChunkX; x <= blocks.maxChunkX; x++) {
            for (int z = blocks.minChunkZ; z <= blocks.maxChunkZ; z++) {
                LevelChunk chunk = blocks.chunkRead.get(x, z);
                if (chunk == null || level instanceof ServerLevel
                        && !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) throw new UnknownChunk();
            }
        }
        return new CollisionGetter() {
            @Override public BlockState getBlockState(BlockPos pos) { return blocks.getBlockState(pos); }
            @Override public FluidState getFluidState(BlockPos pos) { return blocks.getFluidState(pos); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return blocks.getBlockEntity(pos); }
            @Override public int getHeight() { return blocks.getHeight(); }
            @Override public int getMinBuildHeight() { return blocks.getMinBuildHeight(); }
            @Override public LevelChunk getChunkForCollisions(int x, int z) {
                if (x < blocks.minChunkX || x > blocks.maxChunkX || z < blocks.minChunkZ || z > blocks.maxChunkZ)
                    throw new UnknownChunk();
                LevelChunk chunk = blocks.chunkRead.get(x, z);
                if (chunk == null || level instanceof ServerLevel
                        && !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) throw new UnknownChunk();
                return chunk;
            }
            @Override public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) { return List.of(); }
            @Override public WorldBorder getWorldBorder() { return level.getWorldBorder(); }
        };
    }

    private static LoadedBlockGetter along(ServerLevel level, Vec3 from, Vec3 to,
                                            double margin, int maxChunks, int chunkMargin) {
        int minX = (BlockPos.containing(Math.min(from.x, to.x) - margin, from.y, from.z).getX() >> 4)
                - chunkMargin;
        int maxX = (BlockPos.containing(Math.max(from.x, to.x) + margin, from.y, from.z).getX() >> 4)
                + chunkMargin;
        int minZ = (BlockPos.containing(from.x, from.y, Math.min(from.z, to.z) - margin).getZ() >> 4)
                - chunkMargin;
        int maxZ = (BlockPos.containing(from.x, from.y, Math.max(from.z, to.z) + margin).getZ() >> 4)
                + chunkMargin;
        long chunks = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (chunks > maxChunks) throw new UnknownChunk();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) throw new UnknownChunk();
            }
        }
        return new LoadedBlockGetter(level, minX, maxX, minZ, maxZ);
    }

    private boolean knownEmpty(BlockPos pos) {
        return frameDomain != null
                && (!frameDomain.storageBounds().contains(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)
                || !frameDomain.plot().contains(new ChunkPos(pos)));
    }

    @Nullable
    private LevelChunk chunk(BlockPos pos) {
        if (knownEmpty(pos)) return null;
        int x = pos.getX() >> 4;
        int z = pos.getZ() >> 4;
        if (frameDomain == null && (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ))
            throw new UnknownChunk();
        if (level.isOutsideBuildHeight(pos)) throw new UnknownChunk();
        if (frameDomain != null) {
            long key = ((long) x << 32) | (z & 0xffffffffL);
            frameDomain.touchedChunks().add(key);
            if (frameDomain.touchedChunks().size() > frameDomain.maxChunks()) throw new UnknownChunk();
        }
        LevelChunk chunk = chunkRead.get(x, z);
        if (chunk == null) throw new UnknownChunk();
        if (requireFullChunks && level instanceof ServerLevel && !chunk.getFullStatus().isOrAfter(
                net.minecraft.server.level.FullChunkStatus.FULL)) throw new UnknownChunk();
        return chunk;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        LevelChunk chunk = chunk(pos);
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        LevelChunk chunk = chunk(pos);
        return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        LevelChunk chunk = chunk(pos);
        return chunk == null ? null : chunk.getBlockEntities().get(pos);
    }

    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }

    /** Sable's native shape API requires this type; all reads remain inside this bounded view. */
    public dev.ryanhcode.sable.util.LevelAccelerator asNativeCollisionAccess() {
        return new dev.ryanhcode.sable.util.LevelAccelerator(level) {
            @Override public BlockState getBlockState(BlockPos pos) { return LoadedBlockGetter.this.getBlockState(pos); }
            @Override public BlockState getBlockState(LevelChunk chunk, BlockPos pos) { return LoadedBlockGetter.this.getBlockState(pos); }
            @Override public FluidState getFluidState(BlockPos pos) { return LoadedBlockGetter.this.getFluidState(pos); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return LoadedBlockGetter.this.getBlockEntity(pos); }
            @Override public int getHeight() { return LoadedBlockGetter.this.getHeight(); }
            @Override public int getMinBuildHeight() { return LoadedBlockGetter.this.getMinBuildHeight(); }
            @Override public LevelChunk getChunk(int x, int z) { throw new UnknownChunk(); }
            @Override public LevelChunk getChunk(BlockPos pos) { throw new UnknownChunk(); }
            @Override public void setBlockFast(BlockPos pos, BlockState state) {
                throw new UnsupportedOperationException("Read-only native collision access");
            }
        };
    }
}
