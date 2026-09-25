package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes successful vanilla teleport acknowledgements only after its normal handler completes. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerMovementTeleportAckMixin {
    @Shadow @Final public ServerPlayer player;

    @Inject(method = "handleAcceptTeleportPacket", at = @At("RETURN"), require = 1)
    private void moonstation14$afterTeleportAcknowledgement(ServerboundAcceptTeleportationPacket packet,
                                                              CallbackInfo callback) {
        MovementServerController.onVanillaTeleportAccepted(player, packet.getId());
    }
}
