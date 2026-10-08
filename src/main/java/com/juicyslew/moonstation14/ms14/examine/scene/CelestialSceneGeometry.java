package com.juicyslew.moonstation14.ms14.examine.scene;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared coordinates for sky drawing and examine ray hits; discs match the renderer's 32-segment fan. */
public final class CelestialSceneGeometry {
    public static final int SEGMENTS = 32;
    private CelestialSceneGeometry() { }
    /** Shared scene gate: only the configured active Overworld sky and currently rendered body are eligible. */
    public static boolean available(net.minecraft.world.level.Level level, CelestialSceneData profile, boolean critical) {
        return level != null && profile != null && !critical
                && level.dimension().location().toString().equals(profile.dimension())
                && net.minecraft.world.level.Level.OVERWORLD.location().toString().equals(profile.dimension())
                && com.juicyslew.moonstation14.Config.shouldRenderMoonSky(level.dimension());
    }
    /** Renderer-local center plane Y. Keep scene side/distance composed in one place. */
    public static float localPlaneY(CelestialSceneData profile) {
        return (float)profile.skyDistance() * (profile.side() == CelestialSceneData.Side.SUN ? 1 : -1);
    }
    /** Small local-plane displacement for the Earth artwork, toward the camera-facing origin. */
    public static float cameraFacingArtPlaneY(CelestialSceneData profile) {
        float plane = localPlaneY(profile);
        return plane - Math.copySign(0.1f, plane);
    }
    public static Vec3 center(CelestialSceneData profile, double timeOfDay) {
        float phase = (float)(timeOfDay * Math.PI * 2);
        float y = localPlaneY(profile);
        Vector3f p = new Vector3f(0, y, 0).rotateX(phase).rotateY((float)Math.toRadians(-90));
        return new Vec3(p.x, p.y, p.z);
    }
    /** Direction to the highest point in the rendered silhouette, which remains above the horizon while partly set. */
    public static Vec3 visibleDirection(CelestialSceneData profile, double timeOfDay) {
        if (profile == null) return null;
        Matrix4f matrix = transform(timeOfDay);
        float y = localPlaneY(profile);
        Vec3 best = null;
        for (int i = 0; i < SEGMENTS; i++) {
            double angle = Math.PI * 2 * i / SEGMENTS;
            Vector3f local = new Vector3f((float)Math.cos(angle) * (float)profile.radius(), y,
                    (float)Math.sin(angle) * (float)profile.radius());
            Vector3f point = matrix.transformPosition(local);
            if (point.y > 0 && (best == null || point.y > best.y)) best = new Vec3(point.x, point.y, point.z);
        }
        Vec3 c = center(profile, timeOfDay);
        if (c.y > 0 && (best == null || c.y > best.y)) best = c;
        return best == null ? null : best.normalize();
    }
    public static Matrix4f transform(double timeOfDay) {
        return new Matrix4f().rotateY((float)Math.toRadians(-90)).rotateX((float)(timeOfDay * Math.PI * 2));
    }
    /** Returns the nearest fan-triangle intersection only on the rendered, above-horizon portion. */
    public static Vec3 intersect(CelestialSceneData profile, double timeOfDay, Vec3 origin, Vec3 direction) {
        Matrix4f transform = transform(timeOfDay);
        Matrix4f inverse = new Matrix4f(transform).invert();
        Vec3 o = local(inverse, origin), d = localDirection(inverse, direction);
        double y = localPlaneY(profile);
        if (Math.abs(d.y) < 1e-10) return null;
        double t = (y - o.y) / d.y;
        if (t < 0) return null;
        double x = o.x + d.x*t, z = o.z + d.z*t;
        double angle = Math.atan2(z, x);
        double sector = Math.PI * 2 / SEGMENTS;
        // The 32-segment fan vertices lie at multiples of sector; the polygon apothem
        // is measured from the midpoint between adjacent vertices.
        double offset = Math.IEEEremainder(angle - sector / 2, sector);
        double edge = profile.radius() * Math.cos(sector / 2) / Math.cos(offset);
        // The renderer emits float vertices through a float matrix; allow only a few float ULPs
        // at the mathematical boundary so a rendered edge is not lost to inverse-transform rounding.
        double edgeTolerance = Math.ulp((float)profile.radius()) * 2.0;
        if (Math.hypot(x, z) > edge + edgeTolerance) return null;
        Vec3 world = origin.add(direction.scale(t));
        return world.y > 0 ? world : null;
    }
    /** Camera sky rays are camera-relative; world translation must never translate the virtual sky scene. */
    public static Vec3 intersectCameraRay(CelestialSceneData profile, double timeOfDay,
                                          Vec3 cameraWorldPosition, Vec3 direction) {
        if (cameraWorldPosition == null) return null;
        return intersect(profile, timeOfDay, Vec3.ZERO, direction);
    }
    private static Vec3 local(Matrix4f matrix, Vec3 v) { Vector3f r=matrix.transformPosition((float)v.x,(float)v.y,(float)v.z,new Vector3f()); return new Vec3(r.x,r.y,r.z); }
    private static Vec3 localDirection(Matrix4f matrix, Vec3 v) { Vector3f r=matrix.transformDirection((float)v.x,(float)v.y,(float)v.z,new Vector3f()); return new Vec3(r.x,r.y,r.z); }
}
