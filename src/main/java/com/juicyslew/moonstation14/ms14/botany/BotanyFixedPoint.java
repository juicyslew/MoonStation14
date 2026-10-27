package com.juicyslew.moonstation14.ms14.botany;

/** Narrow adapter for SS14 FixedPoint2.New(float); unrelated reagent conversions remain unchanged. */
public final class BotanyFixedPoint {
    private BotanyFixedPoint() { }
    public static final long MAX_CENTS = Integer.MAX_VALUE;
    public static long fromFloat(float value) {
        if (!Float.isFinite(value) || value < 0f) throw new IllegalArgumentException("botany fixed-point value must be finite and nonnegative");
        float scaled = value * 100f + 1.0e-5f;
        if (scaled > Integer.MAX_VALUE) throw new IllegalArgumentException("botany fixed-point value exceeds source range");
        return (int) scaled;
    }
    public static float toFloat(long cents) {
        if (cents < 0 || cents > MAX_CENTS) throw new IllegalArgumentException("botany fixed-point cents outside source range");
        return cents / 100f;
    }
}
