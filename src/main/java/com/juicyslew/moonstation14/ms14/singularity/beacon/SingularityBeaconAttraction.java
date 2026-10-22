package com.juicyslew.moonstation14.ms14.singularity.beacon;

import net.minecraft.world.phys.Vec3;

/** SS14 SingularityAttractor pulse vector: normalize(toward source) * (baseRange / distance). */
public final class SingularityBeaconAttraction {
    private SingularityBeaconAttraction() { }
    public static Vec3 bias(Vec3 source, Vec3 singularity, double baseRange) {
        if (source == null || singularity == null || !Double.isFinite(baseRange) || baseRange < 0) return Vec3.ZERO;
        Vec3 delta = source.subtract(singularity);
        double distance = delta.length();
        if (!Double.isFinite(distance) || distance <= 1.0e-5 || baseRange == 0) return Vec3.ZERO;
        double scale = baseRange / (distance * distance);
        Vec3 bias = delta.scale(scale);
        return Double.isFinite(bias.x) && Double.isFinite(bias.y) && Double.isFinite(bias.z) ? bias : Vec3.ZERO;
    }
}
