package com.juicyslew.moonstation14.ms14.movement;

/** Physical parameters for optional gravity-air control supplied by an environment adapter. */
public record GravityAirControl(double referenceTickSeconds, double horizontalRetentionPerTick,
                                double inputAccelerationPerSecondSquared) {
    public GravityAirControl {
        if (!Double.isFinite(referenceTickSeconds) || referenceTickSeconds <= 0d)
            throw new IllegalArgumentException("referenceTickSeconds must be finite and positive");
        if (!Double.isFinite(horizontalRetentionPerTick) || horizontalRetentionPerTick <= 0d
                || horizontalRetentionPerTick > 1d)
            throw new IllegalArgumentException("horizontalRetentionPerTick must be finite and in (0, 1]");
        if (!Double.isFinite(inputAccelerationPerSecondSquared) || inputAccelerationPerSecondSquared < 0d)
            throw new IllegalArgumentException("inputAccelerationPerSecondSquared must be finite and nonnegative");
    }

    public double retentionFor(double secondsPerTick) {
        if (!Double.isFinite(secondsPerTick) || secondsPerTick <= 0d)
            throw new IllegalArgumentException("secondsPerTick must be finite and positive");
        return Math.pow(horizontalRetentionPerTick, secondsPerTick / referenceTickSeconds);
    }
}
