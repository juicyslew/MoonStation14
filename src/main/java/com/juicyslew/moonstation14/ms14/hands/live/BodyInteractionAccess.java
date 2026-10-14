package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared physical-body reach and loaded eye-ray policy for body-target interactions. */
public final class BodyInteractionAccess {
    private BodyInteractionAccess() { }
    /** Pure physical-root range predicate shared by authoritative admission and client nomination. */
    public static boolean rootWithinReach(net.minecraft.world.entity.Entity actor,
                                          net.minecraft.world.entity.Entity target,double reach) {
        return actor!=null&&target!=null&&rootWithinReach(actor.position(),target.position(),reach);
    }
    public static boolean rootWithinReach(Vec3 actorRoot,Vec3 targetRoot,double reach) {
        if(actorRoot==null||targetRoot==null||!Double.isFinite(reach)||reach<0)return false;
        return actorRoot.distanceToSqr(targetRoot)<=reach*reach;
    }
    public static boolean inRange(LivingEntity actor, LivingEntity target, double reach) {
        if (actor == null || target == null || !Double.isFinite(reach) || reach < 0
                || actor.level() != target.level() || !rootWithinReach(actor,target,reach)
                || !(actor.level() instanceof ServerLevel level)) return false;
        Vec3 from=actor.getEyePosition(), to=target.getEyePosition();
        if (!loadedRay(level,from,to)) return false;
        return level.clip(new net.minecraft.world.level.ClipContext(from,to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,actor)).getType()==HitResult.Type.MISS;
    }
    private static boolean loadedRay(ServerLevel level,Vec3 from,Vec3 to) {
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)*4));
        if(steps>32)return false;
        for(int i=0;i<=steps;i++)if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(from.lerp(to,(double)i/steps))))return false;
        return true;
    }
}

