package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.WirecutterItem;
import com.juicyslew.moonstation14.item.custom.CableSpoolItem;
import com.juicyslew.moonstation14.item.custom.CrowbarItem;
import com.juicyslew.moonstation14.item.custom.AtmosphereAnalyzerItem;
import com.juicyslew.moonstation14.item.custom.SteelItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTileItem;
import com.juicyslew.moonstation14.item.custom.StationFloorTilePryItem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyBlockItemPlacement;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGrenadePrime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Extensible authoritative dispatcher for world item interactions. */
public final class BodyWorldItemActions {
    private BodyWorldItemActions() { }

    private record ConfiguratorProof(com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
                                    String token, long revision,
                                    com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData data) { }

    /** Shared fresh-proof initialization used by both block-use and nominated loose-item use. */
    private static ConfiguratorProof configuratorProof(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands, String handId, String token, long revision) {
        var body = authority.body();
        var type = com.juicyslew.moonstation14.component.ModDataComponents.NETWORK_CONFIGURATOR.get();
        ItemStack stack = hands.stackCopy(handId).orElse(ItemStack.EMPTY);
        if (stack.get(type) == null) {
            ItemStack initialized = stack.copy();
            initialized.set(type, com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData.create());
            if (com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.commit(actor, authority, hands,
                    handId, token, revision, stack, initialized)
                    != com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result.SUCCESS) return null;
            hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !HandActorAuthority.revalidate(actor, authority)
                    || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || !hands.compatible(body)) return null;
            token = hands.token(handId).orElse(null); revision = hands.revision();
            if (token == null) return null;
            BodyHandStateService.publishCommitted(actor, authority);
        }
        var data = hands.stackCopy(handId).orElse(ItemStack.EMPTY).get(type);
        return data == null ? null : new ConfiguratorProof(hands, token, revision, data);
    }

    /** Authenticated USE_TARGET adapter for the exact loose item nominated by the client. */
    public static BodyBlockUseResult.Outcome useLooseConfigurator(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands, BodyHandActionRequest request,
            com.juicyslew.moonstation14.entities.LooseItemEntity loose) {
        if (!HandActorAuthority.revalidate(actor, authority) || !hands.compatible(authority.body())
                || hands.revision() != request.expectedRevision()
                || !expectedTokenMatches(request.expectedToken(), hands.token(request.handId()))
                || loose.isRemoved() || loose.level() != authority.body().level()
                || !com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(
                        hands.stackCopy(request.handId()).orElse(ItemStack.EMPTY))) return BodyBlockUseResult.Outcome.DENIED;
        var proof = configuratorProof(actor, authority, hands, request.handId(), request.expectedToken(), request.expectedRevision());
        if (proof == null || !com.juicyslew.moonstation14.ms14.devicenet.item.RemoteSignallerServer.prepareLooseEndpoint(loose))
            return BodyBlockUseResult.Outcome.DENIED;
        hands = proof.hands(); String token = proof.token(); long revision = proof.revision();
        var data = proof.data();
        var signaller = loose.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REMOTE_SIGNALLER.get());
        if (signaller == null) return BodyBlockUseResult.Outcome.DENIED;
        boolean list = data.mode() == com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData.Mode.LIST;
        boolean selected = list
                ? com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.selectOrStageAdmittedDevice(actor, authority, request.handId(), token, revision, signaller.identity())
                : com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectAdmittedDevice(actor, authority, request.handId(), token, revision, signaller.identity());
        if (!selected) return BodyBlockUseResult.Outcome.DENIED;
        if (list) com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorNetworking.sendListSnapshot(actor,
                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.currentSnapshot(actor));
        else {
            com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorNetworking.sendSnapshot(actor,
                    com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.currentSnapshot(actor));
            actor.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectionFeedback(actor)), true);
        }
        return list ? BodyBlockUseResult.Outcome.HAND_CHANGED : BodyBlockUseResult.Outcome.NO_CHANGE;
    }

    public static BodyBlockUseResult.Outcome use(ServerPlayer actor, BodyBlockUseRequest request,
                                                   BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null
                || request.intent() != BodyBlockUseRequest.Intent.USE) return BodyBlockUseResult.Outcome.DENIED;
        var authority = admission.authority();
        var body = authority.body();
        var hands = admission.hands();
        if (!HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(body) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || !expectedTokenMatches(request.expectedToken(), hands.token(request.handId()))
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.hasChunkAt(admission.hit().pos())
                || !level.getWorldBorder().isWithinBounds(admission.hit().pos())
                || !level.mayInteract(actor, admission.hit().pos())) return BodyBlockUseResult.Outcome.DENIED;
        ItemStack stack = hands.stackCopy(request.handId()).orElse(null);
        if (stack == null || stack.isEmpty()) return BodyBlockUseResult.Outcome.DENIED;
        if (body.level().getBlockEntity(admission.hit().pos()) instanceof com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity collector)
            return com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorInteractionService.use(actor, request, admission, collector);
        if (com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.isConfigurator(stack)) {
            var dataType = com.juicyslew.moonstation14.component.ModDataComponents.NETWORK_CONFIGURATOR.get();
            var configurator = stack.get(dataType);
            String toolToken = request.expectedToken();
            long toolRevision = request.expectedRevision();
            var proof = configuratorProof(actor, authority, hands, request.handId(), toolToken, toolRevision);
            if (proof == null) return BodyBlockUseResult.Outcome.DENIED;
            hands = proof.hands(); toolToken = proof.token(); toolRevision = proof.revision();
            var data = proof.data();
            if (data.mode() == com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData.Mode.LIST)
                actor.displayClientMessage(net.minecraft.network.chat.Component.literal("Network Configurator: LIST mode"), true);
            long revision = toolRevision;
            var visible = com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.visibleCandidates(
                    level, body, com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH);
            if (!visible.isEmpty()) {
                var entity = level.getEntity(visible.getFirst().itemId());
                if (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose
                        && com.juicyslew.moonstation14.ms14.devicenet.item.RemoteSignallerServer.prepareLooseEndpoint(loose)) {
                    var signaller = loose.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REMOTE_SIGNALLER.get());
                    boolean selected = signaller != null && (data.mode() == com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData.Mode.LIST
                            ? com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.selectOrStageAdmittedDevice(
                                    actor, authority, request.handId(), toolToken, revision, signaller.identity())
                            : com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectAdmittedDevice(
                                    actor, authority, request.handId(), toolToken, revision, signaller.identity()));
                    if (selected) {
                        if (data.mode() == com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData.Mode.LIST) {
                            com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorNetworking.sendListSnapshot(actor,
                                    com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.currentSnapshot(actor));
                            return BodyBlockUseResult.Outcome.HAND_CHANGED;
                        }
                        com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorNetworking.sendSnapshot(actor,
                                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.currentSnapshot(actor));
                        actor.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectionFeedback(actor)), true);
                        return BodyBlockUseResult.Outcome.NO_CHANGE;
                    }
                }
            }
            if (data.mode() == com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData.Mode.LIST) {
                var endpoint=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level).transport().lookupAt(admission.hit().pos());
                if(endpoint.size()==1 && endpoint.getFirst().deviceList()!=null) {
                    var session=com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.openList(actor,authority,request.handId(),toolToken,revision);
                    if(session==null || !com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.openConfigure(actor,session,endpoint.getFirst().identity())) return BodyBlockUseResult.Outcome.DENIED;
                } else if(!com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.selectOrStageEndpoint(actor,authority,request.handId(),toolToken,revision,admission.hit().pos())) return BodyBlockUseResult.Outcome.DENIED;
                else return BodyBlockUseResult.Outcome.HAND_CHANGED;
                com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorNetworking.sendListSnapshot(actor,com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorListServer.currentSnapshot(actor));
                return BodyBlockUseResult.Outcome.HAND_CHANGED;
            }
            if (!com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectTarget(
                    actor, authority, request.handId(), toolToken, revision, admission.hit().pos())) {
                actor.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Network Configurator: no linkable source/receiver selected; check reach and endpoint roles"), true);
                return BodyBlockUseResult.Outcome.DENIED;
            }
            com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorNetworking.sendSnapshot(actor,com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.currentSnapshot(actor));
            actor.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorServer.selectionFeedback(actor)), true);
            return BodyBlockUseResult.Outcome.NO_CHANGE;
        }
        if (com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireMenuService
                .dispatchToolMenu(actor, request, admission, stack)) return BodyBlockUseResult.Outcome.NO_CHANGE;
        if (com.juicyslew.moonstation14.ms14.doors.AirlockToolInteraction.use(actor, request, admission, stack))
            return BodyBlockUseResult.Outcome.NO_CHANGE;
        if (com.juicyslew.moonstation14.ms14.doors.AirlockPryService.start(actor, request, admission, stack))
            return BodyBlockUseResult.Outcome.NO_CHANGE;
        if (stack.is(ModItems.GRENADE.get())) {
            BodyGrenadePrime.Result primed = BodyGrenadePrime.prime(actor, request.handId(),
                    request.expectedToken(), request.expectedRevision());
            if (primed == BodyGrenadePrime.Result.SUCCESS) {
                BodyHandStateService.requireFreshQuery(actor);
                return BodyBlockUseResult.Outcome.HAND_CHANGED;
            }
            return primed == BodyGrenadePrime.Result.RECOVERY_REQUIRED
                    ? BodyBlockUseResult.Outcome.RECOVERY_REQUIRED : BodyBlockUseResult.Outcome.DENIED;
        }
        // The specialized adapters are dispatched only after the authenticated body-hand admission.
        // Mutation is intentionally delegated to body-safe item cores, never a fabricated UseOnContext.
        if (stack.getItem() instanceof CableSpoolItem || stack.getItem() instanceof WirecutterItem
                || stack.getItem() instanceof CrowbarItem || stack.getItem() instanceof SteelItem
                || stack.getItem() instanceof StationFloorTileItem || stack.getItem() instanceof StationFloorTilePryItem
                || stack.is(ModItems.PLASTIC) || stack.is(ModItems.SCREWDRIVER) || stack.is(ModItems.WELDER)
                || stack.is(ModItems.WRENCH))
            return BodyConstructionItemUse.use(new BodyUseItemContext(actor, authority, hands, request.handId(),
                    request.expectedToken(), request.expectedRevision(), stack, admission.hit().pos(), admission.hit().face()));
        if (BodyBlockItemPlacement.supports(stack)) return BodyBlockItemPlacement.place(actor, request, admission);
        if (stack.getItem() instanceof AtmosphereAnalyzerItem) {
            AtmosphereAnalyzerItem.reportForBody(level, actor, body, admission.hit().pos(), admission.hit().face());
            return BodyBlockUseResult.Outcome.NO_CHANGE;
        }
        return BodyBlockUseResult.Outcome.DENIED;
    }

    public static BodyBlockUseResult.Outcome activate(ServerPlayer actor, BodyBlockUseRequest request,
                                                       BodyHandRequestService.BlockUseAdmission admission) {
        if (actor != null && request != null && admission != null
                && request.intent() == BodyBlockUseRequest.Intent.ACTIVATE_WORLD) {
            var target=admission.authority().body().level().getBlockEntity(admission.hit().pos());
            var held=admission.hands().stackCopy(request.handId()).orElse(null);
            // Authenticated empty hands are represented by no occupant/token; a missing stack
            // is accepted here only after the shared block-use admission has validated that proof.
            boolean emptyHand = held == null;
            if (emptyHand
                    && target instanceof com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity collector)
                return collector.toggleEnabled() ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyBlockUseResult.Outcome.DENIED;
            if(emptyHand&&target instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity pa
                    && pa.role().equals("control")) {
                var snapshot=com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlSessionService
                        .open(actor,pa,admission.authority(),request.sequence()).orElse(null);
                if(snapshot!=null){com.juicyslew.moonstation14.ms14.singularity.pa.control.PaControlNetworking.sendOpenSnapshot(actor,snapshot);
                    return BodyBlockUseResult.Outcome.NO_CHANGE;}
                return BodyBlockUseResult.Outcome.DENIED;
            }
            if (emptyHand && target instanceof com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity emitter) {
                boolean before = emitter.requestedOn();
                emitter.toggleRequestedOn();
                return emitter.requestedOn() != before ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyBlockUseResult.Outcome.DENIED;
            }
            if (emptyHand && target instanceof com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity field) {
                boolean changed=field.toggleEnabled();
                if (!changed) actor.displayClientMessage(field.toggleFeedback(false),true);
                return changed ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyBlockUseResult.Outcome.DENIED;
            }
            if (com.juicyslew.moonstation14.ms14.doors.AirlockInteraction.activate(request, admission))
                return BodyBlockUseResult.Outcome.NO_CHANGE;
            if (com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenuService
                    .open(actor, request, admission)) return BodyBlockUseResult.Outcome.NO_CHANGE;
            var level=admission.authority().body().level();
            if(level instanceof net.minecraft.server.level.ServerLevel server
                    && server.getBlockEntity(admission.hit().pos()) instanceof com.juicyslew.moonstation14.ms14.devicenet.control.WallSignalControlBlockEntity control
                    && control.activate(actor)) return BodyBlockUseResult.Outcome.NO_CHANGE;
            return com.juicyslew.moonstation14.ms14.power.ui.ApcMenuService.openForBody(actor, request, admission)
                    ? BodyBlockUseResult.Outcome.NO_CHANGE : BodyBlockUseResult.Outcome.DENIED;
        }
        return BodyBlockUseResult.Outcome.DENIED;
    }

    static boolean expectedTokenMatches(String expectedToken, java.util.Optional<String> actualToken) {
        return expectedToken != null && actualToken.filter(expectedToken::equals).isPresent();
    }
}

