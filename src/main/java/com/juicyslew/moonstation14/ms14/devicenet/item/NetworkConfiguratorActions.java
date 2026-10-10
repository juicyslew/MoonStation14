package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Adapts authenticated held-body input to configurator mode and list operations. */
public final class NetworkConfiguratorActions {
    private NetworkConfiguratorActions() { }

    public static BodyHandActionResult.Reason alternate(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            BodyHandActionRequest request) {
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack before = hands == null ? ItemStack.EMPTY : hands.stackCopy(request.handId()).orElse(ItemStack.EMPTY);
        if (before.isEmpty() || !com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(before))
            return BodyHandActionResult.Reason.DENIED;
        var data = before.get(ModDataComponents.NETWORK_CONFIGURATOR.get());
        if (data == null) data = NetworkConfiguratorStackData.create();
        var nextData = new NetworkConfiguratorStackData(data.identity(), data.address(),
                data.mode() == NetworkConfiguratorStackData.Mode.LINK ? NetworkConfiguratorStackData.Mode.LIST
                        : NetworkConfiguratorStackData.Mode.LINK,
                data.staged(), data.selectedSource(), data.selectedSourcePort());
        ItemStack after = before.copy();
        after.set(ModDataComponents.NETWORK_CONFIGURATOR.get(), nextData);
        var outcome = BodyHeldItemMutation.commit(actor, authority, hands, request.handId(), request.expectedToken(),
                request.expectedRevision(), before, after);
        if (outcome == BodyHeldItemMutation.Result.SUCCESS) {
            actor.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Network Configurator: " + nextData.mode().name() + " mode"), true);
            return BodyHandActionResult.Reason.OK;
        }
        return outcome == BodyHeldItemMutation.Result.RECOVERY_REQUIRED ? BodyHandActionResult.Reason.RECOVERY_REQUIRED
                : BodyHandActionResult.Reason.DENIED;
    }

    public static BodyHandActionResult.Reason activate(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            BodyHandActionRequest request) {
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack stack = hands == null ? ItemStack.EMPTY : hands.stackCopy(request.handId()).orElse(ItemStack.EMPTY);
        if (stack.isEmpty() || !com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(stack)) return null;
        var data = stack.get(ModDataComponents.NETWORK_CONFIGURATOR.get());
        if (data == null || data.mode() == NetworkConfiguratorStackData.Mode.LINK) return BodyHandActionResult.Reason.OK;
        var id = NetworkConfiguratorListServer.openList(actor, authority, request.handId(), request.expectedToken(), request.expectedRevision());
        if(id==null)return BodyHandActionResult.Reason.DENIED;
        NetworkConfiguratorNetworking.sendListSnapshot(actor,NetworkConfiguratorListServer.currentSnapshot(actor));
        return BodyHandActionResult.Reason.OK;
    }
}
