package com.juicyslew.moonstation14.ms14.singularity.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Conservative cone test for bounds that may be revealed by the active world lens. */
public final class SingularityWorldLensCulling {
    private SingularityWorldLensCulling() { }

    public static boolean sourceConeIntersects(AABB bounds, SingularityVisualPreview.WorldSource source, Vec3 camera) {
        if (bounds == null || source == null || camera == null || source.profile() == null
                || source.profile().lensStrength() * source.visibility() <= 0) return false;
        Vec3 core = source.worldPosition();
        double dx = core.x - camera.x, dy = core.y - camera.y, dz = core.z - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance) || distance <= 1e-9) return false;
        double nx = dx / distance, ny = dy / distance, nz = dz / distance;
        double bx = bounds.getCenter().x - camera.x, by = bounds.getCenter().y - camera.y, bz = bounds.getCenter().z - camera.z;
        double s = bx * nx + by * ny + bz * nz;
        double vx = bx - nx * s, vy = by - ny * s, vz = bz - nz * s;
        double radial = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double rho = Math.sqrt(bounds.getXsize() * bounds.getXsize() + bounds.getYsize() * bounds.getYsize()
                + bounds.getZsize() * bounds.getZsize()) * .5;
        double front = s + rho;
        double foreground = source.profile().foregroundRampWorldUnits();
        if (!Double.isFinite(front) || !Double.isFinite(rho) || !Double.isFinite(foreground)
                || front <= Math.max(0, distance - foreground)) return false;
        double closestRadial = Math.max(0, radial - rho);
        return closestRadial * distance / front <= source.profile().cutoffWorldUnits();
    }
}
