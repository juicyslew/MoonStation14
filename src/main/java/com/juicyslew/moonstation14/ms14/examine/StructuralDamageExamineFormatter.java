package com.juicyslew.moonstation14.ms14.examine;

/** SS14 WindowMessages band selection: five nearest 20-percent bands (half-up), clamped. */
public final class StructuralDamageExamineFormatter {
    private StructuralDamageExamineFormatter() { }
    public static int band(int damage, int destructionThreshold) {
        if (damage <= 0 || destructionThreshold <= 0) return 0;
        double fraction = damage / (double) destructionThreshold;
        if (!Double.isFinite(fraction)) return 0;
        return Math.clamp((int) Math.floor(fraction * 5.0 + 0.5), 0, 5);
    }
}
