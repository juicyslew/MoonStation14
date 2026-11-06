package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.joml.Vector3dc;

/** Admin-fixture target expressed in world-space SI units; not a gameplay setting. */
public record ShuttleMotionRequest(double targetX, double targetY, double targetZ,
                                   double targetYawRate, int durationTicks) {
    public static final int MIN_DURATION_TICKS = 1;
    public static final int MAX_DURATION_TICKS = 2400;

    public ShuttleMotionRequest {
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)
                || !Double.isFinite(targetYawRate)
                || durationTicks < MIN_DURATION_TICKS || durationTicks > MAX_DURATION_TICKS)
            throw new IllegalArgumentException("Invalid fixture motion request");
    }

    public static ShuttleMotionRequest from(Vector3dc worldTarget, double targetYawRate, int durationTicks) {
        if (worldTarget == null) throw new IllegalArgumentException("Missing world target");
        return new ShuttleMotionRequest(worldTarget.x(), worldTarget.y(), worldTarget.z(), targetYawRate, durationTicks);
    }
}
