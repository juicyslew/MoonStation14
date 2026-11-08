package com.juicyslew.moonstation14.ms14.hands.client;

/** Gives the configured actions-menu chord deterministic priority over climb. */
public final class DirectClimbInputPolicy {
    private DirectClimbInputPolicy() { }

    public static boolean mayClimb(boolean sharesRollKey, boolean bodyDown,
                                    boolean shift, boolean control, boolean alt) {
        if (!sharesRollKey) return true;
        return !bodyDown && !shift && !control && !alt;
    }

    public static void discardMenuOwnedClick(boolean menuOwnsChord, Runnable discard) {
        if (menuOwnsChord) discard.run();
    }
}
