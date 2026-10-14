package com.juicyslew.moonstation14.ms14.hands.client;

import java.util.UUID;

/** Client hint ordering only; the server independently selects the actual world item. */
final class BodyPickupTargetOrder {
    private BodyPickupTargetOrder() { }

    static boolean precedes(double distance, UUID id, double bestDistance, UUID bestId) {
        return Double.isFinite(distance) && id != null
                && (distance < bestDistance || distance == bestDistance
                && bestId != null && id.compareTo(bestId) < 0);
    }
}

