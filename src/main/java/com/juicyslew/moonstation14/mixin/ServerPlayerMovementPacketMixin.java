package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rejects client position/ground claims once custom movement owns the player. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerMovementPacketMixin {
    @Shadow @Final public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$rejectClientMovement(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (!MovementStartupGate.enabledForServer() || !MovementServerController.owns(player)) return;

        // Rotation remains an allowed input except while stunned. The synchronized client stun
        // projection controls local look; the server independently enforces authoritative stun.
        GameType gameType = player.gameMode.getGameModeForPlayer();
        boolean supportedMode = gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
        if (packet.hasRotation() && (!supportedMode || !CharacterControlSystem.isStunned(player))) {
            float yaw = packet.getYRot(player.getYRot());
            float pitch = packet.getXRot(player.getXRot());
            if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
                player.setYRot(Mth.wrapDegrees(yaw));
                player.setXRot(Mth.wrapDegrees(pitch));
            }
        }
        callback.cancel();
    }
}
