package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retires construction records on any external chunk write, including same-state incarnation replacement. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkConstructionHostMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"))
    private void moonstation14$invalidateConstructionHost(BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                                           boolean moved, CallbackInfoReturnable<net.minecraft.world.level.block.state.BlockState> cir) {
        Level level = ((LevelChunk)(Object)this).getLevel();
        if (level instanceof net.minecraft.server.level.ServerLevel server) ConstructionWorldData.invalidateExternalBlockWrite(server, pos);
    }
}

