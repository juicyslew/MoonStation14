package com.juicyslew.moonstation14.ms14.interaction;

/** Minecraft block reach derived from the ordinary SS14 interaction reach. */
public final class InteractionReach {
    public static final double SS14_DEFAULT = 1.5;
    public static final double MINECRAFT_ADDITION = 1.5;
    public static final double DEFAULT_BLOCKS = fromSs14(SS14_DEFAULT);

    private InteractionReach() { }

    /** Preserve a feature's SS14 baseline while applying the shared flat Minecraft reach offset. */
    public static double fromSs14(double baseReach) {
        return Double.isFinite(baseReach) && baseReach >= 0 ? baseReach + MINECRAFT_ADDITION : Double.NaN;
    }
}

