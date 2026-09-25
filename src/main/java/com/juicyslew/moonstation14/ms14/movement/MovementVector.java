package com.juicyslew.moonstation14.ms14.movement;

/** Immutable finite vector. Position is in blocks; velocity is blocks per second. */
public record MovementVector(double x, double y, double z) {
    public static final MovementVector ZERO = new MovementVector(0d, 0d, 0d);

    public MovementVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("movement vector components must be finite");
        }
    }

    public MovementVector add(MovementVector other) {
        return new MovementVector(x + other.x, y + other.y, z + other.z);
    }

    public MovementVector scale(double scale) {
        if (!Double.isFinite(scale)) throw new IllegalArgumentException("scale must be finite");
        return new MovementVector(x * scale, y * scale, z * scale);
    }

    public double horizontalLength() {
        return Math.hypot(x, z);
    }
}
