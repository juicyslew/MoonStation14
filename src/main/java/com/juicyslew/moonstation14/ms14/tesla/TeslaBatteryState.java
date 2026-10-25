package com.juicyslew.moonstation14.ms14.tesla;

/** Persistent finite energy store and allocation settlement for a Tesla coil. */
public final class TeslaBatteryState {
    private double energyJoules;

    public TeslaBatteryState() { }
    public TeslaBatteryState(double energyJoules) { this.energyJoules = Double.isFinite(energyJoules) ? Math.max(0, energyJoules) : 0; }

    public double energyJoules() { return energyJoules; }

    public void restore(double joules, double capacityJoules) {
        energyJoules = Double.isFinite(joules) && Double.isFinite(capacityJoules)
                ? Math.max(0, Math.min(joules, Math.max(0, capacityJoules))) : 0;
    }

    public double offerWatts(double requestedWatts, long elapsedTicks) {
        if (!Double.isFinite(requestedWatts) || requestedWatts <= 0 || elapsedTicks <= 0 || energyJoules <= 0) return 0;
        return Math.min(requestedWatts, energyJoules / (elapsedTicks / 20.0));
    }

    public boolean charge(double joules, double capacityJoules) {
        if (!Double.isFinite(joules) || joules <= 0 || !Double.isFinite(capacityJoules) || capacityJoules <= 0) return false;
        double before = energyJoules;
        energyJoules = Math.min(capacityJoules, before + joules);
        return energyJoules > before;
    }

    /** Deducts only an actual, known delivered allocation. */
    public double settle(double actualWatts, boolean graphKnown, long elapsedTicks) {
        if (!graphKnown || elapsedTicks <= 0 || !Double.isFinite(actualWatts) || actualWatts <= 0) return 0;
        double used = Math.min(energyJoules, actualWatts * elapsedTicks / 20.0);
        energyJoules -= used;
        if (energyJoules < 1e-8) energyJoules = 0;
        return used;
    }
}
