package com.juicyslew.moonstation14.ms14.prototype.network;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/** Common registration and server-side distribution for prototype catalog packets. */
public final class PrototypeCatalogNetworking {
    public static final String PROTOCOL_VERSION = "1";
    private static final AtomicLong NEXT_REVISION = new AtomicLong();
    private static volatile BiConsumer<PrototypeCatalogSyncPayload, IPayloadContext> clientHandler;

    private PrototypeCatalogNetworking() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN)
                .playToClient(PrototypeCatalogSyncPayload.TYPE, PrototypeCatalogSyncPayload.STREAM_CODEC,
                        PrototypeCatalogNetworking::handleClientPayload);
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        try {
            // NeoForge uses a null player for the post-success aggregate
            // reload event. A joining-player event must never consume a
            // candidate left by a failed aggregate reload.
            if (event.getPlayer() == null) {
                PrototypeRuntime.serverManager().commitStagedReload();
            }
            Map<ResourceLocation, Map<ResourceLocation, JsonObject>> snapshot =
                    PrototypeRuntime.serverManager().encodePublishedCatalogs();
            List<PrototypeCatalogSyncPayload> chunks = PrototypeCatalogChunker.chunk(
                    UUID.randomUUID(), NEXT_REVISION.incrementAndGet(), snapshot);
            event.getRelevantPlayers().forEach(player -> sendChunks(player, chunks));
        } catch (RuntimeException exception) {
            MoonStation14.LOGGER.error("Failed to send prototype catalog sync", exception);
            throw exception;
        }
    }

    /** Validates the exact bounded chunk format used for server sync. */
    public static void validateSyncableCatalogs(
            Map<ResourceLocation, ? extends Map<ResourceLocation, JsonObject>> catalogs) {
        // The UUID is fixed for deterministic validation. Reserve the largest
        // wire width of any valid positive revision used by live syncs.
        PrototypeCatalogChunker.chunk(new UUID(0L, 0L), Long.MAX_VALUE, catalogs);
    }

    public static void installClientHandler(BiConsumer<PrototypeCatalogSyncPayload, IPayloadContext> handler) {
        clientHandler = handler;
    }

    private static void handleClientPayload(PrototypeCatalogSyncPayload payload, IPayloadContext context) {
        BiConsumer<PrototypeCatalogSyncPayload, IPayloadContext> handler = clientHandler;
        if (handler == null) {
            throw new IllegalStateException("received prototype catalog sync before client handler installation");
        }
        handler.accept(payload, context);
    }

    private static void sendChunks(ServerPlayer player, List<PrototypeCatalogSyncPayload> chunks) {
        for (PrototypeCatalogSyncPayload chunk : chunks) {
            PacketDistributor.sendToPlayer(player, chunk);
        }
    }
}
