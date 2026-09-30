package com.juicyslew.moonstation14.ms14.power.ui;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Server-side menu open and request validation. */
public final class ApcMenuService {
    private ApcMenuService() { }

    public static boolean open(ServerPlayer player, BlockPos pos, PowerDeviceBlockEntity device) {
        if (!validLoadedApc(player, pos, device) || !deviceAccess(player, pos)) return false;
        UUID session = UUID.randomUUID();
        MenuProvider provider = new SimpleMenuProvider((id, inventory, owner) ->
                new ApcMenu(id, inventory, pos, session, player.serverLevel(), device),
                Component.translatable("container.moonstation14.apc"));
        player.openMenu(provider, buf -> {
            buf.writeBlockPos(pos);
            buf.writeUUID(session);
        });
        return player.containerMenu instanceof ApcMenu menu && menu.session().equals(session)
                && menu.boundDevice() == device;
    }

    public static ApcToggleResponse apply(ServerPlayer player, ApcToggleRequest request) {
        if (player.containerMenu.containerId != request.containerId()
                || !(player.containerMenu instanceof ApcMenu menu)
                || !menu.session().equals(request.session()))
            return result(request, false, false, false, 0, 0, false);
        if (!menu.validContext(player)) return result(request, false, false, false, 0, 0, false);
        PowerDeviceBlockEntity device = menu.boundDevice();
        double distance = player.distanceToSqr(menu.devicePos().getX() + .5,
                menu.devicePos().getY() + .5, menu.devicePos().getZ() + .5);
        ApcBreakerIntentPolicy.Decision decision = ApcBreakerIntentPolicy.decide(
                request.expectedRevision(), menu.currentRevision(), device.breakerClosed(), request.desiredClosed());
        boolean accepted = decision == ApcBreakerIntentPolicy.Decision.NO_CHANGE
                || decision == ApcBreakerIntentPolicy.Decision.TOGGLE && device.toggleBreaker(player, distance);
        return result(request, accepted, true, device.breakerClosed(), menu.currentRevision(),
                ApcMenu.batteryPermille(device.energyJoules()), device.tripLatched());
    }

    private static ApcToggleResponse result(ApcToggleRequest request, boolean accepted, boolean snapshot,
                                            boolean breakerClosed, long revision, int batteryPermille,
                                            boolean tripLatched) {
        return new ApcToggleResponse(request.containerId(), request.session(), request.requestId(), accepted,
                snapshot, breakerClosed, revision, batteryPermille, tripLatched);
    }

    private static boolean validLoadedApc(ServerPlayer player, BlockPos pos, PowerDeviceBlockEntity device) {
        if (!player.serverLevel().hasChunkAt(pos) || player.serverLevel().getBlockEntity(pos) != device) return false;
        return player.serverLevel().getBlockState(pos).getBlock() instanceof
                com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block
                && block.kind() == com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.APC;
    }

    private static boolean deviceAccess(ServerPlayer player, BlockPos pos) {
        double distance = player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        return com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules.canToggle(
                true, player.mayBuild(), distance);
    }
}
