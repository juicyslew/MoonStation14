package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

import java.util.List;

/** Chooses an unoccupied, unreserved hand for an owned-body pull request. */
public final class BodyPullHandSelector {
    private BodyPullHandSelector() { }

    public static BodyHandStateSnapshot.Hand select(List<BodyHandStateSnapshot.Hand> hands, String activeHand) {
        if (hands == null || activeHand == null) return null;
        BodyHandStateSnapshot.Hand active = hands.stream().filter(hand -> activeHand.equals(hand.id()))
                .findFirst().orElse(null);
        if (available(active)) return active;
        return hands.stream().filter(BodyPullHandSelector::available).findFirst().orElse(null);
    }

    private static boolean available(BodyHandStateSnapshot.Hand hand) {
        return hand != null && hand.token() == null && hand.pullTarget() == null;
    }
}

