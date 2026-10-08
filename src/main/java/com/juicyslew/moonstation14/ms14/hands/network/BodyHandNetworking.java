package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Network integration for observer-safe held/worn appearance. */
public final class BodyHandNetworking {
    private BodyHandNetworking() { }
    private static final BodyAppearanceClientCache CLIENT_CACHE = new BodyAppearanceClientCache();
    private static java.util.function.Consumer<BodyAppearanceSnapshot> publicationTestSink;
    public static BodyAppearanceClientCache clientCache() { return CLIENT_CACHE; }
    static void setPublicationTestSink(java.util.function.Consumer<BodyAppearanceSnapshot> sink) { publicationTestSink=sink; }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(BodyAppearanceSnapshot.TYPE, BodyAppearanceSnapshot.STREAM_CODEC, (payload, context) -> {
                    context.enqueueWork(() -> {
                        var player=context.player();
                        if(player!=null) CLIENT_CACHE.accept(payload,player.level().dimension().location());
                    });
                });
        NeoForge.EVENT_BUS.addListener(BodyHandNetworking::startTracking);
    }
    private static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity body && event.getEntity() instanceof ServerPlayer player) sendTo(body, player);
    }
    public static void publish(LivingEntity body, LiveHands state) {
        if (body == null || state == null || body.level().isClientSide || !state.compatible(body) || !BodyHandStateService.representable(state, body)) return;
        var snapshot=BodyAppearanceSnapshot.from(body,state);
        if(snapshot==null) return;
        if(publicationTestSink!=null) { publicationTestSink.accept(snapshot); return; }
        PacketDistributor.sendToPlayersTrackingEntity(body,snapshot);
        for (ServerPlayer player : body.level().getServer().getPlayerList().getPlayers()) {
            var authority=HandActorAuthority.resolve(player).orElse(null);
            if(authority!=null&&authority.body()==body) PacketDistributor.sendToPlayer(player,snapshot);
        }
    }
    private static void sendTo(LivingEntity body, ServerPlayer player) {
        LiveHands state=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var snapshot=BodyAppearanceSnapshot.from(body,state); if(snapshot!=null) PacketDistributor.sendToPlayer(player,snapshot);
    }
}
