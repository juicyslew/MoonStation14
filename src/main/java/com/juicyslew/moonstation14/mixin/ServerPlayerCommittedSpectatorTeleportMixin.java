package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.player_body_control.server.CommittedSpectatorGuard;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after vanilla's thread handoff, before target lookup and teleport side effects. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerCommittedSpectatorTeleportMixin {
    @Shadow @Final public ServerPlayer player;

    @Inject(method = "handleTeleportToEntityPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$blockCommittedTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo callback) {
        if (CommittedSpectatorGuard.blockTeleport(player, packet)) callback.cancel();
    }
}
