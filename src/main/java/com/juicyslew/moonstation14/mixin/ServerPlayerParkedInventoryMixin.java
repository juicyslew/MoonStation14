package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.juicyslew.moonstation14.ms14.hands.quarantine.ParkedInventoryPacketPolicy;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Denies client packet mutations while the exact connected account has a parked carrier. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerParkedInventoryMixin {
    @Shadow @Final public ServerPlayer player;

    private boolean moonstation14$denyParkedPacket() {
        boolean connected = !(player instanceof FakePlayer) && player.level() instanceof ServerLevel level
                && !level.isClientSide && !player.isRemoved() && player.isAlive()
                && level.getServer() != null && level.getServer().isSameThread()
                && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && level.getEntity(player.getUUID()) == player;
        if (!connected) return false;
        boolean markerPresent = player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
        try {
            // existingFor intentionally rejects mismatched markers. For a connected holder, that
            // is a recovery state, not evidence that the carrier can safely be used.
            return ParkedInventoryPacketPolicy.deny(true, markerPresent,
                    CreativeParkedInventory.existingFor(player).isPresent());
        } catch (RuntimeException unreadableMarker) {
            return markerPresent; // Present but unreadable marker cannot grant mutation permission.
        }
    }

    private void moonstation14$resyncParkedPacket() {
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
    }

    @Inject(method = {
            "handlePickItem", "handleSetCarriedItem", "handleContainerClick", "handleContainerButtonClick",
            "handlePlaceRecipe", "handleSetCreativeModeSlot", "handlePlayerAction", "handleUseItem",
            "handleUseItemOn", "handleInteract"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 10)
    private void moonstation14$denyParkedMutation(CallbackInfo callback) {
        if (moonstation14$denyParkedPacket()) {
            callback.cancel();
            moonstation14$resyncParkedPacket();
        }
    }
}
