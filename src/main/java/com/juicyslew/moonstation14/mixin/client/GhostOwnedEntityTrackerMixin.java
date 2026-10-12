package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves vanilla tracker packets untouched except for the actively predicted ghost body. */
@Mixin(ClientPacketListener.class)
public abstract class GhostOwnedEntityTrackerMixin {
    /** Vanilla motion packets are another tracker-owned write to the locally predicted body. */
    @Inject(method = "handleSetEntityMotion(Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$keepOwnedGhostMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity target = minecraft.level.getEntity(packet.getId());
        if (GhostControlClient.ownedHarnessForTracker(target) != null) callback.cancel();
    }

    @Inject(method = "handleMoveEntity(Lnet/minecraft/network/protocol/game/ClientboundMoveEntityPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$keepOwnedGhostPrediction(ClientboundMoveEntityPacket packet, CallbackInfo callback) {
        if (Minecraft.getInstance().level == null) return;
        Entity target = packet.getEntity(Minecraft.getInstance().level);
        Entity owned = GhostControlClient.ownedHarnessForTracker(target);
        if (owned == null) return;

        if (packet.hasPosition()) {
            Vec3 decoded = owned.getPositionCodec().decode(packet.getXa(), packet.getYa(), packet.getZa());
            owned.getPositionCodec().setBase(decoded);
        }
        callback.cancel();
    }

    @Inject(method = "handleTeleportEntity(Lnet/minecraft/network/protocol/game/ClientboundTeleportEntityPacket;)V",
            at = @At("TAIL"), require = 1)
    private void moonstation14$barrierOwnedGhostPrediction(ClientboundTeleportEntityPacket packet,
                                                            CallbackInfo callback) {
        GhostControlClient.onOwnedGhostTeleport(packet.getId());
    }
}
