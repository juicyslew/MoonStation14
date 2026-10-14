package com.juicyslew.moonstation14.ms14.explosion.core;

/**
 * Pure Minecraft translation of SS14's outward knockback direction and 10*sqrt(intensity) target speed.
 * This is not an SS14 prototype field: the pinned Default prototype has no knockback setting. Here SS14
 * speed units are explicitly converted at 0.1 blocks/tick per unit and capped at 1.5 blocks/tick.
 */
public final class BlastKnockbackPolicy {
    public static final double MAX_INTENSITY = 10.0;
    public static final double SS14_UNITS_TO_BLOCKS_PER_TICK = 0.1;
    public static final double MAX_SPEED = 1.5;

    private BlastKnockbackPolicy() { }

    public record Velocity(double x, double y, double z) { }
    public record Result(boolean applicable, Velocity velocity) { }

    /** Returns an unapplied zero velocity for invalid inputs or zero intensity. Never mutates world/entity state. */
    public static Result calculate(double intensity,
                                   double originX, double originY, double originZ,
                                   double targetX, double targetY, double targetZ) {
        Velocity zero = new Velocity(0, 0, 0);
        if (!Double.isFinite(intensity) || intensity < 0 || intensity > MAX_INTENSITY
                || !Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                || !Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)
                || intensity == 0) return new Result(false, zero);

        double dx = targetX - originX;
        double dy = targetY - originY;
        double dz = targetZ - originZ;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz))
            return new Result(false, zero);
        // Scale before length calculation to avoid overflow for otherwise finite, widely separated coordinates.
        double scale = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        double nx, ny, nz;
        if (scale == 0) {
            // Deterministic outward-direction fallback for coincident positions.
            nx = 1;
            ny = 0;
            nz = 0;
        } else {
            double sx = dx / scale, sy = dy / scale, sz = dz / scale;
            double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
            nx = sx / length;
            ny = sy / length;
            nz = sz / length;
        }
        double speed = Math.min(MAX_SPEED, SS14_UNITS_TO_BLOCKS_PER_TICK * 10.0 * Math.sqrt(intensity));
        return new Result(true, new Velocity(nx * speed, ny * speed, nz * speed));
    }
}

