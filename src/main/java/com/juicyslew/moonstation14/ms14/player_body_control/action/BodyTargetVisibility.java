package com.juicyslew.moonstation14.ms14.player_body_control.action;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/** Shared loaded-only collider visibility for read-only body-target admission. */
public final class BodyTargetVisibility {
    private static final int MAX_CHUNKS = 16;
    private BodyTargetVisibility() { }

    /** Shared bounded getter used by all body-target block queries. */
    public static BlockGetter loadedAlong(ServerLevel level, Vec3 from, Vec3 to, double margin) {
        int minX=BlockPos.containing(Math.min(from.x,to.x)-margin,from.y,from.z).getX()>>4;
        int maxX=BlockPos.containing(Math.max(from.x,to.x)+margin,from.y,from.z).getX()>>4;
        int minZ=BlockPos.containing(from.x,from.y,Math.min(from.z,to.z)-margin).getZ()>>4;
        int maxZ=BlockPos.containing(from.x,from.y,Math.max(from.z,to.z)+margin).getZ()>>4;
        if((long)(maxX-minX+1)*(maxZ-minZ+1)>MAX_CHUNKS)throw new IllegalStateException("target query exceeds loaded chunk bound");
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level.getChunkSource().getChunkNow(x,z)==null)throw new IllegalStateException("target query crosses unloaded chunk");
        return new Getter(level,minX,maxX,minZ,maxZ);
    }

    public static boolean visible(ServerLevel level, Entity body, Vec3 from, Vec3 to) {
        if (level == null || body == null || level.isClientSide || !level.getServer().isSameThread()
                || body.level() != level || level.getEntity(body.getUUID()) != body || body.isRemoved() || !body.isAddedToLevel()
                || from == null || to == null || !finite(from) || !finite(to)
                || !inside(level, from) || !inside(level, to)) return false;
        int minX = (Math.min(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) + 1;
        if ((long)(maxX-minX+1)*(maxZ-minZ+1)>MAX_CHUNKS) return false;
        try {
            for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++)
                if(level.getChunkSource().getChunkNow(x,z)==null)return false;
            BlockHitResult hit = new Getter(level,minX,maxX,minZ,maxZ).clip(new ClipContext(from,to,
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            return hit.getType()==HitResult.Type.MISS;
        } catch (MissingChunk ignored) { return false; }
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
    private static boolean inside(ServerLevel l, Vec3 v) { return !l.isOutsideBuildHeight(BlockPos.containing(v))&&l.getWorldBorder().isWithinBounds(v); }
    private static final class MissingChunk extends RuntimeException { }
    private static final class Getter implements BlockGetter {
        private final ServerLevel level; private final int minX,maxX,minZ,maxZ;
        Getter(ServerLevel l,int a,int b,int c,int d){level=l;minX=a;maxX=b;minZ=c;maxZ=d;}
        private LevelChunk chunk(BlockPos p){int x=p.getX()>>4,z=p.getZ()>>4;if(x<minX||x>maxX||z<minZ||z>maxZ)throw new MissingChunk();LevelChunk c=level.getChunkSource().getChunkNow(x,z);if(c==null||level.isOutsideBuildHeight(p))throw new MissingChunk();return c;}
        @Override public BlockState getBlockState(BlockPos p){return chunk(p).getBlockState(p);}
        @Override public FluidState getFluidState(BlockPos p){return chunk(p).getFluidState(p);}
        @Nullable @Override public BlockEntity getBlockEntity(BlockPos p){return chunk(p).getBlockEntities().get(p);}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
    }
}
