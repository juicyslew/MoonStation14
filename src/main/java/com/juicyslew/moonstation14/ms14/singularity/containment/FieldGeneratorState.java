package com.juicyslew.moonstation14.ms14.singularity.containment;

/** Immutable transition state. A hit is the only event that resets decay elapsed time. */
public record FieldGeneratorState(int charge, int decayElapsedTicks, boolean anchored, boolean enabled) {
    public FieldGeneratorState {
        if (charge < 0 || decayElapsedTicks < 0) throw new IllegalArgumentException("Negative field generator state");
        if (enabled && !anchored) throw new IllegalArgumentException("Enabled generator must be anchored");
    }
    public static FieldGeneratorState empty() { return new FieldGeneratorState(0, 0, false, false); }
    public FieldGeneratorState receiveValidBolt(FieldGeneratorProfile p) {
        return new FieldGeneratorState(Math.min(p.maxCharge(), charge + p.boltCharge()), 0, anchored, enabled);
    }
    public FieldGeneratorState tick(FieldGeneratorProfile p, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative ticks");
        if (charge == 0) return this;
        long elapsed = (long) decayElapsedTicks + ticks;
        int decayCount = (int) (elapsed / p.decayIntervalTicks());
        return new FieldGeneratorState(Math.max(0, charge - decayCount * p.decayAmount()),
                (int) (elapsed % p.decayIntervalTicks()), anchored, enabled);
    }
    /** Upstream retains an edge while either endpoint remains charge-valid. */
    public static boolean connectionValid(FieldGeneratorState a, FieldGeneratorState b, FieldGeneratorProfile p) {
        return a.anchored && b.anchored && (a.charge >= p.connectionThreshold() || b.charge >= p.connectionThreshold());
    }
    public FieldGeneratorState setEnabled(boolean value, boolean hasConnection) {
        if (value && !anchored) return this;
        if (!value && enabled && hasConnection) return this;
        return new FieldGeneratorState(charge, decayElapsedTicks, anchored, value);
    }
    public FieldGeneratorState setAnchored(boolean value, boolean hasConnection) {
        if (!value && (enabled || hasConnection)) return this;
        return new FieldGeneratorState(charge, decayElapsedTicks, value, enabled);
    }
}

