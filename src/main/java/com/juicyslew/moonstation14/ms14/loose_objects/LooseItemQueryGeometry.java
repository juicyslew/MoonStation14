package com.juicyslew.moonstation14.ms14.loose_objects;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Rotated physical-profile proxy for loose-item queries; not an exact visual mesh envelope. */
public final class LooseItemQueryGeometry {
    private final double width, height;
    private final Vec3 pivot;
    private final Quaterniond orientation;

    /** Conservative radial reach of any resolved loose-object query profile. */
    public static double maximumProfileRadius(boolean client) {
        final var catalog = client
                ? com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientLooseObjectProfiles()
                : com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverLooseObjectProfiles();
        return maximumProfileRadius(catalog);
    }

    /** Catalog-snapshot calculation shared by the live server/client path and focused prototype tests. */
    static double maximumProfileRadius(com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<LooseObjectProfile> catalog) {
        double maximum = 0;
        if (catalog == null) return 0;
        for (LooseObjectProfile profile : catalog.values()) {
            if (profile == null) continue;
            double halfWidth = profile.physicalWidth() * .5;
            double radius = Math.sqrt(2 * halfWidth * halfWidth
                    + (double) profile.physicalHeight() * profile.physicalHeight());
            if (Double.isFinite(radius)) maximum = Math.max(maximum, radius);
        }
        return maximum;
    }

