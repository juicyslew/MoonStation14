package com.juicyslew.moonstation14.ms14.movement;

/**
 * Server-startup latch for opting into experimental vertical-slice movement.
 * This is only permission to use that movement: it does not enroll players or
 * replace the vanilla path when no authenticated protocol session is active.
 */
public final class MovementStartupGate {
    private static volatile boolean enabled;

    private MovementStartupGate() {
    }

    /**
     * Returns the server-startup decision. The gate is closed before server
     * startup and after the server stops; configuration edits do not change it
     * during a running session.
     */
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
