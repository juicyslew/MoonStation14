package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.ControlledChunkDiagnostics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** State-change diagnostics for the server-authored chunk-cache center and controlled camera. */
@Mixin(ClientPacketListener.class)
public abstract class ClientChunkCenterDiagnosticMixin {
    @Inject(method = "handleSetChunkCacheCenter", at = @At("TAIL"))
    private void moonstation14$logChunkCenter(ClientboundSetChunkCacheCenterPacket packet, CallbackInfo ci) {
        ControlledChunkDiagnostics.observeCenter(packet.getX(), packet.getZ());
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void moonstation14$observeReceivedChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        ControlledChunkDiagnostics.observe(net.minecraft.client.Minecraft.getInstance());
    }
}
