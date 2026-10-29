package com.juicyslew.moonstation14.ms14.actions.network;

import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionService;
import com.juicyslew.moonstation14.ms14.actions.client.BodyActionClient;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Network transport for authoritative body actions. */
public final class BodyActionNetworking {
    private record Sent(UUID body, long epoch, long revision) { }
    private static final Map<UUID, Sent> SENT = new HashMap<>();

    private BodyActionNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(BodyActionNetworking.class);
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(BodyActionPayloads.Query.TYPE, BodyActionPayloads.Query.STREAM_CODEC, (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || !valid(player)) return;
                    sendSnapshot(player);
                })
                .playToServer(BodyActionPayloads.Activate.TYPE, BodyActionPayloads.Activate.STREAM_CODEC, (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || !valid(player)) return;
                    var result = BodyActionService.activate(player, payload.request());
                    if (valid(player)) PacketDistributor.sendToPlayer(player, new BodyActionPayloads.Result(result));
                })
                .playToClient(BodyActionPayloads.Snapshot.TYPE, BodyActionPayloads.Snapshot.STREAM_CODEC, (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BodyActionClient.onSnapshot(payload.snapshot());
                })
                .playToClient(BodyActionPayloads.Result.TYPE, BodyActionPayloads.Result.STREAM_CODEC, (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BodyActionClient.onResult(payload.result());
                });
    }

    private static boolean valid(ServerPlayer player) {
        return player.connection != null && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void sendSnapshot(ServerPlayer player) {
        if (valid(player)) PacketDistributor.sendToPlayer(player, new BodyActionPayloads.Snapshot(BodyActionService.query(player)));
    }

    public static void requestQuery() { PacketDistributor.sendToServer(new BodyActionPayloads.Query()); }
    public static void requestAction(BodyActionRequest request) { PacketDistributor.sendToServer(new BodyActionPayloads.Activate(request)); }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !valid(player)) return;
        var snapshot = BodyActionService.query(player);
        var previous = SENT.get(player.getUUID());
        if (snapshot.isEmpty()) {
            if (previous != null) {
                SENT.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, new BodyActionPayloads.Snapshot(java.util.Optional.empty()));
            }
            return;
        }
        var current = snapshot.get();
        var identity = new Sent(current.bodyId(), current.bodyEpoch(), current.revision());
        if (!identity.equals(previous)) {
            SENT.put(player.getUUID(), identity);
            PacketDistributor.sendToPlayer(player, new BodyActionPayloads.Snapshot(snapshot));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SENT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { SENT.clear(); }
}
