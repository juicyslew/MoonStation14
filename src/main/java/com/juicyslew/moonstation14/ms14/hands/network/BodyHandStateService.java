package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main-thread-only read route. An absent or incompatible attachment is never created or advertised as empty. */
public final class BodyHandStateService {
    private static final Map<ServerPlayer, QueryGate> GATES = new IdentityHashMap<>();

    private BodyHandStateService() { }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer actor) GATES.remove(actor);
    }

    /** No action before a successful full snapshot was sent for this exact connection and epoch. */
    static boolean hasIssued(ServerPlayer actor, HandActorAuthority.Snapshot authority) {
        QueryGate gate = GATES.get(actor);
        return gate != null && gate.issuedEpoch == authority.epoch() && gate.issuedBody == authority.body();
    }

    public static BodyHandStateSnapshot query(ServerPlayer actor, BodyHandStateQuery query) {
        QueryGate gate = GATES.get(actor);
        if (gate != null) gate.clearIssued();
        var resolved = HandActorAuthority.resolve(actor);
        if (resolved.isEmpty()) return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NO_AUTHORITY);
        var authority = resolved.orElseThrow();
        gate = GATES.computeIfAbsent(actor, ignored -> new QueryGate());
        var admission = gate.admit(query.epoch(), query.sequence(), actor.level().getGameTime());
        if (admission != null) return BodyHandStateSnapshot.rejected(query, admission);
        if (!HandActorAuthority.revalidate(actor, authority))
            return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NO_AUTHORITY);
        LiveHands hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(authority.body()) || !hands.handIds().equals(authority.handIds())
                || hands.handIds().size() > HandState.MAX_HANDS)
            return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NOT_READY);
        var slots = new ArrayList<BodyHandStateSnapshot.Hand>();
        for (String id : authority.handIds()) {
            var token = hands.token(id);
            var stack = hands.stackCopy(id);
            if (token.isPresent() != stack.isPresent())
                return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NOT_READY);
            if (token.isEmpty()) {
                slots.add(new BodyHandStateSnapshot.Hand(id, null, null, 0));
            } else {
                var item = stack.orElseThrow();
                String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
                if (itemId.isBlank() || itemId.length() > 256 || item.getCount() > 99)
                    return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NOT_READY);
                slots.add(new BodyHandStateSnapshot.Hand(id, token.orElseThrow(), itemId, item.getCount()));
            }
        }
        if (!HandActorAuthority.revalidate(actor, authority)
                || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands)
            return BodyHandStateSnapshot.rejected(query, BodyHandStateSnapshot.Reason.NO_AUTHORITY);
        gate.issuedEpoch = authority.epoch();
        gate.issuedBody = authority.body();
        return new BodyHandStateSnapshot(query.epoch(), query.sequence(), BodyHandStateSnapshot.Reason.OK,
                actor.getUUID(), authority.body().getUUID(), authority.epoch(), hands.revision(), hands.activeHand(), slots);
    }

    /** Bounded replay history keyed by the exact player identity and requested epoch (including bootstrap zero). */
    static final class QueryGate {
        private final LinkedHashMap<Long, Long> sequences = new LinkedHashMap<>();
        private long tick = Long.MIN_VALUE;
        private int count;
        private long issuedEpoch;
        private LivingEntity issuedBody;

        void clearIssued() {
            issuedEpoch = 0;
            issuedBody = null;
        }

        BodyHandStateSnapshot.Reason admit(long epoch, long sequence, long currentTick) {
            if (tick != currentTick) { tick = currentTick; count = 0; }
            if (++count > 4) return BodyHandStateSnapshot.Reason.RATE_LIMITED;
            if (sequence <= sequences.getOrDefault(epoch, 0L)) return BodyHandStateSnapshot.Reason.REPLAY;
            sequences.put(epoch, sequence);
            if (sequences.size() > 8) sequences.remove(sequences.keySet().iterator().next());
            return null;
        }
    }
}
