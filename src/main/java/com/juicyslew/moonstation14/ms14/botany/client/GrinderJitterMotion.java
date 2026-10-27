package com.juicyslew.moonstation14.ms14.botany.client;

import java.util.random.RandomGenerator;

/** Client-only SS14 jitter timing; source XY maps to Minecraft's horizontal XZ plane. */
public final class GrinderJitterMotion {
    private double x, z, targetX, targetZ, segmentElapsed;
    private boolean initialized;
    private int lastSignX, lastSignZ;
    public record Offset(double x, double z) { }
    int signX() { return lastSignX; }
    int signZ() { return lastSignZ; }
    double targetX() { return targetX; }
    double targetZ() { return targetZ; }

    public Offset sample(double deltaSeconds, RandomGenerator random) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0) throw new IllegalArgumentException("deltaSeconds");
        if (!initialized) { initialized = true; chooseTarget(random); }
        double remaining = deltaSeconds;
        while (remaining >= 0.01 - segmentElapsed) {
            double step = 0.01 - segmentElapsed;
            remaining -= step;
            segmentElapsed = 0;
            x = targetX; z = targetZ;
            chooseTarget(random);
        }
        segmentElapsed += remaining;
        double t = segmentElapsed / .01;
        return new Offset(x + (targetX - x) * t, z + (targetZ - z) * t);
    }

    private void chooseTarget(RandomGenerator random) {
        double amp = .09;
        double nx = (.25 + random.nextDouble() * .75) * amp;
        double nz = (.25 + random.nextDouble() / 12d) * amp;
        if (random.nextBoolean()) nx = -nx;
        if (random.nextBoolean()) nz = -nz;
        int sx = nx < 0 ? -1 : 1, sz = nz < 0 ? -1 : 1;
        // SS14 compares signs with OR and flips a randomly selected axis only on a match.
        if ((sx == lastSignX || sz == lastSignZ) && random.nextBoolean()) nx = -nx;
        else if (sx == lastSignX || sz == lastSignZ) nz = -nz;
        targetX = nx; targetZ = nz;
        lastSignX = nx < 0 ? -1 : 1; lastSignZ = nz < 0 ? -1 : 1;
    }
}
