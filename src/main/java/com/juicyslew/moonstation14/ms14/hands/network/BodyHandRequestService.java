package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyEquippedStorageTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldGridTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyBodyGridTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandTransfer;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGrenadePrime;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGrenadeThrow;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Main-thread-only request gate; neither a menu nor a carrier-inventory route. */
public final class BodyHandRequestService {
    private static final Map<ServerPlayer, SequenceGate> GATES = new IdentityHashMap<>();
    private static final Map<ServerPlayer, SequenceGate> BLOCK_USE_GATES = new IdentityHashMap<>();

    private BodyHandRequestService() { }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { GATES.remove(player); BLOCK_USE_GATES.remove(player); }
    }

    /** Read-only admission only: deliberately does not invoke block use, item use, or protection callbacks. */
    public static boolean probeBlockUse(ServerPlayer actor, BodyBlockUseRequest request) {
        return admitBlockUse(actor, request) != null;
    }

    /** Package-private authenticated read-only admission snapshot for narrowly scoped dispatchers. */
    public static BlockUseAdmission admitBlockUse(ServerPlayer actor, BodyBlockUseRequest request) {
        if (request == null) return null;
        var resolved = HandActorAuthority.resolve(actor);
        if (resolved.isEmpty()) return null;
        var authority = resolved.orElseThrow();
        if (!BodyHandStateService.hasIssued(actor, authority)) return null;
        var gate = BLOCK_USE_GATES.computeIfAbsent(actor, ignored -> new SequenceGate());
        if (gate.admit(authority.epoch(), request.epoch(), request.sequence(), actor.level().getGameTime()) != null) return null;
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !request.handId().equals(hands.activeHand())
                || !hands.handIds().contains(request.handId()) || !authority.handIds().contains(request.handId())
                || !hands.compatible(authority.body()) || hands.revision() != request.expectedRevision()) return null;
        var token = hands.token(request.handId());
        var stack = hands.stackCopy(request.handId());
        if (!blockUseHandStateMatches(request.expectedToken(), token.isPresent(), stack.isPresent(), token.orElse(null)))
            return null;
        if (!(authority.body().level() instanceof net.minecraft.server.level.ServerLevel level)
                || !HandActorAuthority.revalidate(actor, authority)) return null;
        // No reliable controlled-body crouch authority is currently defined. The hint is intentionally ignored.
        var hit = BodyBlockUseTargetProbe.probe(level, authority.body(), request.target(), request.face());
        return hit != null && HandActorAuthority.revalidate(actor, authority)
                && authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && hands.revision() == request.expectedRevision()
                ? new BlockUseAdmission(authority, hands, hit) : null;
    }

    static boolean blockUseHandStateMatches(String expectedToken, boolean tokenPresent, boolean stackPresent,
                                            String actualToken) {
        return expectedToken == null ? !tokenPresent && !stackPresent
                : tokenPresent && stackPresent && expectedToken.equals(actualToken);
    }

    public record BlockUseAdmission(HandActorAuthority.Snapshot authority, LiveHands hands,
                                   BodyBlockUseTargetProbe.Target hit) { }

    public static BodyHandActionResult apply(ServerPlayer actor, BodyHandActionRequest request) {
        // resolve includes exact player-list identity, main thread, non-fake actor, marker and clean carrier.
        var resolved = HandActorAuthority.resolve(actor);
        if (resolved.isEmpty()) return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
        var authority = resolved.orElseThrow();
        if (!BodyHandStateService.hasIssued(actor, authority))
            return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
        SequenceGate gate = GATES.computeIfAbsent(actor, ignored -> new SequenceGate());
        BodyHandActionResult.Reason admission = gate.admit(authority.epoch(), request.epoch(), request.sequence(),
                actor.level().getGameTime());
        if (admission != null) return result(request, admission,
                admission == BodyHandActionResult.Reason.WRONG_EPOCH ? null : authority);

        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body()) || !hands.handIds().contains(request.handId())
                 || !authority.handIds().contains(request.handId()) || hands.revision() != request.expectedRevision())
            return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);

        if (request.action() == BodyHandActionRequest.Action.SIP_PUDDLE) {
            return result(request, BodyTimedIngestionService.startPuddle(actor, request.targetBlock(),
                    request.handId(), request.expectedRevision(), request.sequence()), authority);
        }

        if (storageMutation(request.action()) && ModItemFootprints.serverCatalog().size() == 0)
            return result(request, BodyHandActionResult.Reason.DENIED, authority);

        if (request.action() == BodyHandActionRequest.Action.SELECT_HAND) {
            var next = hands.selectActive(request.expectedRevision(), request.handId());
            if (authority.handIds().size() != 2 || !hands.handIds().equals(authority.handIds())
                    || next.isEmpty()) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            if (!HandActorAuthority.revalidate(actor, authority)
                    || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || hands.revision() != request.expectedRevision())
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            if (!selectionAllowed(authority.body(), hands, next.orElseThrow(), request.expectedRevision()))
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            BodyHandActionResult.Reason reason;
            try {
                if (!com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway.commit(actor, authority, next.orElseThrow()))
                    throw new IllegalStateException("hand commit rejected");
                reason = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                        && next.orElseThrow().compatible(authority.body())
                        ? BodyHandActionResult.Reason.OK : BodyHandActionResult.Reason.RECOVERY_REQUIRED;
            } catch (RuntimeException failure) {
                reason = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                        && hands.compatible(authority.body())
                        ? BodyHandActionResult.Reason.DENIED : BodyHandActionResult.Reason.RECOVERY_REQUIRED;
            }
            BodyHandActionResult response = result(request, reason, authority);
            if (reason == BodyHandActionResult.Reason.OK) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }

        if (request.action() == BodyHandActionRequest.Action.PICKUP) {
            if (!HandActorAuthority.revalidate(actor, authority))
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            java.util.concurrent.atomic.AtomicReference<java.util.UUID> source = new java.util.concurrent.atomic.AtomicReference<>();
            BodyHandItemTransfer.Result outcome = BodyHandItemTransfer.pickup(actor, request.pickupTarget(),
                    request.handId(), request.expectedRevision(), source::set);
            BodyHandActionResult.Reason pickupReason = reason(outcome);
            BodyHandActionResult.HostTransition transition = null;
            if (pickupReason == BodyHandActionResult.Reason.OK) {
                LiveHands committedHands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                var committedTransition = pickupTransition(committedHands, source.get(), request.handId(),
                        HandActorAuthority.revalidate(actor, authority), authority.body());
                if (committedTransition.isEmpty())
                    pickupReason = BodyHandActionResult.Reason.RECOVERY_REQUIRED;
                else transition = committedTransition.orElseThrow();
            }
            return result(request, pickupReason, authority, transition);
        }

        if (request.action() == BodyHandActionRequest.Action.TRANSFER_HAND) {
            if (authority.handIds().size() != 2 || !hands.handIds().equals(authority.handIds())
                    || !authority.handIds().contains(request.destinationHostId()))
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            if (!HandActorAuthority.revalidate(actor, authority)
                    || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || hands.revision() != request.expectedRevision())
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            var outcome = BodyHandTransfer.transfer(actor, request.handId(), request.destinationHostId(),
                    request.expectedToken(), request.expectedRevision());
            BodyHandActionResult response = result(request, switch (outcome) {
                case SUCCESS -> BodyHandActionResult.Reason.OK;
                case DENIED -> BodyHandActionResult.Reason.DENIED;
                case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
            }, authority);
            if (outcome != BodyHandTransfer.Result.DENIED) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }

        if (request.action() == BodyHandActionRequest.Action.HELD_GRID_STORE
                || request.action() == BodyHandActionRequest.Action.HELD_GRID_TAKE
                || request.action() == BodyHandActionRequest.Action.HELD_GRID_MOVE) {
            if (authority.handIds().size() != 2 || !hands.handIds().equals(authority.handIds()))
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var otherHands = authority.handIds().stream().filter(id -> !id.equals(request.handId())).toList();
            if (otherHands.size() != 1) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            String otherHand = otherHands.getFirst();
            String hostToken = hands.token(request.handId()).orElse(null);
            String otherToken = hands.token(otherHand).orElse(null);
            var host = hands.stackCopy(request.handId()).orElse(null);
            var contents = host == null ? null : GridContents.read(host).orElse(null);
            boolean storing = request.action() == BodyHandActionRequest.Action.HELD_GRID_STORE;
            boolean moving = request.action() == BodyHandActionRequest.Action.HELD_GRID_MOVE;
            if (hostToken == null || !hostToken.equals(request.expectedToken()) || host == null
                    || !(host.is(ModItems.BAG) || host.is(ModItems.BELT)) || contents == null
                    || storing && (!request.expectedChildOrSourceToken().equals(otherToken))
                    || !storing && !request.expectedChildOrSourceToken().equals(moving
                        ? contents.entries().stream().filter(e -> e.token().equals(request.expectedChildOrSourceToken())
                            && e.x() == request.oldCellX() && e.y() == request.oldCellY()
                            && e.rotation().degrees() == request.oldRotation()).map(GridContents.Entry::token).findFirst().orElse(null)
                        : contents.entries().stream().filter(e -> e.token().equals(request.expectedChildOrSourceToken())
                            && e.x() == request.cellX() && e.y() == request.cellY()).map(GridContents.Entry::token).findFirst().orElse(null))
                    || !storing && !moving && otherToken != null)
                return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            if (!HandActorAuthority.revalidate(actor, authority)
                    || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || hands.revision() != request.expectedRevision())
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            var rotation = storing || moving ? java.util.Arrays.stream(GridGeometry.Rotation.values())
                    .filter(r -> r.degrees() == request.rotation()).findFirst().orElseThrow() : null;
            BodyHeldGridTransfer.Result outcome = storing
                    ? BodyHeldGridTransfer.store(actor, request.handId(), hostToken, otherHand,
                        request.expectedChildOrSourceToken(), request.expectedRevision(),
                        new GridGeometry.Cell(request.cellX(), request.cellY()), rotation)
                    : moving ? BodyHeldGridTransfer.move(actor, request.handId(), hostToken,
                        request.expectedChildOrSourceToken(), request.expectedRevision(),
                        new GridGeometry.Cell(request.oldCellX(), request.oldCellY()),
                        java.util.Arrays.stream(GridGeometry.Rotation.values()).filter(r -> r.degrees() == request.oldRotation()).findFirst().orElseThrow(),
                        new GridGeometry.Cell(request.cellX(), request.cellY()), rotation)
                    : BodyHeldGridTransfer.take(actor, request.handId(), hostToken, otherHand,
                        request.expectedChildOrSourceToken(), request.expectedRevision(),
                        new GridGeometry.Cell(request.cellX(), request.cellY()));
            BodyHandActionResult response = result(request, reason(outcome), authority);
            if (outcome == BodyHeldGridTransfer.Result.SUCCESS) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }

         if (request.action() == BodyHandActionRequest.Action.GRID_BODY_WORN_TO_HELD
                 || request.action() == BodyHandActionRequest.Action.GRID_BODY_HELD_TO_WORN
                 || request.action() == BodyHandActionRequest.Action.GRID_BODY_HELD_TO_HELD) {
             if (!hands.handIds().equals(authority.handIds()))
                 return result(request, BodyHandActionResult.Reason.DENIED, authority);
             var handSlots = BodyHandStateService.slots(hands, authority.handIds());
             var equipment = handSlots == null ? null : BodyHandStateService.equipment(hands, authority.body(), handSlots);
             if (equipment == null) return result(request, BodyHandActionResult.Reason.DENIED, authority);
             boolean fromWorn = request.action() == BodyHandActionRequest.Action.GRID_BODY_WORN_TO_HELD;
             boolean toWorn = request.action() == BodyHandActionRequest.Action.GRID_BODY_HELD_TO_WORN;
             String fromId = request.sourceHostId(), toId = request.destinationHostId();
             if ((!fromWorn && !hands.handIds().contains(fromId)) || (!toWorn && !hands.handIds().contains(toId))
                     || (fromWorn && equipment.stream().noneMatch(s -> s.id().equals(fromId)))
                     || (toWorn && equipment.stream().noneMatch(s -> s.id().equals(toId))))
                 return result(request, BodyHandActionResult.Reason.DENIED, authority);
             var liveEquipment = LiveHands.equipmentSnapshot(authority.body()).orElse(null);
             if (liveEquipment == null || !liveEquipment.persisted())
                 return result(request, BodyHandActionResult.Reason.DENIED, authority);
             var from = new LiveHands.GridHost(fromWorn, fromId, request.expectedToken());
             var to = new LiveHands.GridHost(toWorn, toId, request.destinationHostToken());
             var source = fromWorn ? liveEquipment.slots().stream().filter(s -> s.id().equals(fromId)
                     && s.token().filter(request.expectedToken()::equals).isPresent())
                     .findFirst().flatMap(LiveHands.EquipmentSlot::stack).orElse(null)
                     : hands.token(fromId).filter(request.expectedToken()::equals).flatMap(t -> hands.stackCopy(fromId)).orElse(null);
             var destination = toWorn ? liveEquipment.slots().stream().filter(s -> s.id().equals(toId)
                     && s.token().filter(request.destinationHostToken()::equals).isPresent())
                     .findFirst().flatMap(LiveHands.EquipmentSlot::stack).orElse(null)
                     : hands.token(toId).filter(request.destinationHostToken()::equals).flatMap(t -> hands.stackCopy(toId)).orElse(null);
             var contents = source == null ? null : GridContents.read(source).orElse(null);
             if (contents == null || destination == null || GridContents.read(destination).isEmpty()
                     || contents.entries().stream().noneMatch(e -> e.token().equals(request.expectedChildOrSourceToken())
                         && e.x() == request.oldCellX() && e.y() == request.oldCellY()
                         && e.rotation().degrees() == request.oldRotation()))
                 return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
             if (!HandActorAuthority.revalidate(actor, authority)
                     || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                     || !equipment.equals(BodyHandStateService.equipment(hands, authority.body(), handSlots)))
                 return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
             var oldRotation = java.util.Arrays.stream(GridGeometry.Rotation.values())
                     .filter(r -> r.degrees() == request.oldRotation()).findFirst().orElseThrow();
             var newRotation = java.util.Arrays.stream(GridGeometry.Rotation.values())
                     .filter(r -> r.degrees() == request.rotation()).findFirst().orElseThrow();
             var outcome = BodyBodyGridTransfer.move(actor, from, to, request.expectedChildOrSourceToken(),
                     request.expectedRevision(), new GridGeometry.Cell(request.oldCellX(), request.oldCellY()), oldRotation,
                     new GridGeometry.Cell(request.cellX(), request.cellY()), newRotation);
             BodyHandActionResult response = result(request, switch (outcome) {
                 case SUCCESS -> BodyHandActionResult.Reason.OK;
                 case DENIED -> BodyHandActionResult.Reason.DENIED;
                 case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
             }, authority);
             if (outcome != BodyBodyGridTransfer.Result.DENIED) BodyHandStateService.requireFreshQuery(actor);
             return response;
         }

         if (request.action() == BodyHandActionRequest.Action.GRID_CROSS_BELT_TO_BACK
                || request.action() == BodyHandActionRequest.Action.GRID_CROSS_BACK_TO_BELT) {
            if (!hands.handIds().equals(authority.handIds()))
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var handSlots = BodyHandStateService.slots(hands, authority.handIds());
            if (handSlots == null) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var equipment = BodyHandStateService.equipment(hands, authority.body(), handSlots);
            if (equipment == null) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            String from = request.action() == BodyHandActionRequest.Action.GRID_CROSS_BELT_TO_BACK ? "belt" : "back";
            String to = from.equals("belt") ? "back" : "belt";
            var source = equipment.stream().filter(slot -> slot.id().equals(from)).findFirst();
            var destination = equipment.stream().filter(slot -> slot.id().equals(to)).findFirst();
            if (source.isEmpty() || destination.isEmpty())
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            if (!request.expectedToken().equals(source.orElseThrow().token())
                    || !request.destinationHostToken().equals(destination.orElseThrow().token()))
                return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            var liveEquipment = LiveHands.equipmentSnapshot(authority.body());
            if (liveEquipment.isEmpty() || !liveEquipment.orElseThrow().persisted())
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var sourceHost = liveEquipment.orElseThrow().slots().stream().filter(s -> s.id().equals(from))
                    .findFirst().flatMap(LiveHands.EquipmentSlot::stack).orElse(null);
            var destinationHost = liveEquipment.orElseThrow().slots().stream().filter(s -> s.id().equals(to))
                    .findFirst().flatMap(LiveHands.EquipmentSlot::stack).orElse(null);
            var contents = sourceHost == null ? null : GridContents.read(sourceHost).orElse(null);
            if (contents == null || destinationHost == null || GridContents.read(destinationHost).isEmpty()
                    || contents.entries().stream().noneMatch(entry -> entry.token().equals(request.expectedChildOrSourceToken())
                        && entry.x() == request.oldCellX() && entry.y() == request.oldCellY()
                        && entry.rotation().degrees() == request.oldRotation()))
                return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            if (!HandActorAuthority.revalidate(actor, authority)
                    || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || hands.revision() != request.expectedRevision()
                    || !equipment.equals(BodyHandStateService.equipment(hands, authority.body(), handSlots)))
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            var oldRotation = java.util.Arrays.stream(GridGeometry.Rotation.values())
                    .filter(r -> r.degrees() == request.oldRotation()).findFirst().orElseThrow();
            var newRotation = java.util.Arrays.stream(GridGeometry.Rotation.values())
                    .filter(r -> r.degrees() == request.rotation()).findFirst().orElseThrow();
            var outcome = BodyEquippedStorageTransfer.moveGridBetweenHosts(actor, from, request.expectedToken(),
                    to, request.destinationHostToken(), request.expectedChildOrSourceToken(), request.expectedRevision(),
                    new GridGeometry.Cell(request.oldCellX(), request.oldCellY()), oldRotation,
                    new GridGeometry.Cell(request.cellX(), request.cellY()), newRotation);
            BodyHandActionResult response = result(request, reason(outcome), authority);
            if (outcome == BodyEquippedStorageTransfer.Result.SUCCESS
                    || outcome == BodyEquippedStorageTransfer.Result.RECOVERY_REQUIRED)
                BodyHandStateService.requireFreshQuery(actor);
            return response;
        }

        String equipmentSlot = switch (request.action()) {
            case EQUIP_BELT, UNEQUIP_BELT, STORE_BELT, TAKE_BELT, GRID_STORE_BELT, GRID_TAKE_BELT, GRID_MOVE_BELT, SMART_STORE_BELT, SMART_TAKE_BELT -> "belt";
            case EQUIP_BACK, UNEQUIP_BACK, STORE_BACK, TAKE_BACK, GRID_STORE_BACK, GRID_TAKE_BACK, GRID_MOVE_BACK, SMART_STORE_BACK, SMART_TAKE_BACK -> "back";
            default -> null;
        };
        if (equipmentSlot != null) {
            if (!hands.handIds().equals(authority.handIds()))
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var handSlots = BodyHandStateService.slots(hands, authority.handIds());
            if (handSlots == null) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var equipment = BodyHandStateService.equipment(hands, authority.body(), handSlots);
            if (equipment == null) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var target = equipment.stream().filter(slot -> slot.id().equals(equipmentSlot)).findFirst();
            if (target.isEmpty()) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var slot = target.orElseThrow();
            boolean equipping = request.action() == BodyHandActionRequest.Action.EQUIP_BELT
                    || request.action() == BodyHandActionRequest.Action.EQUIP_BACK;
            boolean unequipping = request.action() == BodyHandActionRequest.Action.UNEQUIP_BELT
                    || request.action() == BodyHandActionRequest.Action.UNEQUIP_BACK;
            boolean storingAction = request.action() == BodyHandActionRequest.Action.STORE_BELT
                    || request.action() == BodyHandActionRequest.Action.STORE_BACK;
            boolean smartAction = request.action() == BodyHandActionRequest.Action.SMART_STORE_BELT
                    || request.action() == BodyHandActionRequest.Action.SMART_STORE_BACK
                    || request.action() == BodyHandActionRequest.Action.SMART_TAKE_BELT
                    || request.action() == BodyHandActionRequest.Action.SMART_TAKE_BACK;
            boolean gridAction = request.action() == BodyHandActionRequest.Action.GRID_STORE_BELT
                    || request.action() == BodyHandActionRequest.Action.GRID_STORE_BACK
                    || request.action() == BodyHandActionRequest.Action.GRID_TAKE_BELT
                    || request.action() == BodyHandActionRequest.Action.GRID_TAKE_BACK
                    || request.action() == BodyHandActionRequest.Action.GRID_MOVE_BELT
                    || request.action() == BodyHandActionRequest.Action.GRID_MOVE_BACK;
            String handToken = hands.token(request.handId()).orElse(null);
            boolean smartStoreAction = request.action() == BodyHandActionRequest.Action.SMART_STORE_BELT
                    || request.action() == BodyHandActionRequest.Action.SMART_STORE_BACK;
            if (smartAction ? !request.expectedToken().equals(slot.token())
                    || (smartStoreAction ? !request.expectedChildOrSourceToken().equals(handToken) : handToken != null)
                    : !equipmentPrecheck(request.action(), request.expectedToken(), handToken, slot.token(), slot.childToken()))
                return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            var liveEquipment = LiveHands.equipmentSnapshot(authority.body()).orElse(null);
            var liveSlot = liveEquipment == null ? null : liveEquipment.slots().stream()
                    .filter(candidate -> candidate.id().equals(equipmentSlot)).findFirst().orElse(null);
            if (!gridAction && (request.action() == BodyHandActionRequest.Action.STORE_BELT
                    || request.action() == BodyHandActionRequest.Action.STORE_BACK
                    || request.action() == BodyHandActionRequest.Action.TAKE_BELT
                    || request.action() == BodyHandActionRequest.Action.TAKE_BACK)
                    && liveSlot != null && liveSlot.stack().filter(stack -> stack.has(ModDataComponents.GRID_CONTENTS.get())).isPresent())
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            if (gridAction) {
                boolean storing = request.action() == BodyHandActionRequest.Action.GRID_STORE_BELT
                        || request.action() == BodyHandActionRequest.Action.GRID_STORE_BACK;
                boolean moving = request.action() == BodyHandActionRequest.Action.GRID_MOVE_BELT
                        || request.action() == BodyHandActionRequest.Action.GRID_MOVE_BACK;
                var host = liveSlot == null ? null : liveSlot.stack().orElse(null);
                var contents = host == null ? null : GridContents.read(host).orElse(null);
                var anchor = new GridGeometry.Cell(request.cellX(), request.cellY());
                if (contents == null
                        || storing && !request.expectedChildOrSourceToken().equals(handToken)
                        || storing && request.action() != BodyHandActionRequest.Action.GRID_STORE_BELT
                            && request.action() != BodyHandActionRequest.Action.GRID_STORE_BACK
                        || !storing && !moving && (handToken != null || contents.entries().stream().noneMatch(entry ->
                            entry.token().equals(request.expectedChildOrSourceToken()) && entry.x() == request.cellX()
                                    && entry.y() == request.cellY()))
                        || moving && contents.entries().stream().noneMatch(entry -> entry.token().equals(request.expectedChildOrSourceToken())
                            && entry.x() == request.oldCellX() && entry.y() == request.oldCellY() && entry.rotation().degrees() == request.oldRotation()))
                    return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
                if (!HandActorAuthority.revalidate(actor, authority)
                        || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                        || hands.revision() != request.expectedRevision()
                        || !equipment.equals(BodyHandStateService.equipment(hands, authority.body(), handSlots)))
                    return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
                BodyEquippedStorageTransfer.Result gridOutcome = storing
                        ? BodyEquippedStorageTransfer.storeGrid(actor, equipmentSlot, request.expectedToken(), request.handId(),
                            request.expectedChildOrSourceToken(), request.expectedRevision(), anchor,
                            java.util.Arrays.stream(GridGeometry.Rotation.values()).filter(r -> r.degrees() == request.rotation()).findFirst().orElseThrow())
                        : moving ? BodyEquippedStorageTransfer.moveGrid(actor, equipmentSlot, request.expectedToken(),
                            request.expectedChildOrSourceToken(), request.expectedRevision(), new GridGeometry.Cell(request.oldCellX(), request.oldCellY()),
                            java.util.Arrays.stream(GridGeometry.Rotation.values()).filter(r -> r.degrees() == request.oldRotation()).findFirst().orElseThrow(),
                            anchor, java.util.Arrays.stream(GridGeometry.Rotation.values()).filter(r -> r.degrees() == request.rotation()).findFirst().orElseThrow())
                        : BodyEquippedStorageTransfer.takeGrid(actor, equipmentSlot, request.expectedToken(), request.handId(),
                            request.expectedChildOrSourceToken(), anchor, request.expectedRevision());
                BodyHandActionResult response = result(request, reason(gridOutcome), authority);
                if (gridOutcome == BodyEquippedStorageTransfer.Result.SUCCESS) BodyHandStateService.requireFreshQuery(actor);
                return response;
            }
            if (smartAction) {
                if (!HandActorAuthority.revalidate(actor, authority)
                        || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                        || hands.revision() != request.expectedRevision()
                        || !equipment.equals(BodyHandStateService.equipment(hands, authority.body(), handSlots)))
                    return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
                boolean storingSmart = request.action() == BodyHandActionRequest.Action.SMART_STORE_BELT
                        || request.action() == BodyHandActionRequest.Action.SMART_STORE_BACK;
                var outcome = storingSmart
                        ? BodyEquippedStorageTransfer.smartStore(actor, equipmentSlot, request.expectedToken(), request.handId(),
                            request.expectedChildOrSourceToken(), request.expectedRevision())
                        : BodyEquippedStorageTransfer.smartTake(actor, equipmentSlot, request.expectedToken(), request.handId(),
                            request.expectedChildOrSourceToken(), request.expectedRevision());
                BodyHandActionResult response = result(request, reason(outcome), authority);
                if (outcome == BodyEquippedStorageTransfer.Result.SUCCESS) BodyHandStateService.requireFreshQuery(actor);
                return response;
            }
            if (!HandActorAuthority.revalidate(actor, authority)
                    || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || hands.revision() != request.expectedRevision()
                    || !equipment.equals(BodyHandStateService.equipment(hands, authority.body(), handSlots)))
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            BodyHandActionResult.Reason outcome;
            if (equipping) outcome = reason(BodyEquipmentTransfer.equip(actor, request.handId(), equipmentSlot,
                    request.expectedToken(), request.expectedRevision()));
            else if (unequipping) outcome = reason(BodyEquipmentTransfer.unequip(actor, equipmentSlot, request.handId(),
                    request.expectedToken(), request.expectedRevision()));
            else if (request.action() == BodyHandActionRequest.Action.STORE_BELT
                    || request.action() == BodyHandActionRequest.Action.STORE_BACK)
                outcome = reason(BodyEquippedStorageTransfer.store(actor, equipmentSlot, request.expectedToken(),
                        request.handId(), handToken, request.expectedRevision()));
            else outcome = reason(BodyEquippedStorageTransfer.take(actor, equipmentSlot, request.expectedToken(),
                    request.handId(), slot.childToken(), request.expectedRevision()));
            BodyHandActionResult response = result(request, outcome, authority);
            if (outcome == BodyHandActionResult.Reason.OK) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }

        if (request.action() == BodyHandActionRequest.Action.INSERT_POUCH
                || request.action() == BodyHandActionRequest.Action.EXTRACT_POUCH)
            return result(request, BodyHandActionResult.Reason.DENIED, authority);

        if (request.action() == BodyHandActionRequest.Action.PICKUP_SELECTED) {
            if (!HandActorAuthority.revalidate(actor, authority))
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            java.util.concurrent.atomic.AtomicReference<java.util.UUID> source = new java.util.concurrent.atomic.AtomicReference<>();
            var outcome = BodyHandItemTransfer.pickupSelected(actor, request.pickupTarget(), request.handId(),
                    request.expectedRevision(), source::set);
            BodyHandActionResult.Reason pickupReason = reason(outcome);
            BodyHandActionResult.HostTransition transition = null;
            if (pickupReason == BodyHandActionResult.Reason.OK) {
                LiveHands committedHands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                var committedTransition = pickupTransition(committedHands, source.get(), request.handId(),
                        HandActorAuthority.revalidate(actor, authority), authority.body());
                if (committedTransition.isEmpty()) pickupReason = BodyHandActionResult.Reason.RECOVERY_REQUIRED;
                else transition = committedTransition.orElseThrow();
            }
            return result(request, pickupReason, authority, transition);
        }
        if (request.action() == BodyHandActionRequest.Action.STOP_PULL) {
            var outcome = BodyPullService.stop(actor);
            BodyHandActionResult response = result(request, pullReason(outcome), authority);
            if (outcome != BodyPullService.Result.DENIED) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }
        if (request.action() == BodyHandActionRequest.Action.TOGGLE_PULL) {
            var outcome = BodyPullService.toggle(actor, request.pickupTarget(), request.handId(), request.expectedRevision());
            BodyHandActionResult response = result(request, pullReason(outcome), authority);
            if (outcome != BodyPullService.Result.DENIED) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }
        if (request.action() == BodyHandActionRequest.Action.ALTERNATE_TARGET) {
            if (!HandActorAuthority.revalidate(actor, authority))
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            if (hands.revision() != request.expectedRevision())
                return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            var ownedToken = hands.token(request.handId()).orElse(null);
            if (request.expectedToken() != null && !request.expectedToken().equals(ownedToken))
                return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            if (ownedToken != null) {
                var held = hands.stackCopy(request.handId()).orElse(null);
                var heldId = held == null ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
                try {
                    var medical = heldId == null ? null : com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                            com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(authority.body().level()), heldId);
                    if (medical != null && medical.syringePolicy().isPresent())
                        return result(request, liquidReason(com.juicyslew.moonstation14.ms14.medical.syringe.SyringeContainerService.useContainer(
                                actor, request.handId(), ownedToken, request.expectedRevision(), request.sequence(), request.pickupTarget())), authority);
                } catch (RuntimeException ignored) { return result(request, BodyHandActionResult.Reason.DENIED, authority); }
                var outcome = BodyLiquidInteraction.fill(actor, request.pickupTarget(), request.handId(), ownedToken,
                        request.expectedRevision(), true);
                BodyHandActionResult response = result(request, liquidReason(outcome), authority);
                if (outcome != BodyLiquidInteraction.Result.DENIED) BodyHandStateService.requireFreshQuery(actor);
                return response;
            }
            if (hands.reservation(request.handId()).isPresent())
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            return result(request, BodyTimedIngestionService.startWorld(actor, request.pickupTarget(),
                    request.handId(), request.expectedRevision(), request.sequence()), authority);
        }
        if (hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty())
            return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
        if (!HandActorAuthority.revalidate(actor, authority))
            return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);

        java.util.concurrent.atomic.AtomicReference<java.util.UUID> spawned = new java.util.concurrent.atomic.AtomicReference<>();
        if (request.action() == BodyHandActionRequest.Action.THROW_ITEM) {
            var outcome = BodyLooseThrow.throwHeld(actor, request.handId(), request.expectedToken(), request.expectedRevision(), spawned::set);
            BodyHandActionResult.Reason throwReason = looseThrowReason(outcome);
            if (throwReason == BodyHandActionResult.Reason.OK && spawned.get() != null)
                BodyWorldGridService.authorizeTransferredOpen(actor, authority, spawned.get());
            BodyHandActionResult.HostTransition transition = throwReason == BodyHandActionResult.Reason.OK && spawned.get() != null
                    ? new BodyHandActionResult.HostTransition(BodyHandActionResult.HostTransition.Direction.DROP, spawned.get(), request.expectedToken()) : null;
            if (outcome != BodyLooseThrow.Result.DENIED) BodyHandStateService.requireFreshQuery(actor);
            return result(request, throwReason, authority, transition);
        }
        if (request.action() == BodyHandActionRequest.Action.USE_TARGET) {
            var held = hands.stackCopy(request.handId()).orElse(null);
            var heldId = held == null ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
            try {
                var medical = heldId == null ? null : com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                        com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(authority.body().level()), heldId);
                if (medical != null && medical.syringePolicy().isPresent())
                    return result(request, liquidReason(com.juicyslew.moonstation14.ms14.medical.syringe.SyringeContainerService.useContainer(
                            actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence(), request.pickupTarget())), authority);
            } catch (RuntimeException ignored) { return result(request, BodyHandActionResult.Reason.DENIED, authority); }
            var outcome = BodyLiquidInteraction.fill(actor, request.pickupTarget(), request.handId(), request.expectedToken(),
                    request.expectedRevision(), false);
            BodyHandActionResult response = result(request, liquidReason(outcome), authority);
            if (outcome != BodyLiquidInteraction.Result.DENIED) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }
        if (request.action() == BodyHandActionRequest.Action.USE_IN_HAND) {
            var stack = hands.stackCopy(request.handId()).orElse(null);
            if (stack == null) return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            var medicalId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            try {
                var medical = medicalId == null ? null : com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                        com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(authority.body().level()), medicalId);
                if (medical != null && medical.healing().isPresent())
                    return result(request, com.juicyslew.moonstation14.ms14.medical.MedicalTreatmentService.start(
                            actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence(), authority.body().getUUID()), authority);
                if (medical != null && medical.healthAnalyzer().isPresent())
                    return result(request, com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerServer.activate(
                            actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence()), authority);
                if (medical != null && medical.syringePolicy().isPresent())
                    return result(request, heldMutationReason(com.juicyslew.moonstation14.ms14.medical.syringe.SyringeModeService.activate(
                            actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence())), authority);
            } catch (RuntimeException ignored) { }
            if (!(stack.getItem() instanceof IReagentTrait))
                return result(request, BodyHeldItemActions.activate(actor, request), authority);
            if (!ModIngestionProfiles.serverReady())
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null || ModIngestionProfiles.serverLookup(itemId) == null)
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            BodyHandActionResult.Reason reason = BodyTimedIngestionService.startHeld(actor, request.handId(),
                    request.expectedToken(), request.expectedRevision(), request.sequence());
            BodyHandActionResult response = result(request, reason, authority);
            return response;
        }
        if (request.action() == BodyHandActionRequest.Action.USE_BODY) {
            var held = hands.stackCopy(request.handId()).orElse(null);
            if (held == null) return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
            var id = BuiltInRegistries.ITEM.getKey(held.getItem());
            com.juicyslew.moonstation14.ms14.medical.MedicalItemData medical = null;
            try {
                if (id != null) medical = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                        com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(authority.body().level()), id);
            } catch (RuntimeException ignored) { }
            if (medical == null) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            BodyHandActionResult.Reason outcome;
            if (medical.syringePolicy().isPresent()) outcome = com.juicyslew.moonstation14.ms14.medical.syringe.SyringeService.useBody(
                    actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence(), request.pickupTarget());
            else if (medical.healing().isPresent()) outcome = com.juicyslew.moonstation14.ms14.medical.MedicalTreatmentService.start(
                    actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence(), request.pickupTarget());
            else if (medical.healthAnalyzer().isPresent()) outcome = com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerServer.scan(
                    actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence(), request.pickupTarget());
            else outcome = BodyHandActionResult.Reason.DENIED;
            return result(request, outcome, authority);
        }
        if (request.action() == BodyHandActionRequest.Action.CYCLE_SYRINGE_AMOUNT) {
            var held = hands.stackCopy(request.handId()).orElse(null);
            var id = held == null ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
            com.juicyslew.moonstation14.ms14.medical.MedicalItemData medical = null;
            try { if (id != null) medical = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                    com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(authority.body().level()), id); }
            catch (RuntimeException ignored) { }
            if (medical == null || medical.syringePolicy().isEmpty())
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            return result(request, heldMutationReason(com.juicyslew.moonstation14.ms14.medical.syringe.SyringeModeService.cycleDose(
                    actor, request.handId(), request.expectedToken(), request.expectedRevision(), request.sequence())), authority);
        }
        if (request.action() == BodyHandActionRequest.Action.PRIME_GRENADE) {
            var primeOutcome = BodyGrenadePrime.prime(actor, request.handId(), request.expectedToken(), request.expectedRevision());
            BodyHandActionResult.Reason outcome = grenadeReason(primeOutcome);
            BodyHandActionResult response = result(request, outcome, authority);
            if (primeOutcome == BodyGrenadePrime.Result.SUCCESS) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }

        if (request.action() == BodyHandActionRequest.Action.USE_HELD) {
            BodyHandActionResult response = result(request, heldUseReason(actor, request, hands), authority);
            if (response.reason() == BodyHandActionResult.Reason.OK
                    && hands.stackCopy(request.handId()).filter(stack -> stack.is(ModItems.GRENADE.get())).isPresent())
                BodyHandStateService.requireFreshQuery(actor);
            return response;
        }

        if (!isDropAction(request.action()))
            return result(request, BodyHandActionResult.Reason.DENIED, authority);
        BodyHandItemTransfer.Result outcome = BodyHandItemTransfer.drop(actor, request.handId(),
                request.expectedToken(), request.expectedRevision(), spawned::set);
        BodyHandActionResult.Reason dropReason = reason(outcome);
        if (dropReason == BodyHandActionResult.Reason.OK && spawned.get() != null)
            BodyWorldGridService.authorizeTransferredOpen(actor, authority, spawned.get());
        return result(request, dropReason, authority, dropReason == BodyHandActionResult.Reason.OK && spawned.get() != null
                 ? new BodyHandActionResult.HostTransition(BodyHandActionResult.HostTransition.Direction.DROP,
                     spawned.get(), request.expectedToken()) : null);
    }

    private static BodyHandActionResult.Reason heldUseReason(ServerPlayer actor, BodyHandActionRequest request, LiveHands hands) {
        var stack = hands.stackCopy(request.handId()).orElse(null);
        return stack != null && stack.is(ModItems.GRENADE.get())
                ? grenadeReason(BodyGrenadePrime.prime(actor, request.handId(), request.expectedToken(), request.expectedRevision()))
                : BodyHeldItemActions.use(actor, request);
    }

    static java.util.Optional<BodyHandActionResult.HostTransition> pickupTransition(
            LiveHands committedHands, java.util.UUID selectedSource, String handId,
            boolean authorityValid, LivingEntity body) {
        if (!authorityValid || selectedSource == null || committedHands == null
                || !committedHands.compatible(body) || !committedHands.handIds().contains(handId))
            return java.util.Optional.empty();
        return committedHands.token(handId).map(token -> new BodyHandActionResult.HostTransition(
                BodyHandActionResult.HostTransition.Direction.PICKUP, selectedSource, token));
    }

    static boolean equipmentPrecheck(BodyHandActionRequest.Action action, String expectedToken,
                                     String handToken, String slotToken, String childToken) {
        boolean equipping = action == BodyHandActionRequest.Action.EQUIP_BELT
                || action == BodyHandActionRequest.Action.EQUIP_BACK;
        boolean unequipping = action == BodyHandActionRequest.Action.UNEQUIP_BELT
                || action == BodyHandActionRequest.Action.UNEQUIP_BACK;
        boolean gridAction = action == BodyHandActionRequest.Action.GRID_STORE_BELT
                || action == BodyHandActionRequest.Action.GRID_STORE_BACK
                || action == BodyHandActionRequest.Action.GRID_TAKE_BELT
                || action == BodyHandActionRequest.Action.GRID_TAKE_BACK
                || action == BodyHandActionRequest.Action.GRID_MOVE_BELT || action == BodyHandActionRequest.Action.GRID_MOVE_BACK;
        boolean storingAction = action == BodyHandActionRequest.Action.STORE_BELT
                || action == BodyHandActionRequest.Action.STORE_BACK;
        if (equipping) return expectedToken.equals(handToken) && slotToken == null;
        return expectedToken.equals(slotToken) && (gridAction || (unequipping ? handToken == null
                : storingAction ? handToken != null && childToken == null
                : handToken == null && childToken != null));
    }

    /** Final pre-publication policy after authority callbacks; never changes either attachment. */
    static boolean selectionAllowed(LivingEntity body, LiveHands before, LiveHands after, long revision) {
        return before.revision() == revision && revision != Long.MAX_VALUE
                && after.revision() == revision + 1 && before.compatible(body)
                && after.compatible(body) && CharacterControlSystem.canAct(body);
    }

    private static BodyHandActionResult.Reason reason(BodyHandItemTransfer.Result outcome) {
        return switch (outcome) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    private static BodyHandActionResult.Reason heldMutationReason(com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation.Result outcome) {
        return switch (outcome) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    private static BodyHandActionResult.Reason reason(BodyEquipmentTransfer.Result outcome) {
        return switch (outcome) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    private static BodyHandActionResult.Reason reason(BodyEquippedStorageTransfer.Result outcome) {
        return switch (outcome) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    static boolean isDropAction(BodyHandActionRequest.Action action) {
        return action == BodyHandActionRequest.Action.DROP;
    }

    private static boolean storageMutation(BodyHandActionRequest.Action action) {
        return switch (action) {
            case STORE_BELT, STORE_BACK, TAKE_BELT, TAKE_BACK,
                    SMART_STORE_BELT, SMART_STORE_BACK, SMART_TAKE_BELT, SMART_TAKE_BACK,
                    GRID_STORE_BELT, GRID_STORE_BACK, GRID_TAKE_BELT, GRID_TAKE_BACK,
                    GRID_MOVE_BELT, GRID_MOVE_BACK, HELD_GRID_STORE, HELD_GRID_TAKE, HELD_GRID_MOVE,
                    GRID_BODY_WORN_TO_HELD, GRID_BODY_HELD_TO_WORN, GRID_BODY_HELD_TO_HELD,
                    GRID_CROSS_BELT_TO_BACK, GRID_CROSS_BACK_TO_BELT -> true;
            default -> false;
        };
    }

    private static BodyHandActionResult.Reason liquidReason(BodyLiquidInteraction.Result outcome) {
        return switch (outcome) { case SUCCESS -> BodyHandActionResult.Reason.OK; case DENIED -> BodyHandActionResult.Reason.DENIED; case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED; };
    }
    private static BodyHandActionResult.Reason looseThrowReason(BodyLooseThrow.Result outcome) {
        return switch (outcome) { case SUCCESS -> BodyHandActionResult.Reason.OK; case DENIED -> BodyHandActionResult.Reason.DENIED; case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED; };
    }
    private static BodyHandActionResult.Reason pullReason(BodyPullService.Result outcome) {
        return switch (outcome) { case SUCCESS -> BodyHandActionResult.Reason.OK; case DENIED -> BodyHandActionResult.Reason.DENIED; case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED; };
    }

    private static BodyHandActionResult.Reason grenadeReason(BodyGrenadePrime.Result outcome) {
        return switch (outcome) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    private static BodyHandActionResult.Reason grenadeReason(BodyGrenadeThrow.Result outcome) {
        return switch (outcome) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    private static BodyHandActionResult.Reason reason(BodyHeldGridTransfer.Result outcome) {
        return switch (outcome) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

    private static BodyHandActionResult result(BodyHandActionRequest request, BodyHandActionResult.Reason reason,
                                                 HandActorAuthority.Snapshot authority) {
        return result(request, reason, authority, null);
    }

    private static BodyHandActionResult result(BodyHandActionRequest request, BodyHandActionResult.Reason reason,
                                                 HandActorAuthority.Snapshot authority,
                                                 BodyHandActionResult.HostTransition transition) {
        if (authority != null && HandActorAuthority.revalidate(authority.actor(), authority)) {
            LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (hands != null && hands.compatible(authority.body()) && hands.handIds().contains(request.handId())) {
                var token = hands.token(request.handId());
                var stack = hands.stackCopy(request.handId());
                if (token.isPresent() && stack.isPresent()) {
                    var item = stack.orElseThrow();
                    String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
                    // Snapshot encoding must never fail after a successful ownership transfer.
                    if (itemId.isBlank() || itemId.length() > 256) {
                        return new BodyHandActionResult(request.epoch(), request.sequence(),
                                reason == BodyHandActionResult.Reason.OK, reason, false, 0, null, null, null, 0, transition);
                    }
                    return new BodyHandActionResult(request.epoch(), request.sequence(),
                            reason == BodyHandActionResult.Reason.OK, reason, true, hands.revision(),
                            hands.activeHand(), token.orElseThrow(), itemId, item.getCount(), transition);
                }
                return new BodyHandActionResult(request.epoch(), request.sequence(),
                        reason == BodyHandActionResult.Reason.OK, reason, true, hands.revision(),
                        hands.activeHand(), null, null, 0, null);
            }
        }
        return new BodyHandActionResult(request.epoch(), request.sequence(),
                reason == BodyHandActionResult.Reason.OK, reason, false, 0, null, null, null, 0, null);
    }

    /** Pure dispatch policy; never serves as proof of a real authenticated player. */
    static final class SequenceGate {
        private long epoch;
        private long sequence;
        private long tick = Long.MIN_VALUE;
        private int count;

        BodyHandActionResult.Reason admit(long currentEpoch, long requestedEpoch,
                                          long nextSequence, long currentTick) {
            if (tick != currentTick) {
                tick = currentTick;
                count = 0;
            }
            if (count >= 8) return BodyHandActionResult.Reason.RATE_LIMITED;
            count++;
            if (currentEpoch <= 0 || requestedEpoch != currentEpoch)
                return BodyHandActionResult.Reason.WRONG_EPOCH;
            if (epoch != currentEpoch) {
                epoch = currentEpoch;
                sequence = 0;
            }
            if (nextSequence <= sequence) return BodyHandActionResult.Reason.REPLAY;
            sequence = nextSequence;
            return null;
        }
    }
}
