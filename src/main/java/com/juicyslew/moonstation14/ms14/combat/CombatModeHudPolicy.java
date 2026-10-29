package com.juicyslew.moonstation14.ms14.combat;

/** Pure layout and pointer-admission rules for the compact combat action. */
public final class CombatModeHudPolicy {
    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double px,double py) { return px>=x && px<x+width && py>=y && py<y+height; }
    }
    private CombatModeHudPolicy() { }
    public static Bounds bounds(int width,int height) {
        int size=Math.min(32,Math.max(1,Math.min(width-16,height-16)));
        return new Bounds(8,Math.max(0,height-size-8),size,size);
    }
    public static String icon(boolean enabled) { return enabled ? "on" : "off"; }
    /** Register for capability, not attachment presence; combat mode defaults to OFF without an attachment. */
    public static boolean shouldRegister(boolean capable) { return capable; }

    /** Keep the legacy control only until the current body's resolved first action slot is available. */
    public static boolean shouldShowLegacy(boolean capable,
                                           com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot snapshot,
                                           java.util.UUID bodyId, long bodyEpoch) {
        if (!capable) return false;
        if (snapshot == null || bodyId == null || bodyEpoch <= 0
                || !bodyId.equals(snapshot.bodyId()) || bodyEpoch != snapshot.bodyEpoch()) return true;
        return com.juicyslew.moonstation14.ms14.actions.client.BodyActionSlots.at(snapshot, 1) == null;
    }
    public static boolean admitted(boolean ready,boolean mouseGrabbed,boolean modal,boolean storageOwnsStream) {
        return ready && !mouseGrabbed && !modal && !storageOwnsStream;
    }
}
