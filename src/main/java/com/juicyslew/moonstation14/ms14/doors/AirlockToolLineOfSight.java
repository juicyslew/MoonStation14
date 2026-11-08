package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import java.util.UUID;

/** Shared reach/occlusion check for tools targeting either half of one root airlock. */
public final class AirlockToolLineOfSight {
    private AirlockToolLineOfSight() { }

    public static boolean canReachRoot(LivingEntity body,ServerLevel level,BlockPos root,AirlockBlockEntity expected,UUID identity) {
        return tracedControllerHalf(body,level,root,expected,identity)!=null;
    }

    /** Same root/reach/outline policy for the second supported two-cell door owner. */
    public static boolean firelockReach(LivingEntity body,ServerLevel level,BlockPos root,FirelockBlockEntity expected,UUID identity) {
        if(body==null||level==null||root==null||expected==null||identity==null||body.level()!=level
                ||!level.hasChunkAt(root)||!level.hasChunkAt(root.above())
                ||!level.getWorldBorder().isWithinBounds(root)||!level.getWorldBorder().isWithinBounds(root.above())
                ||level.getBlockEntity(root)!=expected||!expected.identity().equals(identity)
                ||!(level.getBlockState(root).getBlock() instanceof FirelockBlock)
                ||level.getBlockState(root).getValue(FirelockBlock.HALF)!=DoubleBlockHalf.LOWER
                ||level.getBlockState(root.above()).getBlock()!=level.getBlockState(root).getBlock()
                ||level.getBlockState(root.above()).getValue(FirelockBlock.HALF)!=DoubleBlockHalf.UPPER)return false;
        Vec3 eye=body.getEyePosition(),target=Vec3.atCenterOf(root);
        if(eye.distanceToSqr(target)>25)return false;
        var hit=level.clip(new ClipContext(eye,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));
        return hit instanceof BlockHitResult blockHit&&(blockHit.getBlockPos().equals(root)||blockHit.getBlockPos().equals(root.above()))
                &&level.getBlockEntity(root)==expected&&expected.identity().equals(identity);
    }

    /** Reach check for either controller placement, requiring the canonical target and both visible door cells. */
    public static boolean firelockReach(LivingEntity body,ServerLevel level,BlockPos root,FirelockTargets.Target expected,UUID identity) {
        if(body==null||level==null||root==null||expected==null||identity==null||body.level()!=level
                ||!root.equals(expected.root())||!identity.equals(expected.identity())
                ||!level.hasChunkAt(root)||!level.hasChunkAt(root.above())
                ||!level.getWorldBorder().isWithinBounds(root)||!level.getWorldBorder().isWithinBounds(root.above()))return false;
        FirelockTargets.Target current=FirelockTargets.atRoot(level,root);
        if(current==null||current.controller()!=expected.controller()||!current.identity().equals(identity))return false;
        Vec3 eye=body.getEyePosition(),target=Vec3.atCenterOf(root);
        if(eye.distanceToSqr(target)>25)return false;
        var hit=level.clip(new ClipContext(eye,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));
        return hit instanceof BlockHitResult blockHit&&(blockHit.getBlockPos().equals(root)||blockHit.getBlockPos().equals(root.above()));
    }

    static BlockPos tracedControllerHalf(LivingEntity body,ServerLevel level,BlockPos root,AirlockBlockEntity expected,UUID identity) {
        if(body==null||level==null||root==null||expected==null||identity==null||body.level()!=level
                ||!level.hasChunkAt(root)||!level.hasChunkAt(root.above())
                ||!level.getWorldBorder().isWithinBounds(root)||!level.getWorldBorder().isWithinBounds(root.above()))return null;
        if(level.getBlockEntity(root)!=expected||!expected.identity().equals(identity)
                ||!(level.getBlockState(root).getBlock() instanceof AirlockBlock)
                ||level.getBlockState(root).getValue(AirlockBlock.HALF)!=DoubleBlockHalf.LOWER
                ||level.getBlockState(root.above()).getBlock()!=level.getBlockState(root).getBlock()
                ||level.getBlockState(root.above()).getValue(AirlockBlock.HALF)!=DoubleBlockHalf.UPPER)return null;
        var targetPoint=LoadedFrameRayQuery.currentTargetPoint(level,root);
        var upperPoint=LoadedFrameRayQuery.currentTargetPoint(level,root.above());
        if(targetPoint==null||upperPoint==null||!java.util.Objects.equals(targetPoint.frameId(),upperPoint.frameId()))return null;
        Vec3 eye=body.getEyePosition(),target=targetPoint.worldPoint();
        if(eye.distanceToSqr(target)>25)return null;
        // OUTLINE (not COLLIDER) retains the target outline when the door is open
        // and physically passable; opaque blocks before it still occlude the ray.
        var result=LoadedFrameRayQuery.clip(level,eye,target,16,.125,1,
                (getter,from,to)->new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));
        if(result.state()==LoadedFrameRayQuery.State.UNKNOWN)return null;
        // The query retains both nominations when terrain wins; never trust a farther frame hit.
        if(result.frameHit()!=null&&result.worldHit()!=null
                &&result.worldHit().getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)return null;
        var frame=result.frameHit();
        if(frame!=null){
            if(!java.util.Objects.equals(targetPoint.frameId(),frame.frameId())
                    ||!frame.storagePos().equals(root)&&!frame.storagePos().equals(root.above())
                    ||!LoadedFrameRayQuery.revalidate(level,frame))return null;
            return frame.storagePos();
        }
        if(result.worldHit()==null||result.worldHit().getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return null;
        BlockPos hitPos=result.worldHit().getBlockPos();
        if(!java.util.Objects.equals(targetPoint.frameId(),null)||!hitPos.equals(root)&&!hitPos.equals(root.above()))return null;
        return level.getBlockEntity(root)==expected&&expected.identity().equals(identity)?hitPos:null;
    }
}
