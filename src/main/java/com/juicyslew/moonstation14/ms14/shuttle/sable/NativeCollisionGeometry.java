package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import java.util.Arrays;

/** Immutable snapshot of borrowed SDK collision geometry and solver values. */
public record NativeCollisionGeometry(MovementVector entityBoundsCenter, MovementVector entityBoundsDimensions,
        QuaternionValue entityBoundsOrientation, NativeFrameInterval.PoseValue subLevelPose, AABB selectedStorageBounds,
        MovementVector maxMtv, MovementVector collisionMotion, int iteration, int substeps) {
    public record QuaternionValue(double x, double y, double z, double w) {
        public QuaternionValue {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w))
                throw new IllegalArgumentException("quaternion components must be finite");
        }
        static QuaternionValue copy(Quaterniondc q) { return new QuaternionValue(q.x(), q.y(), q.z(), q.w()); }
    }
    public NativeCollisionGeometry {
        java.util.Objects.requireNonNull(entityBoundsCenter);
        java.util.Objects.requireNonNull(entityBoundsDimensions);
        java.util.Objects.requireNonNull(entityBoundsOrientation);
        java.util.Objects.requireNonNull(subLevelPose);
        java.util.Objects.requireNonNull(selectedStorageBounds);
        java.util.Objects.requireNonNull(maxMtv);
        java.util.Objects.requireNonNull(collisionMotion);
        if (entityBoundsDimensions.x() < 0 || entityBoundsDimensions.y() < 0 || entityBoundsDimensions.z() < 0)
            throw new IllegalArgumentException("entity dimensions must be nonnegative");
        if (!Double.isFinite(selectedStorageBounds.minX) || !Double.isFinite(selectedStorageBounds.minY)
                || !Double.isFinite(selectedStorageBounds.minZ) || !Double.isFinite(selectedStorageBounds.maxX)
                || !Double.isFinite(selectedStorageBounds.maxY) || !Double.isFinite(selectedStorageBounds.maxZ)
                || selectedStorageBounds.minX > selectedStorageBounds.maxX
                || selectedStorageBounds.minY > selectedStorageBounds.maxY
                || selectedStorageBounds.minZ > selectedStorageBounds.maxZ)
            throw new IllegalArgumentException("storage bounds must be finite and ordered");
    }
    public static NativeCollisionGeometry capture(OrientedBoundingBox3d body, Pose3dc pose,
            BoundingBox3d bounds, Vector3dc mtv, Vector3dc motion, int iteration, int substeps) {
        try {
            if (body == null || pose == null || bounds == null || mtv == null || motion == null) return null;
            AABB b = bounds.toMojang();
            return new NativeCollisionGeometry(v(body.getPosition()), v(body.getDimensions()),
                    QuaternionValue.copy(body.getOrientation()), NativeFrameInterval.PoseValue.capture(pose), b,
                    v(mtv), v(motion), iteration, substeps);
        } catch (RuntimeException invalid) { return null; }
    }
    private static MovementVector v(Vector3dc v) { return new MovementVector(v.x(), v.y(), v.z()); }
    @Override public String toString() { return "NativeCollisionGeometry[entityCenter=" + entityBoundsCenter
            + ", entityDimensions=" + entityBoundsDimensions + ", entityOrientation=" + entityBoundsOrientation
            + ", subLevelPose=" + Arrays.toString(subLevelPose.matrix()) + ", selectedStorageBounds=" + selectedStorageBounds
            + ", maxMtv=" + maxMtv + ", collisionMotion=" + collisionMotion + ", iteration=" + iteration + ", substeps=" + substeps + ']'; }
}
