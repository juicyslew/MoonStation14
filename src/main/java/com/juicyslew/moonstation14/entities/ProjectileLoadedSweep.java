package com.juicyslew.moonstation14.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared bounded block-ray primitive for fast native projectiles; never requests an unloaded chunk. */
public final class ProjectileLoadedSweep {
    private ProjectileLoadedSweep() { }
    public static boolean isLoadedSpan(ServerLevel level, Vec3 from, Vec3 to, double halfExtent) {
        if (level == null || from == null || to == null || !Double.isFinite(halfExtent) || halfExtent < 0) return false;
        AABB sweep = new AABB(from, to).inflate(halfExtent);
        int minX = BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ).getX() >> 4;
        int maxX = BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ).getX() >> 4;
        int minZ = BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ).getZ() >> 4;
        int maxZ = BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ).getZ() >> 4;
        if ((maxX-minX+1L)*(maxZ-minZ+1L)>4) return false;
        for (int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null) return false;
        return true;
    }
    public static HitResult clip(ServerLevel level, Entity projectile, Vec3 from, Vec3 to, double halfExtent) {
        if (level == null || projectile == null || from == null || to == null
                || !Double.isFinite(halfExtent) || halfExtent < 0) return null;
        if (!isLoadedSpan(level, from, to, halfExtent)) return null;
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,projectile));
    }
}

