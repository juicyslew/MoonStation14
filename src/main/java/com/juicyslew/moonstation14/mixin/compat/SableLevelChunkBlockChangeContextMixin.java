package com.juicyslew.moonstation14.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Preserve Sable's active block-change position across legitimate nested chunk writes. */
@Mixin(value = LevelChunk.class, priority = 900)
public abstract class SableLevelChunkBlockChangeContextMixin {
    // Sable 2.0.6's required LevelChunkMixin owns this private @Unique context field.
    @Accessor(value = "sable$blockSet", remap = false)
    protected abstract BlockPos moonstation14$getSableBlockSet();

    @Accessor(value = "sable$blockSet", remap = false)
    protected abstract void moonstation14$setSableBlockSet(BlockPos position);

    @WrapMethod(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;")
    private BlockState moonstation14$restoreSableBlockChangeContext(BlockPos pos, BlockState state,
                                                                    boolean isMoving,
                                                                    Operation<BlockState> original) {
        BlockPos previous = moonstation14$getSableBlockSet();
        try {
            return original.call(pos, state, isMoving);
        } finally {
            moonstation14$setSableBlockSet(previous);
        }
    }
}
