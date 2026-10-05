package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.server.level.ServerPlayer;
import com.juicyslew.moonstation14.ms14.atmos.piping.FunctionalExposedPipePlacement;
import com.juicyslew.moonstation14.ms14.atmos.piping.FunctionalVentilationDevicePlacement;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.FunctionalAirAlarmPlacement;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenuService;
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
        NeoForge.EVENT_BUS.addListener(BodyWorldGridService::logout);
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(BodyBlockUseRequest.TYPE, BodyBlockUseRequest.STREAM_CODEC, (request, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    var admission = BodyHandRequestService.admitBlockUse(actor, request);
                    BodyBlockUseResult.Outcome outcome = BodyBlockUseResult.Outcome.DENIED;
                    if (admission != null && request.intent() == BodyBlockUseRequest.Intent.USE) {
                        var held = admission.hands().stackCopy(request.handId()).orElse(null);
                        if (held != null && (held.is(ModItems.GAS_VENT.get()) || held.is(ModItems.GAS_SCRUBBER.get()))) {
                            var result = FunctionalVentilationDevicePlacement.place(actor, request, admission);
                            outcome = result == FunctionalVentilationDevicePlacement.Result.INSTALLED ? BodyBlockUseResult.Outcome.PLACED
                                    : result == FunctionalVentilationDevicePlacement.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
                        } else if (held != null && held.is(ModItems.AIR_ALARM.get())) {
                            var result = FunctionalAirAlarmPlacement.place(actor, request, admission);
                            outcome = result == FunctionalAirAlarmPlacement.Result.INSTALLED ? BodyBlockUseResult.Outcome.PLACED
                                    : result == FunctionalAirAlarmPlacement.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
                        } else if (held != null && (held.is(ModItems.GAS_PIPE_SEGMENT.get())
                                || held.is(ModItems.GAS_PIPE_SEGMENT_SECONDARY.get())
                                || held.is(ModItems.GAS_PIPE_SEGMENT_TERTIARY.get()))) {
                            var result = FunctionalExposedPipePlacement.place(actor, request, admission);
                            outcome = result == FunctionalExposedPipePlacement.Result.INSTALLED ? BodyBlockUseResult.Outcome.PLACED
                                    : result == FunctionalExposedPipePlacement.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
                        } else if (held == null) {
                            boolean opened = AirAlarmMenuService.open(actor, request, admission);
                            if (opened) outcome = BodyBlockUseResult.Outcome.NO_CHANGE;
                        } else {
                            outcome = BodyWorldItemActions.use(actor, request, admission);
                        }
                    } else if (admission != null) {
                        outcome = BodyWorldItemActions.activate(actor, request, admission);
                    }
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor)
                        PacketDistributor.sendToPlayer(actor, new BodyBlockUseResult(request.epoch(), request.sequence(), outcome));
                })
                .playToClient(BodyBlockUseResult.TYPE, BodyBlockUseResult.STREAM_CODEC,
                        (result, context) -> { if (FMLEnvironment.dist == Dist.CLIENT) BodyBlockUseResult.deliverClient(result); })
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
                        })
                .playToServer(BodyWorldGridQuery.TYPE, BodyWorldGridQuery.STREAM_CODEC, (query, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    var snapshot = BodyWorldGridService.query(actor, query);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor) PacketDistributor.sendToPlayer(actor, snapshot);
                })
                .playToServer(BodyWorldGridClose.TYPE, BodyWorldGridClose.STREAM_CODEC, (close, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    BodyWorldGridService.close(actor, close);
                })
                .playToServer(BodyWorldGridAction.TYPE, BodyWorldGridAction.STREAM_CODEC, (action, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    var result = BodyWorldGridService.action(actor, action);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor) PacketDistributor.sendToPlayer(actor, result);
                })
                .playToClient(BodyWorldGridActionResult.TYPE, BodyWorldGridActionResult.STREAM_CODEC, (result, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BodyWorldGridActionResult.deliverClient(result);
                })
                .playToServer(BodyWorldCrossAction.TYPE, BodyWorldCrossAction.STREAM_CODEC, (action, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    var result = BodyWorldGridService.cross(actor, action);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor) PacketDistributor.sendToPlayer(actor, result);
                })
                .playToClient(BodyWorldCrossActionResult.TYPE, BodyWorldCrossActionResult.STREAM_CODEC, (result, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BodyWorldCrossActionResult.deliverClient(result);
                })
                .playToServer(BodyWorldWorldCrossAction.TYPE, BodyWorldWorldCrossAction.STREAM_CODEC, (action, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    var result = BodyWorldGridService.worldCross(actor, action);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor) PacketDistributor.sendToPlayer(actor, result);
                })
                .playToClient(BodyWorldWorldCrossActionResult.TYPE, BodyWorldWorldCrossActionResult.STREAM_CODEC,
                        (result, context) -> { if (FMLEnvironment.dist == Dist.CLIENT) BodyWorldWorldCrossActionResult.deliverClient(result); })
                .playToClient(BodyWorldGridSnapshot.TYPE, BodyWorldGridSnapshot.STREAM_CODEC, (snapshot, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BodyWorldGridSnapshot.deliverClient(snapshot);
                });
    }
}
