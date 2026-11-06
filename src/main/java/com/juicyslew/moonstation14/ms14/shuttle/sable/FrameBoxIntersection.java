package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Quaterniondc;

/** Exact OBB/SAT intersection between a world body box and a native-frame storage box. */
public final class FrameBoxIntersection {
    private FrameBoxIntersection() { }

    public static boolean intersects(AABB worldBox, AABB storageBox, Pose3dc pose) {
        return intersects(worldBox, new Quaterniond(), storageBox, pose);
    }

    /** Body orientation is explicit for callers whose native entity box is yaw-oriented. */
    public static boolean intersects(AABB worldBox, Quaterniond bodyOrientation, AABB storageBox, Pose3dc pose) {
        if (!valid(worldBox) || !valid(storageBox) || pose == null || bodyOrientation == null
                || !unitScale(pose)) return false;
        Vector3d center = new Vector3d((storageBox.minX + storageBox.maxX) * .5,
                (storageBox.minY + storageBox.maxY) * .5, (storageBox.minZ + storageBox.maxZ) * .5);
        pose.transformPosition(center);
        LevelReusedVectors scratch = new LevelReusedVectors();
        var body = new OrientedBoundingBox3d(new Vector3d((worldBox.minX + worldBox.maxX) * .5,
                (worldBox.minY + worldBox.maxY) * .5, (worldBox.minZ + worldBox.maxZ) * .5),
                new Vector3d(worldBox.getXsize(), worldBox.getYsize(), worldBox.getZsize()),
                new Quaterniond(bodyOrientation), scratch);
        var frameShape = new OrientedBoundingBox3d(center,
                new Vector3d(storageBox.getXsize(), storageBox.getYsize(), storageBox.getZsize()),
                new Quaterniond(pose.orientation()), scratch);
        return new Vector3d(OrientedBoundingBox3d.sat(body, frameShape, new Vector3d())).lengthSquared() > 0;
    }

    /** True only when this SAT implementation represents the supplied frame pose exactly. */
    public static boolean supportsPose(Pose3dc pose) {
        return pose != null && unitScale(pose)
                && Double.isFinite(pose.orientation().x()) && Double.isFinite(pose.orientation().y())
                && Double.isFinite(pose.orientation().z()) && Double.isFinite(pose.orientation().w());
    }

    /** World-axis-aligned envelope of a box centered at its original center and yawed in world space. */
    public static AABB orientedWorldBounds(AABB baseBox, Quaterniondc orientation) {
        if (!valid(baseBox) || orientation == null || !Double.isFinite(orientation.x())
                || !Double.isFinite(orientation.y()) || !Double.isFinite(orientation.z())
                || !Double.isFinite(orientation.w())) return null;
        double cx=(baseBox.minX+baseBox.maxX)*.5, cy=(baseBox.minY+baseBox.maxY)*.5,
                cz=(baseBox.minZ+baseBox.maxZ)*.5;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
        double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        Quaterniond rotation=new Quaterniond(orientation);
        for(int i=0;i<8;i++) {
            Vector3d corner=new Vector3d((i&1)==0?baseBox.minX-cx:baseBox.maxX-cx,
                    (i&2)==0?baseBox.minY-cy:baseBox.maxY-cy,
                    (i&4)==0?baseBox.minZ-cz:baseBox.maxZ-cz);
            rotation.transform(corner).add(cx,cy,cz);
            minX=Math.min(minX,corner.x); minY=Math.min(minY,corner.y); minZ=Math.min(minZ,corner.z);
            maxX=Math.max(maxX,corner.x); maxY=Math.max(maxY,corner.y); maxZ=Math.max(maxZ,corner.z);
        }
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ);
    }

    private static boolean valid(AABB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && box.getXsize() > 0 && box.getYsize() > 0 && box.getZsize() > 0;
    }

    private static boolean unitScale(Pose3dc pose) {
        return Double.isFinite(pose.scale().x()) && Double.isFinite(pose.scale().y())
                && Double.isFinite(pose.scale().z()) && pose.scale().x() == 1
                && pose.scale().y() == 1 && pose.scale().z() == 1;
    }
}
