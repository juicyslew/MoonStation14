package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server packet boundary for player actions which vanilla executes on receipt. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerStunActionMixin {
    @Shadow @Final public ServerPlayer player;

    private boolean moonstation14$denyAction() {
        return !CharacterControlSystem.canAct(player);
    }

    /** Correct client-side item predictions without changing authoritative inventory state. */
    private void moonstation14$resyncInventory() {
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    @Inject(method = "handlePlayerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyPlayerInput(CallbackInfo callback) {
        if (moonstation14$denyAction()) callback.cancel();
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyMove(CallbackInfo callback) {
        // Custom movement's packet boundary rejects client positions without teleporting; let it
        // process movement packets even when stunned, and avoid issuing a competing correction.
        if (MovementStartupGate.enabledForServer() && MovementServerController.owns(player)) return;
        if (moonstation14$denyAction()) {
            callback.cancel();
            // Use vanilla's position-sync correction path; do not accept a client-proposed position.
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
        }
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyUseOn(CallbackInfo callback) {
        if (moonstation14$denyAction()) {
            callback.cancel();
            moonstation14$resyncInventory();
        }
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyUse(CallbackInfo callback) {
        if (moonstation14$denyAction()) {
            callback.cancel();
            moonstation14$resyncInventory();
        }
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyInteract(CallbackInfo callback) {
        if (moonstation14$denyAction()) {
            callback.cancel();
            moonstation14$resyncInventory();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        if (moonstation14$denyAction()) {
            callback.cancel();
            ServerboundPlayerActionPacket.Action action = packet.getAction();
            if (action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                    || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                    || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
                moonstation14$resyncInventory();
            }
        }
    }

    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyPlayerCommand(CallbackInfo callback) {
        if (moonstation14$denyAction()) callback.cancel();
    }

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyContainerClick(CallbackInfo callback) {
        if (moonstation14$denyAction()) {
            callback.cancel();
            player.containerMenu.sendAllDataToRemote();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void moonstation14$denyCreativeSlot(CallbackInfo callback) {
        if (moonstation14$denyAction()) {
            callback.cancel();
            player.inventoryMenu.sendAllDataToRemote();
        }
    }
}
