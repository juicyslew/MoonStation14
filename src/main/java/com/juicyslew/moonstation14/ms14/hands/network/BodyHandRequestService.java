package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyEquippedStorageTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyPouchTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Main-thread-only request gate; neither a menu nor a carrier-inventory route. */
public final class BodyHandRequestService {
    private static final Map<ServerPlayer, SequenceGate> GATES = new IdentityHashMap<>();

    private BodyHandRequestService() { }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GATES.remove(player);
    }

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
                authority.body().setData(ModDataAttachments.LIVE_HANDS.get(), next.orElseThrow());
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
            BodyHandItemTransfer.Result outcome = BodyHandItemTransfer.pickup(actor, request.pickupTarget(),
                    request.handId(), request.expectedRevision());
            return result(request, reason(outcome), authority);
        }

        String equipmentSlot = switch (request.action()) {
            case EQUIP_BELT, UNEQUIP_BELT, STORE_BELT, TAKE_BELT -> "belt";
            case EQUIP_BACK, UNEQUIP_BACK, STORE_BACK, TAKE_BACK -> "back";
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
            String handToken = hands.token(request.handId()).orElse(null);
            if (equipping ? !request.expectedToken().equals(handToken) || slot.token() != null
                    : !request.expectedToken().equals(slot.token())
                            || (unequipping ? handToken != null
                                    : request.action() == BodyHandActionRequest.Action.STORE_BELT
                                            || request.action() == BodyHandActionRequest.Action.STORE_BACK
                                            ? handToken == null || slot.childToken() != null
                                            : handToken != null || slot.childToken() == null))
                return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
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

        if (hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty())
            return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
        if (request.action() == BodyHandActionRequest.Action.INSERT_POUCH
                || request.action() == BodyHandActionRequest.Action.EXTRACT_POUCH) {
            // Prototype geometry, not a client-selected hand. Require the exact same attachment and revision
            // after reading the full server-side pouch snapshot and before invoking the atomic transfer.
            if (authority.handIds().size() != 2 || !hands.handIds().equals(authority.handIds()))
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var others = authority.handIds().stream().filter(id -> !id.equals(request.handId())).toList();
            if (others.size() != 1) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var slots = BodyHandStateService.slots(hands, authority.handIds());
            if (slots == null) return result(request, BodyHandActionResult.Reason.DENIED, authority);
            var pouch = slots.stream().filter(slot -> slot.id().equals(request.handId())).findFirst();
            var other = slots.stream().filter(slot -> slot.id().equals(others.getFirst())).findFirst();
            if (pouch.isEmpty() || other.isEmpty() || !pouch.orElseThrow().pouch()
                    || !request.expectedToken().equals(pouch.orElseThrow().token()))
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            String token = request.action() == BodyHandActionRequest.Action.INSERT_POUCH
                    ? other.orElseThrow().token() : pouch.orElseThrow().childToken();
            if (token == null || request.action() == BodyHandActionRequest.Action.EXTRACT_POUCH
                    && other.orElseThrow().token() != null
                    || request.action() == BodyHandActionRequest.Action.INSERT_POUCH
                    && pouch.orElseThrow().childToken() != null)
                return result(request, BodyHandActionResult.Reason.DENIED, authority);
            if (!HandActorAuthority.revalidate(actor, authority)
                    || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || hands.revision() != request.expectedRevision())
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            BodyPouchTransfer.Result outcome = request.action() == BodyHandActionRequest.Action.INSERT_POUCH
                    ? BodyPouchTransfer.insert(actor, request.handId(), request.expectedToken(), others.getFirst(),
                            token, request.expectedRevision())
                    : BodyPouchTransfer.extract(actor, request.handId(), request.expectedToken(), others.getFirst(),
                            token, request.expectedRevision());
            // The result only describes the top-level hand. Require a new full query for child state.
            BodyHandActionResult response = result(request, reason(outcome), authority);
            if (outcome == BodyPouchTransfer.Result.SUCCESS) BodyHandStateService.requireFreshQuery(actor);
            return response;
        }
        if (!HandActorAuthority.revalidate(actor, authority))
            return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);

        BodyHandItemTransfer.Result outcome = BodyHandItemTransfer.drop(actor, request.handId(),
                request.expectedToken(), request.expectedRevision());
        return result(request, reason(outcome), authority);
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

    private static BodyHandActionResult.Reason reason(BodyPouchTransfer.Result outcome) {
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

    private static BodyHandActionResult result(BodyHandActionRequest request, BodyHandActionResult.Reason reason,
                                                HandActorAuthority.Snapshot authority) {
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
                                reason == BodyHandActionResult.Reason.OK, reason, false, 0, null, null, null, 0);
                    }
                    return new BodyHandActionResult(request.epoch(), request.sequence(),
                            reason == BodyHandActionResult.Reason.OK, reason, true, hands.revision(),
                            hands.activeHand(), token.orElseThrow(), itemId, item.getCount());
                }
                return new BodyHandActionResult(request.epoch(), request.sequence(),
                        reason == BodyHandActionResult.Reason.OK, reason, true, hands.revision(),
                        hands.activeHand(), null, null, 0);
            }
        }
        return new BodyHandActionResult(request.epoch(), request.sequence(),
                reason == BodyHandActionResult.Reason.OK, reason, false, 0, null, null, null, 0);
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
