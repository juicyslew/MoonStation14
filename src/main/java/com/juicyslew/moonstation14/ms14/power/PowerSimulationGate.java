package com.juicyslew.moonstation14.ms14.power;

/** Startup-sampled switch for server power simulation; cable content and persistence are independent. */
public final class PowerSimulationGate {
    private static boolean enabled = true;

    private PowerSimulationGate() { }

    public static boolean isEnabled() { return enabled; }

    public static void configureAtServerStart(boolean enable) { enabled = enable; }

    /** Restore the default for the next integrated/dedicated server lifecycle. */
    public static void onServerStopped() { enabled = true; }
}
