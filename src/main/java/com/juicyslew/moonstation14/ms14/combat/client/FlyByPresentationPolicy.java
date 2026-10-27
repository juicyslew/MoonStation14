package com.juicyslew.moonstation14.ms14.combat.client;

/** Per-projectile entry/chance decision; caller owns the single inside bit. */
public final class FlyByPresentationPolicy {
    private FlyByPresentationPolicy() { }

    public static boolean entered(boolean wasInside, boolean nowInside, boolean ownShot, float probability, float roll) {
        return !ownShot && !wasInside && nowInside && Float.isFinite(probability)
                && probability >= 0f && probability <= 1f && Float.isFinite(roll)
                && roll >= 0f && roll < probability;
    }
}
