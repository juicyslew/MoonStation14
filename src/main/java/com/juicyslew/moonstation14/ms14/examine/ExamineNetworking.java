package com.juicyslew.moonstation14.ms14.examine;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Main-thread owner request and authoritative response networking. Contains no client-class references. */
public final class ExamineNetworking {
    private static volatile Consumer<ExamineResponse> clientResponseHandler;
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID, Long> LAST_ACCEPTED_INFO = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID, Long> LAST_DENIAL_INFO = new java.util.concurrent.ConcurrentHashMap<>();
    private ExamineNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        BuiltinExamineContributors.register();
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(ExamineRequest.TYPE, ExamineRequest.STREAM_CODEC, (request, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    context.enqueueWork(() -> {
                        ExamineService.QueryResult result = ExamineService.queryDiagnostic(player, request);
                        var admission = result.admission();
                        if (admission != ExamineService.Admission.ACCEPTED) {
                            long now = player.level().getGameTime();
                            Long previous = LAST_DENIAL_INFO.put(player.getUUID(), now);
                            if (previous == null || now - previous >= 20) {
                                var visibility = result.visibility();
                                String blockerId = visibility == null || visibility.blocker() == null ? "none"
                                        : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(visibility.blocker()).getBlock()).toString();
                                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Examine denied requestId={} actor={} epoch={} target={} admission={} state={} range={} eye={} targetPoint={} visibility={} blocker={} blockerId={}", request.requestId(), request.actorUuid(), request.epoch(), request.target(), admission, result.actorState(), result.distance(), result.eye(), result.targetPoint(), visibility == null ? "unavailable" : visibility.reason(), visibility == null ? "none" : visibility.blocker(), blockerId);
                            }
                        } else {
                            long now = player.level().getGameTime();
                            Long previous = LAST_ACCEPTED_INFO.put(player.getUUID(), now);
                            if (previous == null || now - previous >= 20)
                                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Examine requestId={} actor={} epoch={} target={} admission={}", request.requestId(), request.actorUuid(), request.epoch(), request.target(), admission);
                            else com.juicyslew.moonstation14.MoonStation14.LOGGER.debug("Examine requestId={} actor={} epoch={} target={} admission={}", request.requestId(), request.actorUuid(), request.epoch(), request.target(), admission);
                        }
                        ExamineResponse response = result.response();
                        PacketDistributor.sendToPlayer(player, response);
                    });
                })
                .playToClient(ExamineResponse.TYPE, ExamineResponse.STREAM_CODEC, (response, context) -> {
                    Consumer<ExamineResponse> handler = clientResponseHandler;
                    if (handler != null) handler.accept(response);
                });
    }

    /** Client module installs this callback without making common networking load client classes. */
    public static void setClientHandler(Consumer<ExamineResponse> handler) { clientResponseHandler = handler; }

    /** Submit an examination intent; the server authenticates actor identity and epoch. */
    public static void sendRequest(ExamineRequest request) { PacketDistributor.sendToServer(request); }

    public static void send(ServerPlayer player, ExamineResponse response) {
        PacketDistributor.sendToPlayer(player, response);
    }
}

