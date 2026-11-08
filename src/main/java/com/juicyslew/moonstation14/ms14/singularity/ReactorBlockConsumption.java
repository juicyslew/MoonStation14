package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Shared eligibility and safe-removal path for reactor contact block consumption. */
public final class ReactorBlockConsumption {
    private ReactorBlockConsumption() { }

    public static boolean eligible(ServerLevel level, BlockPos pos, boolean canBreachContainment) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        var be = level.getBlockEntity(pos);
        if (be instanceof FieldGeneratorBlockEntity generator && !canBreachContainment
                && FieldContainmentRuntime.hasConnection(level, generator)) return false;
        return be == null || be instanceof SingularityConsumable || state.getBlock() instanceof SingularityConsumable;
    }

    public static boolean touches(BlockPos pos, Vec3 center, double radius) {
        return SingularityHorizonGeometry.touches(center, radius, new net.minecraft.world.phys.AABB(pos));
    }
    public static boolean touchesSegment(BlockPos pos, Vec3 start, Vec3 end, double radius) {
        var box=new net.minecraft.world.phys.AABB(pos);
        if(start==null||end==null||!Double.isFinite(radius)||radius<0)return false;
        double lo=0,hi=1;
        for(int i=0;i<48;i++){
            double a=(2*lo+hi)/3,b=(lo+2*hi)/3;
            if(distance(start,end,box,a)<=distance(start,end,box,b))hi=b;else lo=a;
        }
        return Math.min(distance(start,end,box,(lo+hi)*.5),Math.min(distance(start,end,box,0),distance(start,end,box,1)))<=radius*radius;
    }
    private static double distance(Vec3 start,Vec3 end,net.minecraft.world.phys.AABB box,double t){
        Vec3 p=start.add(end.subtract(start).scale(t));
        return p.distanceToSqr(net.minecraft.util.Mth.clamp(p.x,box.minX,box.maxX),net.minecraft.util.Mth.clamp(p.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(p.z,box.minZ,box.maxZ));
    }

    public static boolean remove(ServerLevel level, BlockPos pos, boolean canBreachContainment) {
        if (!eligible(level, pos, canBreachContainment) || !level.removeBlock(pos, false)) return false;
        com.juicyslew.moonstation14.ms14.power.cable.CableStorage.removeHost(level, pos);
        return true;
    }
}
