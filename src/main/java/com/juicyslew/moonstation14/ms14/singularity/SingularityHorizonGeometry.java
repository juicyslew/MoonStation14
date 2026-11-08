package com.juicyslew.moonstation14.ms14.singularity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Exact closest-point test for sphere contact against a physical body AABB. */
public final class SingularityHorizonGeometry {
    private SingularityHorizonGeometry() { }
    public static boolean touches(Vec3 center, double radius, AABB body) {
        if (center == null || body == null || !Double.isFinite(radius) || radius < 0) return false;
        double x = net.minecraft.util.Mth.clamp(center.x, body.minX, body.maxX);
        double y = net.minecraft.util.Mth.clamp(center.y, body.minY, body.maxY);
        double z = net.minecraft.util.Mth.clamp(center.z, body.minZ, body.maxZ);
        return center.distanceToSqr(x, y, z) <= radius * radius;
    }
}
