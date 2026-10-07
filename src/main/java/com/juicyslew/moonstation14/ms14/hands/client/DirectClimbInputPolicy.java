package com.juicyslew.moonstation14.ms14.hands.client;

/** Resolves the shared K binding without stealing prone-roll or body-turn gestures. */
public final class DirectClimbInputPolicy {
    private DirectClimbInputPolicy() { }

    public static boolean mayClimb(boolean sharesRollKey, boolean bodyDown,
                                    boolean shift, boolean control, boolean alt) {
        if (!sharesRollKey) return true;
        return !bodyDown && !shift && !control && !alt;
    }
}
