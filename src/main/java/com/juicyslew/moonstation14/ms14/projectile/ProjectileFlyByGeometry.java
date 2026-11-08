package com.juicyslew.moonstation14.ms14.projectile;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Minecraft adaptation: a three-dimensional expanded listener AABB swept by the projectile segment. */
public final class ProjectileFlyByGeometry {
    private ProjectileFlyByGeometry() { }

    public static boolean intersects(Vec3 from, Vec3 to, AABB listenerBounds, double range) {
        if (from == null || to == null || listenerBounds == null || !Double.isFinite(range) || range < 0
                || !finite(from) || !finite(to)) return false;
        return listenerBounds.inflate(range).clip(from, to).isPresent()
                || listenerBounds.inflate(range).contains(from);
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
