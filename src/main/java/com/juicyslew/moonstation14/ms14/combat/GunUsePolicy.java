package com.juicyslew.moonstation14.ms14.combat;

/** Mirrors SharedGunSystem's use-delay admission: absent component is not ready. */
public final class GunUsePolicy {
    private GunUsePolicy() { }
    public static boolean canCycle(boolean hasUseDelay, long now, long deadline) {
        return hasUseDelay && now >= deadline;
    }
    public static long deadline(long now, float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0 || now < 0) throw new IllegalArgumentException("invalid use delay");
        return now + (long) Math.ceil(seconds * 20d);
    }
}
