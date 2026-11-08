package com.juicyslew.moonstation14.ms14.tesla;

/** Pure authoritative energy transition for the main Tesla ball. */
public final class TeslaEnergyState {
    public enum Transition { NONE, SPAWN_MINI, COLLAPSE }
    public record Adjustment(double energy, Transition transition) { }

    private TeslaEnergyState() { }

    /** Applies a finite adjustment once; deliberately never loops when a large value crosses many thresholds. */
    public static Adjustment adjust(double energy, double delta, TeslaBallProfile.Energy rules) {
        if (!Double.isFinite(energy) || !Double.isFinite(delta) || rules == null)
            return new Adjustment(energy, Transition.NONE);
        double next = energy + delta;
        if (!Double.isFinite(next)) return new Adjustment(energy, Transition.NONE);
        if (next > rules.growthThreshold())
            return new Adjustment(next - rules.growthThreshold(), Transition.SPAWN_MINI);
        if (next < rules.collapseThreshold())
            return new Adjustment(next, Transition.COLLAPSE);
        return new Adjustment(next, Transition.NONE);
    }
}
