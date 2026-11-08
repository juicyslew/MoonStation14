package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.server.level.ServerPlayer;

/** Controlled-body owner for generic wield reservations. Input/event ordering is owned by gun integration. */
public final class LiveWieldSystem {
    private static final java.util.Set<net.minecraft.world.entity.LivingEntity> PENDING =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public enum UseResult { HANDLED, NOT_HANDLED, DENIED }
    private LiveWieldSystem() { }

    public static boolean isWielded(net.minecraft.world.entity.LivingEntity body, String hand, String token) {
        WieldState state = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get());
        LiveHands hands = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return state != null && state.sourceHand().equals(hand) && state.sourceToken().equals(token)
                && WieldHandPolicy.validates(body, hands, state);
    }

    /** Toggle from authenticated current body authority; support hand is chosen from declared layout. */
    public static boolean toggle(ServerPlayer actor, String hand, String token) {
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || !HandActorAuthority.revalidate(actor, authority)) return false;
        var body = authority.body();
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body)) return false;
        WieldState existing = body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get());
        if (existing != null) {
            if (!existing.sourceHand().equals(hand) || !existing.sourceToken().equals(token)
                    || !WieldHandPolicy.validates(body, before, existing)) return false;
            return cleanup(actor, authority, existing, before);
        }
        String support = before.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElse(null);
        WieldHandPolicy.Acquisition acquired = WieldHandPolicy.acquire(body, before, hand, token, support).orElse(null);
        if (acquired == null || !HandActorAuthority.revalidate(actor, authority)
                ) return false;
        body.setData(ModDataAttachments.WIELD_STATE.get(), acquired.wield());
        if (!LiveHandsCommitGateway.commit(actor, authority, acquired.hands())
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != acquired.hands()) {
            if (body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get()) == acquired.wield())
                body.removeData(ModDataAttachments.WIELD_STATE.get());
            return false;
        }
        com.juicyslew.moonstation14.ms14.standing.PostureSystem.refreshSupportedGunAim(body);
        return true;
    }

    /** Matches SS14 use-in-hand handling: initial wield is handled even when denied; default caller config unwields. */
    public static UseResult useInHand(ServerPlayer actor, String hand, String token, boolean unwieldOnUse) {
        HandActorAuthority.Snapshot authority = HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) return UseResult.DENIED;
        var body = authority.body();
        if (isWielded(body, hand, token))
            return unwieldOnUse ? (toggle(actor, hand, token) ? UseResult.HANDLED : UseResult.DENIED)
                    : UseResult.NOT_HANDLED;
        return toggle(actor, hand, token) ? UseResult.HANDLED : UseResult.HANDLED;
    }

    /** Safe reconciliation entry point for hand-commit owners; only removes this lease's exact UUID. */
    public static boolean reconcile(ServerPlayer actor, HandActorAuthority.Snapshot authority) {
        if (actor == null || authority == null || !HandActorAuthority.revalidate(actor, authority)) return false;
        var body = authority.body();
        WieldState state = body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get());
        if (state == null) return true;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (WieldHandPolicy.validates(body, hands, state)) return true;
        return cleanup(actor, authority, state, hands);
    }

    /** Commit callbacks only enqueue invalid leases; they must not nest a hand commit. */
    static void afterHandsCommit(net.minecraft.world.entity.LivingEntity body) {
        if (body == null) return;
        WieldState state = body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get());
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (state != null && !WieldHandPolicy.validates(body, hands, state))
            synchronized (PENDING) { PENDING.add(body); }
    }

    /** Drain after the initiating level-tick action has returned, never from its commit callback. */
    public static void tick(net.minecraft.server.level.ServerLevel level) {
        java.util.List<net.minecraft.world.entity.LivingEntity> bodies = new java.util.ArrayList<>();
        synchronized (PENDING) {
            for (var body : PENDING) if (body != null && body.level() == level) bodies.add(body);
            PENDING.removeAll(bodies);
        }
        for (var body : bodies) {
            WieldState state = body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get());
            LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (state == null || WieldHandPolicy.validates(body, hands, state)) continue;
            LiveHands released = WieldHandPolicy.release(hands, state).orElse(null);
            if (released == null) { removeProof(body, state); com.juicyslew.moonstation14.ms14.standing.PostureSystem.refreshSupportedGunAim(body); continue; }
            removeProof(body, state);
            if (!LiveHandsCommitGateway.clearExactReservationTrusted(body, hands, state.supportHand(), state.proof(), released)) {
                if (!body.hasData(ModDataAttachments.WIELD_STATE.get())) body.setData(ModDataAttachments.WIELD_STATE.get(), state);
                synchronized (PENDING) { PENDING.add(body); }
            } else com.juicyslew.moonstation14.ms14.standing.PostureSystem.refreshSupportedGunAim(body);
        }
    }

    private static void removeProof(net.minecraft.world.entity.LivingEntity body, WieldState exact) {
        if (body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get()) == exact)
            body.removeData(ModDataAttachments.WIELD_STATE.get());
    }

    private static boolean cleanup(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                   WieldState state, LiveHands hands) {
        var body = authority.body();
        LiveHands released = WieldHandPolicy.release(hands, state).orElse(null);
        if (released == null) {
            if (body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get()) == state)
                body.removeData(ModDataAttachments.WIELD_STATE.get());
            return true;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.WIELD_STATE.get()) == state)
            body.removeData(ModDataAttachments.WIELD_STATE.get());
        if (LiveHandsCommitGateway.commit(actor, authority, released)) {
            com.juicyslew.moonstation14.ms14.standing.PostureSystem.refreshSupportedGunAim(body);
            return true;
        }
        if (!body.hasData(ModDataAttachments.WIELD_STATE.get())) body.setData(ModDataAttachments.WIELD_STATE.get(), state);
        return false;
    }
}