    public LooseItemQueryGeometry(double width, double height, Vec3 pivot, Quaterniondc orientation) {
        if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0
                || !finite(pivot) || !finite(orientation)) throw new IllegalArgumentException("invalid loose query shape");
        this.width = width;
        this.height = height;
        this.pivot = pivot;
        double norm = orientation.x()*orientation.x()+orientation.y()*orientation.y()
                +orientation.z()*orientation.z()+orientation.w()*orientation.w();
        if (!Double.isFinite(norm) || norm < 1.0e-20) throw new IllegalArgumentException("invalid loose query orientation");
        this.orientation = com.juicyslew.moonstation14.entities.LooseFrameOrientation.normalized(
                orientation.x(), orientation.y(), orientation.z(), orientation.w());
    }

    /** Resolve a physical-profile query proxy at the entity's current feet pivot; callers may translate it once for interpolation. */
    public static LooseItemQueryGeometry forEntity(LooseItemEntity entity, float partialTick) {
        if (entity == null) return null;
        final LooseObjectProfile profile;
        try { profile = entity.profile(); }
        catch (RuntimeException unavailableProfile) { return null; }
        Quaterniond basis;
        float spin;
        if (entity.level().isClientSide) {
            basis = entity.renderOrientation(partialTick);
            spin = entity.getRotationAngle(partialTick);
        } else if (entity.level() instanceof net.minecraft.server.level.ServerLevel server) {
            basis = entity.authoritativeQueryOrientation(server);
            spin = entity.getRotationAngle();
        } else return null;
        if (basis == null || !Float.isFinite(spin)) return null;
        Quaterniond combined = new Quaterniond(basis).mul(new Quaterniond().rotationY(spin)).normalize();
        try { return new LooseItemQueryGeometry(profile.physicalWidth(), profile.physicalHeight(), entity.position(), combined); }
        catch (IllegalArgumentException unavailableShape) { return null; }
    }

    /** Query-shape envelope at the same presented pivot consumed by renderer/culling callers. */
    public static LooseItemQueryGeometry forPresentedEntity(LooseItemEntity entity,float partialTick) {
        LooseItemQueryGeometry geometry=forEntity(entity,partialTick);
        if (geometry==null) return null;
        try { return geometry.translated(entity.renderPosition(partialTick).subtract(entity.position())); }
        catch (RuntimeException invalidPresentation) { return null; }
    }

    public Vec3 pivot() { return pivot; }
    public Quaterniond orientation() { return new Quaterniond(orientation); }
    /** Local feet-pivoted bounds captured from the resolved profile. */
    public AABB localBounds() { return new AABB(-width/2, 0, -width/2, width/2, height, width/2); }

    /** Return an immutable oriented proxy expanded in all three local axes. */
    public LooseItemQueryGeometry inflated(double margin) {
        if (!Double.isFinite(margin) || margin < 0) throw new IllegalArgumentException("invalid query margin");
        if (margin == 0) return this;
        Vec3 localShift = transform(new Vec3(0, -margin, 0), orientation);
        return new LooseItemQueryGeometry(width + 2 * margin, height + 2 * margin,
                pivot.add(localShift), orientation);
    }

    /** Exact minimum projected distance from origin over this oriented box. */
    public double minimumProjection(Vec3 origin, Vec3 direction) {
        if (!finite(origin) || !finite(direction)) return Double.NaN;
        Vec3 localDirection = transform(direction, new Quaterniond(orientation).conjugate());
        double center = pivot.subtract(origin).dot(direction);
        double halfX = width * .5, halfZ = width * .5;
        double minimum = center + localDirection.x * (localDirection.x >= 0 ? -halfX : halfX)
                + localDirection.y * (localDirection.y >= 0 ? 0 : height)
                + localDirection.z * (localDirection.z >= 0 ? -halfZ : halfZ);
        return Double.isFinite(minimum) ? minimum : Double.NaN;
    }

    public LooseItemQueryGeometry translated(Vec3 offset) {
        if (!finite(offset)) throw new IllegalArgumentException("invalid translation");
        return new LooseItemQueryGeometry(width, height, pivot.add(offset), orientation);
    }

    public Vec3 clip(Vec3 start, Vec3 end) {
        if (!finite(start) || !finite(end)) return null;
        Vec3 a = local(start), b = local(end), delta = b.subtract(a);
        double lo = 0, hi = 1;
        double[] origin = {a.x, a.y, a.z}, direction = {delta.x, delta.y, delta.z};
        double[] min = {-width / 2, 0, -width / 2}, max = {width / 2, height, width / 2};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(direction[i]) < 1e-12) {
                if (origin[i] < min[i] || origin[i] > max[i]) return null;
            } else {
                double t0 = (min[i] - origin[i]) / direction[i], t1 = (max[i] - origin[i]) / direction[i];
                if (t0 > t1) { double t = t0; t0 = t1; t1 = t; }
                lo = Math.max(lo, t0); hi = Math.min(hi, t1);
                if (lo > hi) return null;
            }
        }
        return start.add(end.subtract(start).scale(lo));
    }

    public Vec3 nearestPoint(Vec3 point) {
        if (!finite(point)) return null;
        Vec3 p = local(point);
        return world(new Vec3(clamp(p.x, -width / 2, width / 2), clamp(p.y, 0, height),
                clamp(p.z, -width / 2, width / 2)));
    }

    public AABB worldEnvelope() {
        double minX=Double.POSITIVE_INFINITY, minY=minX, minZ=minX;
        double maxX=Double.NEGATIVE_INFINITY, maxY=maxX, maxZ=maxX;
        for (int i=0;i<8;i++) {
            Vec3 p = world(new Vec3((i&1)==0 ? -width/2 : width/2, (i&2)==0 ? 0 : height,
                    (i&4)==0 ? -width/2 : width/2));
            minX=Math.min(minX,p.x); minY=Math.min(minY,p.y); minZ=Math.min(minZ,p.z);
            maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); maxZ=Math.max(maxZ,p.z);
        }
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ);
    }

    /** Axis-aligned union used for conservative visibility tests, not interaction geometry. */
    public static AABB union(AABB first,AABB second) {
        if(first==null)return second;
        if(second==null)return first;
        return new AABB(Math.min(first.minX,second.minX),Math.min(first.minY,second.minY),Math.min(first.minZ,second.minZ),
                Math.max(first.maxX,second.maxX),Math.max(first.maxY,second.maxY),Math.max(first.maxZ,second.maxZ));
    }

    private Vec3 local(Vec3 p) { return transform(p.subtract(pivot), new Quaterniond(orientation).conjugate()); }
    private Vec3 world(Vec3 p) { return pivot.add(transform(p, orientation)); }
    private static Vec3 transform(Vec3 p, Quaterniondc q) {
        Vector3d v = q.transform(new Vector3d(p.x,p.y,p.z));
        return new Vec3(v.x,v.y,v.z);
    }
    private static double clamp(double x,double a,double b) { return Math.max(a,Math.min(b,x)); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static boolean finite(Quaterniondc q) {
        if (q == null || !Double.isFinite(q.x()) || !Double.isFinite(q.y()) || !Double.isFinite(q.z()) || !Double.isFinite(q.w())) return false;
        double n=q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w();
        return Double.isFinite(n) && n > 1e-20;
    }
}
