package com.juicyslew.moonstation14.ms14.slip;

/** Explicit Minecraft physics mapping for an SS14 friction factor. */
public final class MinecraftSlidingPhysics {
    private MinecraftSlidingPhysics() { }

    public static double acceleration(double vanillaAcceleration, double frictionFactor) {
        requireFactor(frictionFactor);
        if (!Double.isFinite(vanillaAcceleration) || vanillaAcceleration < 0d) {
            throw new IllegalArgumentException("vanilla acceleration must be finite and nonnegative");
        }
        return vanillaAcceleration * frictionFactor;
    }

    public static double groundHorizontalRetention(double vanillaRetention, double frictionFactor) {
        requireFactor(frictionFactor);
        if (!Double.isFinite(vanillaRetention) || vanillaRetention < 0d || vanillaRetention > 1d) {
            throw new IllegalArgumentException("vanilla retention must be finite and in [0, 1]");
        }
        return Math.pow(vanillaRetention, frictionFactor);
    }

    private static void requireFactor(double frictionFactor) {
        if (!Double.isFinite(frictionFactor) || frictionFactor < 0d) {
            throw new IllegalArgumentException("friction factor must be finite and nonnegative");
        }
    }
}
