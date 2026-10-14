package com.juicyslew.moonstation14.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapObserverAccess {
    @Invoker("applyChunkTrackingView")
    void moonstation14$applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view);

}

