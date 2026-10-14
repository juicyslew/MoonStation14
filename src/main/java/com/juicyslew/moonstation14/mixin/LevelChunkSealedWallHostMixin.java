package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.sealedwall.SealedWallHostGenerations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invalidates trusted host identity before any section write (including a same-state write). */
@Mixin(LevelChunk.class)
public abstract class LevelChunkSealedWallHostMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"))
    private void moonstation14$invalidateSealedWallHost(BlockPos pos, BlockState state, boolean moved,
                                                         CallbackInfoReturnable<BlockState> cir) {
        Level level = ((LevelChunk) (Object) this).getLevel();
        if (level.isClientSide) return;

        LevelChunk chunk = (LevelChunk) (Object) this;
        SealedWallHostGenerations records = chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        if (records != null && records.invalidateExisting(pos.asLong())) {
            chunk.setUnsaved(true);
            if (level instanceof net.minecraft.server.level.ServerLevel server)
                com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.refreshLoadedAdjacent(server, pos);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel server)
            com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData.existing(server)
                    .ifPresent(data -> data.invalidate(server, pos));
    }
}

