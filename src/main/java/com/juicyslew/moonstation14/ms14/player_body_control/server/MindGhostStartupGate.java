package com.juicyslew.moonstation14.ms14.player_body_control.server;

/** Server-startup latch for the experimental operator-only Mind ghost control. */
public final class MindGhostStartupGate {
    private static volatile boolean enabled;

    private MindGhostStartupGate() {
    }

    /** Configuration edits do not change the decision for a running server. */
    public static boolean enabledForServer() {
        return enabled;
    }

    public static void onServerStarting(boolean configuredEnabled) {
        enabled = configuredEnabled;
    }

    public static void onServerStopped() {
        enabled = false;
    }
}
