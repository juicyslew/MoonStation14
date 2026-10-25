package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class CombatModeNetworking {
    private static java.util.function.Consumer<MeleeAttackReply> meleeReplyHandler = reply -> { };
    private static java.util.function.Consumer<MeleeArcPayload> meleeArcHandler = payload -> { };
    private static java.util.function.Consumer<GunShotReply> gunShotReplyHandler = reply -> { };
    private static java.util.function.Consumer<GunMuzzleFlashPayload> gunMuzzleFlashHandler = payload -> { };
    private CombatModeNetworking() { }
    public static void installMeleeReplyHandler(java.util.function.Consumer<MeleeAttackReply> handler) {
        meleeReplyHandler = java.util.Objects.requireNonNull(handler);
    }
    public static void installMeleeArcHandler(java.util.function.Consumer<MeleeArcPayload> handler) { meleeArcHandler = java.util.Objects.requireNonNull(handler); }
    public static void installGunShotReplyHandler(java.util.function.Consumer<GunShotReply> handler) { gunShotReplyHandler = java.util.Objects.requireNonNull(handler); }
    public static void installGunMuzzleFlashHandler(java.util.function.Consumer<GunMuzzleFlashPayload> handler) { gunMuzzleFlashHandler = java.util.Objects.requireNonNull(handler); }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> CombatModeRequestService.logout(e));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> MeleeAttackRequestService.logout(e));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> GunShotRequestService.logout(e));
        event.registrar("1").executesOn(HandlerThread.MAIN).playToClient(CombatModeRequestResult.TYPE, CombatModeRequestResult.STREAM_CODEC, (result, context) -> CombatModeClient.handleReply(result)).playToServer(CombatModeRequest.TYPE, CombatModeRequest.STREAM_CODEC, (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || player.connection == null || player.getServer() == null
                    || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            context.reply(CombatModeRequestService.handle(player, request));
        });
        event.registrar("1").executesOn(HandlerThread.MAIN).playToClient(MeleeAttackReply.TYPE,MeleeAttackReply.STREAM_CODEC,(reply,context)->meleeReplyHandler.accept(reply)).playToServer(MeleeAttackRequest.TYPE,MeleeAttackRequest.STREAM_CODEC,(request,context)->{
            if (!(context.player() instanceof ServerPlayer player) || player.connection == null || player.getServer() == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            context.reply(MeleeAttackRequestService.handle(player,request));
        });
        event.registrar("1").executesOn(HandlerThread.MAIN).playToClient(MeleeArcPayload.TYPE, MeleeArcPayload.STREAM_CODEC, (payload, context) -> meleeArcHandler.accept(payload));
        event.registrar("1").executesOn(HandlerThread.MAIN).playToClient(GunMuzzleFlashPayload.TYPE,GunMuzzleFlashPayload.STREAM_CODEC,(payload,context)->gunMuzzleFlashHandler.accept(payload));
        event.registrar("1").executesOn(HandlerThread.MAIN).playToClient(GunShotReply.TYPE,GunShotReply.STREAM_CODEC,(reply,context)->gunShotReplyHandler.accept(reply)).playToServer(GunShotRequest.TYPE,GunShotRequest.STREAM_CODEC,(request,context)->{
            if (!(context.player() instanceof ServerPlayer player) || player.connection == null || player.getServer() == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            context.reply(GunShotRequestService.handle(player,request));
        });
    }
}
