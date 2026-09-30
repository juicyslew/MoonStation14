package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
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

        if (request.action() == BodyHandActionRequest.Action.PICKUP) {
            if (!HandActorAuthority.revalidate(actor, authority))
                return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);
            BodyHandItemTransfer.Result outcome = BodyHandItemTransfer.pickup(actor, request.pickupTarget(),
                    request.handId(), request.expectedRevision());
            return result(request, reason(outcome), authority);
        }

        if (hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty())
            return result(request, BodyHandActionResult.Reason.STALE_HAND, authority);
        if (!HandActorAuthority.revalidate(actor, authority))
            return result(request, BodyHandActionResult.Reason.NO_AUTHORITY, null);

        BodyHandItemTransfer.Result outcome = BodyHandItemTransfer.drop(actor, request.handId(),
                request.expectedToken(), request.expectedRevision());
        return result(request, reason(outcome), authority);
    }

    private static BodyHandActionResult.Reason reason(BodyHandItemTransfer.Result outcome) {
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
