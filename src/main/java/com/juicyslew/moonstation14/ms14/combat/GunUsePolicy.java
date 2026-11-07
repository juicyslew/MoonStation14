package com.juicyslew.moonstation14.ms14.combat;

/** Mirrors SharedGunSystem's use-delay admission: absent component is not ready. */
public final class GunUsePolicy {
    public enum Operation { REVOLVER, MAGAZINE, BALLISTIC, BATTERY_MODE, NONE }
    private GunUsePolicy() { }
    public static Operation operation(GunProfile profile) {
        if (profile == null) return Operation.NONE;
        if (profile.ballisticProvider().isPresent()) return Operation.BALLISTIC;
        if (profile.batteryFireModes().filter(modes -> modes.size() > 1).isPresent()) return Operation.BATTERY_MODE;
        if (profile.magazineChamberProvider().isPresent()) return Operation.MAGAZINE;
        if (profile.revolverAmmoProvider().isPresent()) return Operation.REVOLVER;
        return Operation.NONE;
    }
    public static boolean canCycle(boolean hasUseDelay, long now, long deadline) {
        return hasUseDelay && now >= deadline;
    }
    public static long deadline(long now, float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0 || now < 0) throw new IllegalArgumentException("invalid use delay");
        return now + (long) Math.ceil(seconds * 20d);
    }
}
