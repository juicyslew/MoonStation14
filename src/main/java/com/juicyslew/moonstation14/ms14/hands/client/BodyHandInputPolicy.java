package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;

/** Pure decisions shared by live body-owned input handlers and focused policy tests. */
final class BodyHandInputPolicy {
    private BodyHandInputPolicy() { }

    static boolean ownsPrimary(boolean leftPress, boolean screenClosed, boolean mouseGrabbed,
                               boolean committedBody, long committedEpoch) {
        return leftPress && screenClosed && mouseGrabbed && committedBody && committedEpoch > 0;
    }

    static boolean cancelsVanillaAttack(PrimaryWorldGesture.Route route) {
        return route != PrimaryWorldGesture.Route.VANILLA;
    }

    static BodyHandActionRequest.Action dropAction(boolean dropPressed, boolean controlDown) {
        if (!dropPressed) return null;
        return controlDown ? BodyHandActionRequest.Action.THROW_ITEM : BodyHandActionRequest.Action.DROP;
    }
}

