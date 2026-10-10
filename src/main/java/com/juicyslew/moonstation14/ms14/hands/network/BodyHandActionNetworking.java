package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.server.level.ServerPlayer;
import com.juicyslew.moonstation14.ms14.atmos.piping.FunctionalExposedPipePlacement;
import com.juicyslew.moonstation14.ms14.atmos.piping.FunctionalVentilationDevicePlacement;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.FunctionalAirAlarmPlacement;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenuService;
import com.juicyslew.moonstation14.ms14.containment.DisposalContainedActionPolicy;
import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildMenuService;
import com.juicyslew.moonstation14.ms14.construction.tool.BodyDisposalTubeAnchorUse;
import com.juicyslew.moonstation14.ms14.construction.tool.BodyDisposalTubeJunctionUse;
import com.juicyslew.moonstation14.ms14.construction.tool.BodyDisposalTubeWelderUse;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.unit.FunctionalDisposalUnitPlacement;
import com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitMenuService;
import com.juicyslew.moonstation14.ms14.hands.live.DisposalUnitHandAdapter;
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
                      boolean contained = DisposalContainedActionPolicy.isContained(actor);
                     boolean containedTarget = contained && DisposalContainedActionPolicy.permitsOwnerBlockTarget(actor, request);
                     if (contained && !containedTarget) {
                        PacketDistributor.sendToPlayer(actor, new BodyBlockUseResult(request.epoch(), request.sequence(), BodyBlockUseResult.Outcome.DENIED));
                        return;
                    }
                     var admission = BodyHandRequestService.admitBlockUse(actor, request);
                     if (contained && admission != null
                             && !(admission.authority().body().level().getBlockEntity(admission.hit().pos())
                                  instanceof DisposalUnitBlockEntity)) admission = null;
                    BodyBlockUseResult.Outcome outcome = BodyBlockUseResult.Outcome.DENIED;
                     if (admission != null && request.intent() == BodyBlockUseRequest.Intent.USE) {
                         var held = admission.hands().stackCopy(request.handId()).orElse(null);
                          var body = admission.authority().body();
                          var targetEntity = body.level().getBlockEntity(admission.hit().pos());
                          if (targetEntity instanceof com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity chemMaster) {
                              if (held == null && request.sneaking()) {
                                  var level = (net.minecraft.server.level.ServerLevel) body.level();
                                  var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level);
                                  var slot = chemMaster.inputSlot();
                                  var result = slot == null ? com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.DENIED
                                          : com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.take(
                                                  admission.authority(), request.handId(), request.expectedRevision(), slot,
                                                  slot.snapshot().revision(), ledger.snapshot().revision(), ledger,
                                                  () -> chemMaster.isLoadedValid());
                                  if (result == com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS) {
                                      chemMaster.slotChanged();
                                      com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterAudio.playEject(
                                              (net.minecraft.server.level.ServerLevel) body.level(), chemMaster.getBlockPos(), chemMaster.prototype());
                                      com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.machineChanged(chemMaster);
                                      outcome = BodyBlockUseResult.Outcome.HAND_CHANGED;
                                  } else if (result == com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.RECOVERY_REQUIRED)
                                      outcome = BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
                              } else if (held != null && held.getItem() instanceof com.juicyslew.moonstation14.ms14.chemistry.DispenserCompatibleItem compatible
                                      && chemMaster.isLoadedValid() && compatible.fitsInDispenser(body.level())) {
                                  var level = (net.minecraft.server.level.ServerLevel) body.level();
                                  var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level);
                                  var token = admission.hands().token(request.handId()).orElse(null);
                                  var result = token == null ? com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.DENIED
                                          : com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.insert(
                                                  admission.authority(), request.handId(), token, admission.hands().revision(),
                                                  chemMaster.inputSlot(), chemMaster.inputSlot().snapshot().revision(), ledger.snapshot().revision(), ledger,
                                                  () -> chemMaster.isLoadedValid());
                                  if (result == com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS) {
                                      chemMaster.slotChanged();
                                      com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterAudio.playInsert(
                                              (net.minecraft.server.level.ServerLevel) body.level(), chemMaster.getBlockPos(), chemMaster.prototype());
                                      com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.machineChanged(chemMaster);
                                      outcome = BodyBlockUseResult.Outcome.PLACED;
                                  } else if (result == com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.RECOVERY_REQUIRED)
                                      outcome = BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
                              } else if (com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.opensWorkspace(
                                      request.intent(), held == null, request.sneaking())
                                      && com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.open(actor, chemMaster))
                                  outcome = BodyBlockUseResult.Outcome.NO_CHANGE;
                          } else if (held != null && targetEntity instanceof DisposalUnitBlockEntity unit) {
                             boolean inserted = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(actor, admission.authority())
                                     && DisposalUnitHandAdapter.insert(body, request.handId(), request.expectedRevision(), unit);
                             outcome = inserted ? BodyBlockUseResult.Outcome.PLACED : BodyBlockUseResult.Outcome.DENIED;
                           } else if (held != null && held.is(ModItems.WRENCH.get())) {
                               outcome = BodyDisposalTubeAnchorUse.begin(actor, request, admission)
                                       ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyWorldItemActions.use(actor, request, admission);
                           } else if (held != null && held.is(ModItems.SCREWDRIVER.get())) {
                               outcome = BodyDisposalTubeJunctionUse.begin(actor, request, admission)
                                       ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyWorldItemActions.use(actor, request, admission);
                           } else if (held != null && held.is(ModItems.WELDER.get())) {
                               outcome = BodyDisposalTubeWelderUse.begin(actor, request, admission)
                                       ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyWorldItemActions.use(actor, request, admission);
                          } else if (held != null && held.is(ModItems.DISPOSAL_UNIT.get())) {
                             var result = FunctionalDisposalUnitPlacement.place(actor, request, admission);
                             outcome = result == FunctionalDisposalUnitPlacement.Result.INSTALLED ? BodyBlockUseResult.Outcome.PLACED
                                     : result == FunctionalDisposalUnitPlacement.Result.RECOVERY_REQUIRED ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
                         } else if (held != null && (held.is(ModItems.GAS_VENT.get()) || held.is(ModItems.GAS_SCRUBBER.get()))) {
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
                          } else if (held != null && DisposalBuildMenuService.matchesActiveMaterial(admission.authority(), held)) {
                                 String sharedRecipe=com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldService
                                         .selectedRecipe(actor,body);
                                 if(sharedRecipe!=null) {
                                     outcome=BodyWorldItemActions.use(actor,request,admission);
                                 } else if (com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldService
                                         .hasConstructionHost((net.minecraft.server.level.ServerLevel) body.level(), admission.hit().pos())) {
                                    outcome = BodyWorldItemActions.use(actor, request, admission);
                                } else {
                                    long beforeRevision = request.expectedRevision();
                                    boolean handled = DisposalBuildMenuService.useSelected(actor, request, admission);
                                    var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                    outcome = handled
                                            ? BodyConstructionOutcomePolicy.outcome(true, beforeRevision,
                                                    liveHands == null ? null : liveHands.revision())
                                            : BodyWorldItemActions.use(actor, request, admission);
                                }
                        } else if (held == null) {
                            boolean opened = AirAlarmMenuService.open(actor, request, admission);
                            if (opened) outcome = BodyBlockUseResult.Outcome.NO_CHANGE;
                        } else {
                            outcome = BodyWorldItemActions.use(actor, request, admission);
                        }
                      } else if (admission != null) {
                        var targetEntity = admission.authority().body().level().getBlockEntity(admission.hit().pos());
                        var held = admission.hands().stackCopy(request.handId()).orElse(null);
                          if (request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD && request.sneaking() && held == null
                                  && targetEntity instanceof com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity chemMaster) {
                              var body = admission.authority().body();
                              var level = (net.minecraft.server.level.ServerLevel) body.level();
                              var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level);
                              var slot = chemMaster.inputSlot();
                              var result = slot == null ? com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.DENIED
                                      : com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.take(
                                              admission.authority(), request.handId(), request.expectedRevision(), slot,
                                              slot.snapshot().revision(), ledger.snapshot().revision(), ledger,
                                              () -> chemMaster.isLoadedValid());
                              if (result == com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS) {
                                  chemMaster.slotChanged();
                                  com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterAudio.playEject(level,
                                          chemMaster.getBlockPos(), chemMaster.prototype());
                                  com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.machineChanged(chemMaster);
                                  outcome = BodyBlockUseResult.Outcome.HAND_CHANGED;
                              } else if (result == com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.RECOVERY_REQUIRED)
                                  outcome = BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
                          } else if (com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.opensWorkspace(
                                  request.intent(), held == null, request.sneaking())
                                  && targetEntity instanceof com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity chemMaster
                                  && com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.open(actor, chemMaster))
                              outcome = BodyBlockUseResult.Outcome.NO_CHANGE;
                          else if (request.intent() == BodyBlockUseRequest.Intent.CLIMB_INSIDE
                                 && targetEntity instanceof DisposalUnitBlockEntity unit
                                 && DisposalUnitMenuService.beginDirect(actor, unit, admission.authority()))
                             outcome = BodyBlockUseResult.Outcome.NO_CHANGE;
                         else if (request.intent() == BodyBlockUseRequest.Intent.CLIMB_INSIDE)
                             outcome = BodyBlockUseResult.Outcome.DENIED;
                         else if (request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                                && targetEntity instanceof DisposalUnitBlockEntity unit
                                && DisposalUnitMenuService.open(actor, unit, admission.authority()))
                            outcome = BodyBlockUseResult.Outcome.NO_CHANGE;
                        else if (request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                                && targetEntity instanceof DisposalUnitBlockEntity)
                            outcome = BodyBlockUseResult.Outcome.DENIED;
                        else if (request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD
                                && DisposalBuildMenuService.openEdit(actor, request, admission))
                            outcome = BodyBlockUseResult.Outcome.NO_CHANGE;
                        else outcome = BodyWorldItemActions.activate(actor, request, admission);
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
                     if (DisposalContainedActionPolicy.isContained(actor)
                             && !DisposalContainedActionPolicy.permitsBodyHandAction(request.action())) {
                        PacketDistributor.sendToPlayer(actor, new BodyHandActionResult(request.epoch(), request.sequence(), false,
                                BodyHandActionResult.Reason.DENIED, false, 0, null, null, null, 0));
                        return;
                    }
                     java.util.UUID scannerBefore = null;
                     if (request.action() == BodyHandActionRequest.Action.DROP
                             || request.action() == BodyHandActionRequest.Action.PICKUP
                             || request.action() == BodyHandActionRequest.Action.PICKUP_SELECTED) {
                         var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(actor).orElse(null);
                         var live = authority == null ? null : authority.body().getExistingDataOrNull(
                                 com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                         var held = live == null ? null : live.stackCopy(request.handId()).orElse(null);
                         var state = held == null ? null : held.get(com.juicyslew.moonstation14.component.ModDataComponents.HEALTH_ANALYZER.get());
                         if (state != null) scannerBefore = state.scannerId();
                     }
                     BodyHandActionResult result = BodyHandRequestService.apply(actor, request);
                     if (scannerBefore == null && result.accepted() && result.hostTransition() != null
                             && result.hostTransition().direction() == BodyHandActionResult.HostTransition.Direction.PICKUP) {
                         var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(actor).orElse(null);
                         var live = authority == null ? null : authority.body().getExistingDataOrNull(
                                 com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                         var held = live == null ? null : live.stackCopy(request.handId()).orElse(null);
                         var state = held == null ? null : held.get(com.juicyslew.moonstation14.component.ModDataComponents.HEALTH_ANALYZER.get());
                         if (state != null) scannerBefore = state.scannerId();
                     }
                      if (scannerBefore != null && result.accepted() && result.hostTransition() != null)
                          com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerServer.onWorldTransfer(actor,
                                  scannerBefore, result.hostTransition().direction() == BodyHandActionResult.HostTransition.Direction.DROP
                                          ? result.hostTransition().worldEntity() : null);
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
                    if (DisposalContainedActionPolicy.isContained(actor)) return;
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
                    if (DisposalContainedActionPolicy.deniesExternalMutation(actor)) return;
                    var result = BodyWorldGridService.action(actor, action);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor) PacketDistributor.sendToPlayer(actor, result);
                })
                .playToClient(BodyWorldGridActionResult.TYPE, BodyWorldGridActionResult.STREAM_CODEC, (result, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BodyWorldGridActionResult.deliverClient(result);
                })
                .playToServer(BodyWorldCrossAction.TYPE, BodyWorldCrossAction.STREAM_CODEC, (action, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    if (DisposalContainedActionPolicy.deniesExternalMutation(actor)) return;
                    var result = BodyWorldGridService.cross(actor, action);
                    if (actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor) PacketDistributor.sendToPlayer(actor, result);
                })
                .playToClient(BodyWorldCrossActionResult.TYPE, BodyWorldCrossActionResult.STREAM_CODEC, (result, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BodyWorldCrossActionResult.deliverClient(result);
                })
                .playToServer(BodyWorldWorldCrossAction.TYPE, BodyWorldWorldCrossAction.STREAM_CODEC, (action, context) -> {
                    if (!(context.player() instanceof ServerPlayer actor) || actor.connection == null) return;
                    if (actor.getServer() == null || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                    if (DisposalContainedActionPolicy.deniesExternalMutation(actor)) return;
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
