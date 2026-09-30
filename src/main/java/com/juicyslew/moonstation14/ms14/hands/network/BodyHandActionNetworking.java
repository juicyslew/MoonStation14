package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

/** Common registration only; no client key binding or UI is installed. */
public final class BodyHandActionNetworking {
    private BodyHandActionNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        NeoForge.EVENT_BUS.addListener(BodyHandRequestService::logout);
        NeoForge.EVENT_BUS.addListener(BodyHandStateService::logout);
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(BodyHandActionRequest.TYPE, BodyHandActionRequest.STREAM_CODEC, (request, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    // Never acknowledge a displaced connection or an actor not currently online.
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor)
                        return;
                    BodyHandActionResult result = BodyHandRequestService.apply(actor, request);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor)
                        PacketDistributor.sendToPlayer(actor, result);
                })
                .playToClient(BodyHandActionResult.TYPE, BodyHandActionResult.STREAM_CODEC,
                        (result, context) -> {
                            if (FMLEnvironment.dist == Dist.CLIENT) BodyHandActionResult.deliverClient(result);
                        })
                .playToServer(BodyHandStateQuery.TYPE, BodyHandStateQuery.STREAM_CODEC, (query, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor)
                        return;
                    var snapshot = BodyHandStateService.query(actor, query);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor)
                        PacketDistributor.sendToPlayer(actor, snapshot);
                })
                .playToClient(BodyHandStateSnapshot.TYPE, BodyHandStateSnapshot.STREAM_CODEC,
                        (snapshot, context) -> {
                            if (FMLEnvironment.dist == Dist.CLIENT) BodyHandStateSnapshot.deliverClient(snapshot);
                        });
    }
}
