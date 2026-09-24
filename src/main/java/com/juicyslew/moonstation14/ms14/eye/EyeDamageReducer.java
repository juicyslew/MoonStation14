package com.juicyslew.moonstation14.ms14.eye;

import java.util.Objects;

/** Pure eye-damage arithmetic; entity persistence belongs to {@link EyeDamageSystem}. */
public final class EyeDamageReducer {
    private EyeDamageReducer() {
    }

    /**
     * Applies one admitted amount.  The floor is intentional: negative half
     * amounts round away from zero, matching the SS14 effect's asymmetry.
     */
    public static EyeDamageComponent apply(EyeDamageComponent current, int amount, float scale) {
        Objects.requireNonNull(current, "current");
        long delta = scaledDelta(amount, scale);
        if (delta == 0L) {
            return current;
        }

        final long total;
        try {
            total = Math.addExact((long) current.damage(), delta);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("eye damage total overflow", overflow);
        }
        int next = (int) Math.max(EyeDamageComponent.MIN_DAMAGE,
                Math.min(EyeDamageComponent.MAX_DAMAGE, total));
        return next == current.damage() ? current : new EyeDamageComponent(next);
    }

    /** Returns floor(amount * scale), rejecting non-finite or long-out-of-range results. */
    public static long scaledDelta(int amount, float scale) {
        if (!Float.isFinite(scale) || scale < 0f) {
            throw new IllegalArgumentException("eye damage scale must be finite and nonnegative");
        }
        // Keep the upstream float multiplication and its rounding before floor.
        float product = amount * scale;
        if (!Float.isFinite(product)) {
            throw new IllegalArgumentException("eye damage product must be finite");
        }
        double floored = Math.floor(product);
        if (floored < Long.MIN_VALUE || floored > Long.MAX_VALUE) {
            throw new IllegalArgumentException("eye damage delta is outside long range");
        }
        return (long) floored;
    }
}
