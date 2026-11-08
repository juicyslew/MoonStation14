package com.juicyslew.moonstation14.ms14.mobs;

/** Pure compatibility mapping between typed damage and Minecraft's native health. */
public final class MobNativeHealthProjection {
    private MobNativeHealthProjection() { }

    public static Result project(double deadThreshold, double typedDamage, boolean deadAllowed,
                                 boolean savedDead, boolean nativeDead, boolean hasLedger) {
        if (!Double.isFinite(deadThreshold) || deadThreshold <= 0 || !Double.isFinite(typedDamage))
            return new Result(0, 0, false);
        double max = Math.max(1, deadThreshold / 5d);
        boolean lockedDead = savedDead || (nativeDead && !hasLedger);
        double damage = Math.max(0, typedDamage);
        double health = lockedDead ? 0 : !deadAllowed ? Math.max(1, Math.min(max, max - damage / 5d))
                : damage >= deadThreshold ? 0 : Math.max(0.0001, Math.min(max, max - damage / 5d));
        return new Result(max, health, lockedDead);
    }

    /** Selects the native-hurt bypass only for a retained, typed-dead server corpse. */
    public static boolean shouldCommitRetainedDead(boolean serverSide, boolean removed,
                                                    boolean currentIdentity, boolean typedDead,
                                                    boolean nativeHealthZero) {
        return serverSide && !removed && currentIdentity && typedDead && nativeHealthZero;
    }

    public record Result(double maxHealth, double health, boolean lockedDead) { }
}

