package com.juicyslew.moonstation14.ms14.doors.ui;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.BiConsumer;

public final class AirlockWireNetworking {
    private static volatile BiConsumer<AirlockWireSnapshot, IPayloadContext> clientHandler;
    private static volatile BiConsumer<AirlockWireOpen, IPayloadContext> openHandler;
    private static volatile BiConsumer<AirlockWireClosed, IPayloadContext> closeHandler;
    private AirlockWireNetworking() { }
    /** Client intent transport; requests contain only bounded opaque row selectors. */
    public static void send(AirlockWireRequest request) { PacketDistributor.sendToServer(request); }
    public static void requestClose(int windowId, java.util.UUID session) {
        PacketDistributor.sendToServer(new AirlockWireClose(windowId, session));
    }
    public static void installClientHandler(BiConsumer<AirlockWireSnapshot, IPayloadContext> handler) { clientHandler = handler; }
    public static void installClientBridge(BiConsumer<AirlockWireOpen, IPayloadContext> opener,
                                           BiConsumer<AirlockWireSnapshot, IPayloadContext> accepter,
                                           BiConsumer<AirlockWireClosed, IPayloadContext> closer) {
        openHandler=opener; clientHandler=accepter; closeHandler=closer;
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(AirlockWireRequest.TYPE, AirlockWireRequest.STREAM_CODEC, (request, context) -> {
                    if (context.player() instanceof ServerPlayer actor) {
                        var result = handleRequest(actor, request);
                        if (result != null) PacketDistributor.sendToPlayer(actor, result);
                    }
                })
                .playToServer(AirlockWireClose.TYPE, AirlockWireClose.STREAM_CODEC, (close, context) -> {
                    if (context.player() instanceof ServerPlayer actor) AirlockWireMenuService.closeRequest(actor, close);
                })
                .playToClient(AirlockWireOpen.TYPE, AirlockWireOpen.STREAM_CODEC, (open, context) -> {
                    var handler=openHandler; if(handler!=null) handler.accept(open,context);
                })
                .playToClient(AirlockWireSnapshot.TYPE, AirlockWireSnapshot.STREAM_CODEC, (snapshot, context) -> {
                    var handler = clientHandler;
                    if (handler != null) handler.accept(snapshot, context);
                })
                .playToClient(AirlockWireClosed.TYPE, AirlockWireClosed.STREAM_CODEC, (close, context) -> {
                    var handler=closeHandler; if(handler!=null) handler.accept(close,context);
                });
    }
    /** Shared authenticated request owner for transport and package-local GameTest bridge. */
    static AirlockWireSnapshot handleRequest(ServerPlayer actor, AirlockWireRequest request) {
        return AirlockWireMenuService.apply(actor, request);
    }
}
