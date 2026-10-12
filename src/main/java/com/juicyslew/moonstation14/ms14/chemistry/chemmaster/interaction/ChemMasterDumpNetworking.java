package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Dedicated transport for the independent dump gesture. */
public final class ChemMasterDumpNetworking {
    private ChemMasterDumpNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(ChemMasterDumpRequest.TYPE, ChemMasterDumpRequest.STREAM_CODEC, (request, context) -> {
                    if (context.player() instanceof ServerPlayer player && player.connection != null
                            && player.getServer() != null
                            && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player)
                        ChemMasterDumpService.handle(player, request);
                }).playToClient(ChemMasterDumpResult.TYPE, ChemMasterDumpResult.STREAM_CODEC, (result, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) ChemMasterDumpClientReceiver.handle(result);
                });
    }
}
