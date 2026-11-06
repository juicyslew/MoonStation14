package com.juicyslew.moonstation14.ms14.shuttle.sable;

/** Pure stock-compatible sampling arithmetic for relative actor/frame sweep. */
public final class TrackedCollisionSubsteps {
    private TrackedCollisionSubsteps() { }

    public static int required(int stock, double relativeSweep) {
        if (stock < 1) stock = 1;
        if (stock >= 10) return 10;
        if (!Double.isFinite(relativeSweep) || relativeSweep <= 0) return stock;
        double sampled = Math.ceil(relativeSweep / (0.25 / 16.0));
        if (!Double.isFinite(sampled) || sampled >= 10) return 10;
        return Math.max(stock, (int) sampled);
    }

    /** Request minus SDK-transformed actor-point frame delta; unknown endpoints must not be supplied as motion. */
    public static double relativeSweep(double requestX, double requestY, double requestZ,
                                       double frameX, double frameY, double frameZ) {
        if (!Double.isFinite(requestX) || !Double.isFinite(requestY) || !Double.isFinite(requestZ)
                || !Double.isFinite(frameX) || !Double.isFinite(frameY) || !Double.isFinite(frameZ)) return Double.NaN;
        return Math.hypot(Math.hypot(requestX - frameX, requestY - frameY), requestZ - frameZ);
    }
}
