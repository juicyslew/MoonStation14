package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;

/** Classifies the ordinary empty-hand pickup alias before creating its server request. */
public final class AttachedItemPickupRoute {
    private AttachedItemPickupRoute() { }

    public static BodyHandActionRequest.Action actionFor(BodyHandActionRequest.Action requested,
                                                          boolean selectedAttached, boolean emptyHand) {
        return emptyHand && selectedAttached
                && (requested == BodyHandActionRequest.Action.PICKUP
                    || requested == BodyHandActionRequest.Action.PICKUP_SELECTED)
                ? BodyHandActionRequest.Action.ALTERNATE_TARGET : requested;
    }
}
