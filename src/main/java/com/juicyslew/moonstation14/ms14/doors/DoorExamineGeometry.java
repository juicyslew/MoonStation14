package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Pure shared authored-door outline/ray policy used by the world selector for both door types. */
public final class DoorExamineGeometry {
    public record Hit(Vec3 point,double distance,AABB bounds) { }
    private DoorExamineGeometry() { }

    public static List<AABB> visiblePieces(List<AABB> base,List<AABB> cover,boolean attached,boolean latchedClosed,double q) {
        var result=new ArrayList<AABB>();
        if(!attached || !latchedClosed || q!=0) result.addAll(base);
        if(attached) result.addAll(cover);
        return List.copyOf(result);
    }
    public static AABB bounds(List<AABB> pieces) {
        AABB result=null; for(AABB piece:pieces) result=result==null?piece:result.minmax(piece); return result;
    }
    /** Uses Minecraft's own AABB ray clip; this is the selector's actual authored-box hit path. */
    public static Hit ray(List<AABB> pieces,Vec3 origin,Vec3 end) {
        Hit nearest=null; AABB union=bounds(pieces);
        for(AABB piece:pieces) {
            var point=piece.clip(origin,end);
            if(point.isPresent()) { Vec3 hit=point.get(); double distance=origin.distanceTo(hit);
                if(nearest==null||distance<nearest.distance()) nearest=new Hit(hit,distance,union);
            }
        }
        return nearest;
    }
    public static BlockPos canonicalLowerRoot(BlockPos pos,boolean upperHalf,boolean pairedLower) {
        return upperHalf&&pairedLower?pos.below().immutable():pos.immutable();
    }
}
