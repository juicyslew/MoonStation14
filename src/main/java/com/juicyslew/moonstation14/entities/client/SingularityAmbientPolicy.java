package com.juicyslew.moonstation14.entities.client;

/** Listener-range and source-lifecycle decision shared by the two independent ambient loops. */
public final class SingularityAmbientPolicy {
    public enum LoopAction { CREATE, REUSE, REPLACE, DISCARD }
    private SingularityAmbientPolicy() { }
    /** Lifecycle decision consumed by the client owners before updating an attached loop. */
    public static LoopAction loopAction(boolean ownerLive, boolean present, boolean stopped, boolean profileMatches) {
        if (!ownerLive) return present ? LoopAction.DISCARD : LoopAction.CREATE;
        if (!present) return LoopAction.CREATE;
        return stopped || !profileMatches ? LoopAction.REPLACE : LoopAction.REUSE;
    }
    public static boolean shouldPlay(boolean entityLive, boolean worldReady, boolean profileReady,
                                     double distanceSquared, double range) {
        return entityLive && worldReady && profileReady && Double.isFinite(distanceSquared)
                && Double.isFinite(range) && range >= 0 && distanceSquared <= range * range;
    }
}
