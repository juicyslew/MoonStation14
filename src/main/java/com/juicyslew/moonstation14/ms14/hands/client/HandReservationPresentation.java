package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

/** Classifies a reserved hand from the settled snapshot, without treating client proof as authority. */
public final class HandReservationPresentation {
    public enum Kind { NONE, PULL, WIELD }

    private HandReservationPresentation() { }

    public static Kind classify(BodyHandStateSnapshot snapshot, BodyHandStateSnapshot.Hand hand) {
        if (hand == null || hand.pullTarget() == null) return Kind.NONE;
        var projection = snapshot == null ? null : snapshot.wieldProjection();
        if (projection != null && projection.supportHand().equals(hand.id())
                && projection.reservation().toString().equals(hand.pullTarget())) return Kind.WIELD;
        return Kind.PULL;
    }
}
