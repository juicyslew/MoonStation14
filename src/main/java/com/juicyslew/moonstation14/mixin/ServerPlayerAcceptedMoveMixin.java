package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges vanilla's accepted player-movement bookkeeping to authoritative puddle contact. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerAcceptedMoveMixin {
    @Shadow @Final public ServerPlayer player;

    @Inject(method = "handleMovePlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;setKnownMovement(Lnet/minecraft/world/phys/Vec3;)V",
                    shift = At.Shift.AFTER),
            require = 1)
    private void moonstation14$checkAcceptedPuddleMovement(CallbackInfo callback) {
        SlipSystem.onAcceptedPlayerMovement(player, player.getKnownMovement());
    }
}
