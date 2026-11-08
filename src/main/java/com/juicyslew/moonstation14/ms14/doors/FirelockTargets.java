package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.UUID;

/** Loaded-only resolver shared by activation, timed pry, LOS and space closure routes. */
public final class FirelockTargets {
    private FirelockTargets() { }
    public record Target(BlockPos root, FirelockController controller, FirelockHost host, boolean attached) {
        public UUID identity(){return controller.identity();}
        public FirelockState state(){return controller.state();}
    }

    /** Firelock block halves target the standalone door; the upper airlock half selects a closed overlay. */
    public static Target atHit(ServerLevel level, BlockPos hit) {
        if(level==null||hit==null||!level.hasChunkAt(hit)||!level.getWorldBorder().isWithinBounds(hit))return null;
        var block=level.getBlockState(hit);
        if(block.getBlock() instanceof FirelockBlock){BlockPos root=block.getValue(FirelockBlock.HALF)==DoubleBlockHalf.UPPER?hit.below():hit;return atRoot(level,root);}
        if(block.getBlock() instanceof AirlockBlock&&block.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER){
            BlockPos root=hit.below();Target target=atRoot(level,root);
            return target!=null&&target.attached()&&target.state()!=null&&!target.state().passable()?target:null;
        }
        return null;
    }

    /** Resolves an owned lower/root cell without loading chunks; used for endpoint and timed-action revalidation. */
    public static Target atRoot(ServerLevel level,BlockPos root) {
        if(level==null||root==null||!level.hasChunkAt(root)||!level.hasChunkAt(root.above())||!level.getWorldBorder().isWithinBounds(root)||!level.getWorldBorder().isWithinBounds(root.above()))return null;
        var lower=level.getBlockState(root);var upper=level.getBlockState(root.above());
        if(lower.getBlock() instanceof FirelockBlock&&lower.getValue(FirelockBlock.HALF)==DoubleBlockHalf.LOWER
                &&upper.getBlock()==lower.getBlock()&&upper.getValue(FirelockBlock.HALF)==DoubleBlockHalf.UPPER
                &&level.getBlockEntity(root) instanceof FirelockBlockEntity standalone)
            return new Target(root.immutable(),standalone.firelockController(),standalone, false);
        if(lower.getBlock() instanceof AirlockBlock&&lower.getValue(AirlockBlock.HALF)==DoubleBlockHalf.LOWER
                &&upper.getBlock()==lower.getBlock()&&upper.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER
                &&level.getBlockEntity(root) instanceof AirlockBlockEntity airlock&&airlock.firelockController()!=null)
            return new Target(root.immutable(),airlock.firelockController(),airlock.firelockHost(),true);
        return null;
    }

    public static boolean stillValid(ServerLevel level,BlockPos root,UUID identity,long revision) {
        Target t=atRoot(level,root);
        return t!=null&&t.identity().equals(identity)&&t.state()!=null&&t.state().revision()==revision&&t.host().live(level);
    }
}
