package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Replays native SAT against the immutable body and selected-block geometry captured during collision. */
final class NativeSideCollisionWitness {
    private final LevelReusedVectors scratch = new LevelReusedVectors();

    MovementVector separation(NativeCollisionGeometry geometry) {
        try {
            if (geometry == null) return null;
            MovementVector center = geometry.entityBoundsCenter(), dimensions = geometry.entityBoundsDimensions();
            if (!positive(dimensions)) return null;
            NativeCollisionGeometry.QuaternionValue q = geometry.entityBoundsOrientation();
            double qn = q.x()*q.x() + q.y()*q.y() + q.z()*q.z() + q.w()*q.w();
            if (!Double.isFinite(qn) || Math.abs(qn - 1d) > NativeCollisionSlop.MAX_RESIDUAL) return null;

            double[] m = geometry.subLevelPose().matrix();
            // Rigid affine matrix in JOML's column-major layout.
            if (Math.abs(m[3]) > EPS || Math.abs(m[7]) > EPS || Math.abs(m[11]) > EPS || Math.abs(m[15]-1) > EPS) return null;
            Vector3d x = new Vector3d(m[0],m[1],m[2]), y = new Vector3d(m[4],m[5],m[6]), z = new Vector3d(m[8],m[9],m[10]);
            if (Math.abs(x.lengthSquared()-1)>EPS || Math.abs(y.lengthSquared()-1)>EPS || Math.abs(z.lengthSquared()-1)>EPS
                    || Math.abs(x.dot(y))>EPS || Math.abs(x.dot(z))>EPS || Math.abs(y.dot(z))>EPS
                    || x.cross(y, new Vector3d()).dot(z) < 1-EPS) return null;
            var bounds = geometry.selectedStorageBounds();
            double sx=bounds.maxX-bounds.minX, sy=bounds.maxY-bounds.minY, sz=bounds.maxZ-bounds.minZ;
            if (!(sx>0 && sy>0 && sz>0) || !Double.isFinite(sx+sy+sz)) return null;
            Vector3d blockCenter = new Vector3d((bounds.minX+bounds.maxX)*.5,(bounds.minY+bounds.maxY)*.5,(bounds.minZ+bounds.maxZ)*.5);
            new Matrix4d().set(m).transformPosition(blockCenter);
            Quaterniond blockOrientation = new Quaterniond().setFromUnnormalized(new Matrix4d().set(m));
            OrientedBoundingBox3d body = new OrientedBoundingBox3d(new Vector3d(center.x(),center.y(),center.z()),
                    new Vector3d(dimensions.x(),dimensions.y(),dimensions.z()), new Quaterniond(q.x(),q.y(),q.z(),q.w()), scratch);
            OrientedBoundingBox3d block = new OrientedBoundingBox3d(blockCenter, new Vector3d(sx,sy,sz), blockOrientation, scratch);
            Vector3d mtv = new Vector3d();
            OrientedBoundingBox3d.sat(body, block, mtv);
            return new MovementVector(mtv.x, mtv.y, mtv.z);
        } catch (RuntimeException invalid) { return null; }
    }

    private static final double EPS = NativeCollisionSlop.MAX_RESIDUAL;
    private static boolean positive(MovementVector v) {
        return v.x()>0 && v.y()>0 && v.z()>0 && Double.isFinite(v.x()+v.y()+v.z());
    }
}
