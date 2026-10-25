package com.juicyslew.moonstation14.entities.client;

/** Listener-range and source-lifecycle decision shared by the two independent ambient loops. */
public final class SingularityAmbientPolicy {
    private SingularityAmbientPolicy() { }
    public static boolean shouldPlay(boolean entityLive, boolean worldReady, boolean profileReady,
                                     double distanceSquared, double range) {
        return entityLive && worldReady && profileReady && Double.isFinite(distanceSquared)
                && Double.isFinite(range) && range >= 0 && distanceSquared <= range * range;
    }
}
