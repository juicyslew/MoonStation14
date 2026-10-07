package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent native body movement packets from applying a corpse/observer's client transform. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMedicalMovementMixin {
    @Shadow @Final private ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$rejectMedicalBodyMovement(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        var state = player.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        boolean medicalBody = state != null && (state.data().state() == MobStateComponent.State.CRITICAL
                || state.data().state() == MobStateComponent.State.DEAD);
        ObserverSession session = ObserverSystem.query(player);
        if (medicalBody || session != null) callback.cancel();
    }
}
