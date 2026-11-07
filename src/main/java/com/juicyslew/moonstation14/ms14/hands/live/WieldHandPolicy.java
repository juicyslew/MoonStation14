package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.UUID;

/** Pure hand-state transitions shared by the later authoritative wield entry point. */
public final class WieldHandPolicy {
    private WieldHandPolicy() { }

    public record Acquisition(LiveHands hands, WieldState wield) { }

    public static Optional<Acquisition> acquire(LivingEntity body, LiveHands hands, String sourceHand,
                                                 String sourceToken, String supportHand) {
        if (body == null || hands == null || !hands.compatible(body) || sourceHand == null
                || sourceToken == null || supportHand == null || sourceHand.equals(supportHand)) return Optional.empty();
        return acquire(hands, sourceHand, sourceToken, supportHand);
    }

    /** Pure transition used by JVM owner tests; live callers additionally validate body compatibility. */
    public static Optional<Acquisition> acquire(LiveHands hands, String sourceHand, String sourceToken, String supportHand) {
        if (hands == null || sourceHand == null || sourceToken == null || supportHand == null
                || sourceHand.equals(supportHand)) return Optional.empty();
        ItemToken token;
        try { token = new ItemToken(sourceToken); } catch (IllegalArgumentException badToken) { return Optional.empty(); }
        if (!hands.handIds().contains(sourceHand) || !hands.handIds().contains(supportHand)) return Optional.empty();
        if (!hands.token(sourceHand).filter(sourceToken::equals).isPresent() || !hands.isHandAvailable(supportHand))
            return Optional.empty();
        UUID proof = UUID.randomUUID();
        LiveHands reserved = hands.reserveHand(hands.revision(), supportHand, proof).orElse(null);
        if (reserved == null) return Optional.empty();
        return Optional.of(new Acquisition(reserved, new WieldState(sourceHand, token.value(), supportHand, proof)));
    }

    /** Exact token and reservation proof required; never releases unrelated pull/carry leases. */
    public static boolean validates(LivingEntity body, LiveHands hands, WieldState wield) {
        return body != null && hands != null && hands.compatible(body) && validates(hands, wield);
    }

    public static boolean validates(LiveHands hands, WieldState wield) {
        return hands != null && wield != null
                && hands.handIds().contains(wield.sourceHand()) && hands.handIds().contains(wield.supportHand())
                && hands.token(wield.sourceHand()).filter(wield.sourceToken()::equals).isPresent()
                && hands.reservation(wield.supportHand()).filter(wield.proof()::equals).isPresent()
                && hands.stackCopy(wield.supportHand()).isEmpty();
    }

    /** Returns empty if this exact wield lease is no longer present; other reservation owners remain untouched. */
    public static Optional<LiveHands> release(LiveHands hands, WieldState wield) {
        if (hands == null || wield == null
                || !hands.handIds().contains(wield.supportHand())
                || hands.reservation(wield.supportHand()).filter(wield.proof()::equals).isEmpty()) return Optional.empty();
        return hands.releaseHand(wield.supportHand(), wield.proof());
    }
}
