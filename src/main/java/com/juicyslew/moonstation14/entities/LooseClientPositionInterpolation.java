package com.juicyslew.moonstation14.entities;

/** Client-only, one-tick authoritative position buffer; contains no entity simulation. */
public final class LooseClientPositionInterpolation {
    private static final double SNAP_DISTANCE_SQUARED = 16.0;
    private double targetX, targetY, targetZ;
    private boolean pending;

    /** Latest packet wins; large corrections snap to avoid a streak across the world. */
    public boolean stage(double currentX, double currentY, double currentZ, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        targetX = x; targetY = y; targetZ = z; pending = true;
        double dx = x - currentX, dy = y - currentY, dz = z - currentZ;
        if (dx * dx + dy * dy + dz * dz > SNAP_DISTANCE_SQUARED) {
            pending = false;
            return true;
        }
        return false;
    }

    public double targetX(double current) { return pending ? targetX : current; }
    public double targetY(double current) { return pending ? targetY : current; }
    public double targetZ(double current) { return pending ? targetZ : current; }
    public boolean pending() { return pending; }

    /** Applies the entire latest server position in one client tick, then clears it. */
    public double[] advance(double currentX, double currentY, double currentZ) {
        if (!pending) return new double[]{currentX, currentY, currentZ};
        pending = false;
        return new double[]{targetX, targetY, targetZ};
    }
}

