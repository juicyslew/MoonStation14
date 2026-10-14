package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverTracking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkMap.class)
public abstract class ChunkMapObserverCenterMixin {
    @Redirect(method = "updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos moonstation14$observerChunkCenter(ServerPlayer player) {
        var session = ObserverSystem.query(player);
        if (session != null && session.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession.Mode.GHOST) {
            return new ChunkPos(BlockPos.containing(ObserverTracking.viewPosition(player)));
        }
        return player.chunkPosition();
    }
}

