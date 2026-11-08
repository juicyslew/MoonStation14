package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.*;

/** Bounded body-eye reach/outline LOS for persistent slotless menus; never requests chunks. */
public final class BodyMenuReach {
    public enum Result { VALID, OUT_OF_REACH, PATH_UNLOADED, LOS_BLOCKED }
    private BodyMenuReach(){}
    public static Result check(ServerLevel level,LivingEntity body,BlockPos target,Vec3 point){
        Vec3 from=body.getEyePosition();if(from.distanceToSqr(point)>36||!level.getWorldBorder().isWithinBounds(point))return Result.OUT_OF_REACH;
        int minX=(Math.min(BlockPos.containing(from).getX(),target.getX())>>4)-1,maxX=(Math.max(BlockPos.containing(from).getX(),target.getX())>>4)+1;
        int minZ=(Math.min(BlockPos.containing(from).getZ(),target.getZ())>>4)-1,maxZ=(Math.max(BlockPos.containing(from).getZ(),target.getZ())>>4)+1;
        if((long)(maxX-minX+1)*(maxZ-minZ+1)>16)return Result.PATH_UNLOADED;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){var c=level.getChunkSource().getChunkNow(x,z);if(c==null||!c.getFullStatus().isOrAfter(FullChunkStatus.FULL))return Result.PATH_UNLOADED;}
        try{var hit=new Loaded(level,minX,maxX,minZ,maxZ).clip(new ClipContext(from,point,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));return hit.getType()!=HitResult.Type.BLOCK||hit.getBlockPos().equals(target)?Result.VALID:Result.LOS_BLOCKED;}catch(RuntimeException unavailable){return Result.PATH_UNLOADED;}
    }
    private static final class Loaded implements BlockGetter {
        final ServerLevel level;final int ax,bx,az,bz;Loaded(ServerLevel l,int a,int b,int c,int d){level=l;ax=a;bx=b;az=c;bz=d;}
        LevelChunk chunk(BlockPos p){int x=p.getX()>>4,z=p.getZ()>>4;if(x<ax||x>bx||z<az||z>bz||level.isOutsideBuildHeight(p))throw new IllegalStateException();var c=level.getChunkSource().getChunkNow(x,z);if(c==null||!c.getFullStatus().isOrAfter(FullChunkStatus.FULL))throw new IllegalStateException();return c;}
        public BlockState getBlockState(BlockPos p){return chunk(p).getBlockState(p);}public FluidState getFluidState(BlockPos p){return chunk(p).getFluidState(p);}public BlockEntity getBlockEntity(BlockPos p){return chunk(p).getBlockEntities().get(p);}public int getHeight(){return level.getHeight();}public int getMinBuildHeight(){return level.getMinBuildHeight();}
    }
}
