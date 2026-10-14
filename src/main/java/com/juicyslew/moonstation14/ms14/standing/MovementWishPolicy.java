package com.juicyslew.moonstation14.ms14.standing;

/** Notify on changed wish (including stop) and bounded nonzero keepalives. */
public final class MovementWishPolicy {
    private MovementWishPolicy() { }
    public static boolean shouldNotify(int currentMask, int previousMask, long now, long lastNotify, long interval) {
        return currentMask != previousMask || currentMask != 0 && now - lastNotify >= interval;
    }
}

