package com.juicyslew.moonstation14.ms14.examine.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small deterministic ray/AABB kernel shared by production selection and focused tests. */
public final class ExamineRay {
    public record Hit(double distance, Vec3 point) { }
    private ExamineRay() { }

    public static Vec3 normalized(Vec3 direction) {
        if (direction == null || !Double.isFinite(direction.x) || !Double.isFinite(direction.y)
                || !Double.isFinite(direction.z) || direction.lengthSqr() < 1.0e-12) return Vec3.ZERO;
        return direction.normalize();
    }

    public static Hit intersect(Vec3 origin, Vec3 direction, double range, AABB box) {
        Vec3 ray = normalized(direction);
        if (ray.lengthSqr() < 1.0e-12 || origin == null || box == null || !Double.isFinite(range) || range < 0) return null;
        double near = 0, far = range;
        double[] o = {origin.x, origin.y, origin.z}, d = {ray.x, ray.y, ray.z};
        double[] lo = {box.minX, box.minY, box.minZ}, hi = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1.0e-12) {
                if (o[axis] < lo[axis] || o[axis] > hi[axis]) return null;
                continue;
            }
            double a = (lo[axis] - o[axis]) / d[axis], b = (hi[axis] - o[axis]) / d[axis];
            if (a > b) { double swap = a; a = b; b = swap; }
            near = Math.max(near, a); far = Math.min(far, b);
            if (near > far) return null;
        }
        return near <= range ? new Hit(near, origin.add(ray.scale(near))) : null;
    }
}

