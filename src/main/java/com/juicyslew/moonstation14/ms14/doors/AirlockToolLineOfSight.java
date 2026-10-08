package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Shared reach/occlusion check for tools targeting either half of one root airlock. */
public final class AirlockToolLineOfSight {
    private AirlockToolLineOfSight() { }

    public static boolean canReachRoot(LivingEntity body,ServerLevel level,BlockPos root,AirlockBlockEntity expected,UUID identity) {
        return tracedControllerHalf(body,level,root,expected,identity)!=null;
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
        Vec3 eye=body.getEyePosition(),target=Vec3.atCenterOf(root);
        if(eye.distanceToSqr(target)>25)return null;
        // OUTLINE (not COLLIDER) retains the target outline when the door is open
        // and physically passable; opaque blocks before it still occlude the ray.
        var hit=level.clip(new ClipContext(eye,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,body));
        if(!(hit instanceof BlockHitResult blockHit))return null;
        BlockPos hitPos=blockHit.getBlockPos();
        if(!hitPos.equals(root)&&!hitPos.equals(root.above()))return null;
        return level.getBlockEntity(root)==expected&&expected.identity().equals(identity)?hitPos:null;
    }
}
