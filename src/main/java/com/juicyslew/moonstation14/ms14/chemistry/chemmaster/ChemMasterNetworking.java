package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowUpdate;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

/** Independent workspace payload transport; never opens an AbstractContainerMenu. */
public final class ChemMasterNetworking {
    private ChemMasterNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(ChemMasterControlRequest.TYPE, ChemMasterControlRequest.STREAM_CODEC,
                        (request, context) -> {
                            if (!(context.player() instanceof ServerPlayer player) || player.connection == null
                                    || player.getServer() == null
                                    || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                            ChemMasterSessionService.handle(player, request);
                        })
                .playToClient(ChemMasterWindowUpdate.TYPE, ChemMasterWindowUpdate.STREAM_CODEC,
                        (update, context) -> {
                            if (FMLEnvironment.dist == Dist.CLIENT)
                                ChemMasterClientReceiver.handle(update.state());
                        });
    }
    static void send(ServerPlayer player, ChemMasterWindowState state) {
        PacketDistributor.sendToPlayer(player, new ChemMasterWindowUpdate(state));
    }
}
