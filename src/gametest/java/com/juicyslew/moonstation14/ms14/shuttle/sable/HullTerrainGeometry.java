package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.examine.FrameBoundsTransform;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Pure test-oracle seam shared by hull impact sampling and focused SAT geometry tests. */
final class HullTerrainGeometry {
    static final double IDENTITY_ORIENTATION_TOLERANCE = 1.0e-6;
    record Contact(AABB worldBounds, Vector3d mtv) {
        double penetrationDepth() { return mtv.length(); }
        boolean overlaps() { return penetrationDepth() > 0d; }
    }

    private HullTerrainGeometry() { }

    static AABB transformedBounds(AABB localHull,Matrix4dc pose) {
        return FrameBoundsTransform.transformedBounds(localHull,pose);
    }

    static Contact againstTerrain(AABB localHull, Matrix4dc pose, AABB worldTerrain,
                                  LevelReusedVectors scratch) {
        AABB transformed=FrameBoundsTransform.transformedBounds(localHull,pose);
        var localCenter=new Vector3d((localHull.minX+localHull.maxX)*.5,
                (localHull.minY+localHull.maxY)*.5,(localHull.minZ+localHull.maxZ)*.5);
        var center=new Vector3d(localCenter);
        pose.transformPosition(center);
        var dimensions=new Vector3d(localHull.getXsize(),localHull.getYsize(),localHull.getZsize());
        var orientation=new Quaterniond().setFromUnnormalized(new org.joml.Matrix4d(pose));
        var body=new OrientedBoundingBox3d(center,dimensions,orientation,scratch);
        var terrainCenter=new Vector3d((worldTerrain.minX+worldTerrain.maxX)*.5,
                (worldTerrain.minY+worldTerrain.maxY)*.5,(worldTerrain.minZ+worldTerrain.maxZ)*.5);
        var terrainDimensions=new Vector3d(worldTerrain.getXsize(),worldTerrain.getYsize(),worldTerrain.getZsize());
        var terrain=new OrientedBoundingBox3d(terrainCenter,terrainDimensions,new Quaterniond(),scratch);
        var mtv=new Vector3d();
        OrientedBoundingBox3d.sat(body,terrain,mtv);
        return new Contact(transformed,mtv);
    }

    static boolean fullyBelowSupportSlab(AABB actualHullBounds,double supportBottom) {
        return actualHullBounds!=null&&actualHullBounds.maxY<supportBottom;
    }

    static boolean nearSupportSurface(AABB actualHullBounds,double supportSurfaceY,
                                      double belowTolerance,double aboveTolerance) {
        return actualHullBounds!=null&&actualHullBounds.minY>=supportSurfaceY-belowTolerance
                &&actualHullBounds.minY<=supportSurfaceY+aboveTolerance;
    }

    static boolean withinHorizontalPad(AABB actualHullBounds,AABB pad) {
        return actualHullBounds!=null&&actualHullBounds.minX>=pad.minX&&actualHullBounds.maxX<=pad.maxX
                &&actualHullBounds.minZ>=pad.minZ&&actualHullBounds.maxZ<=pad.maxZ;
    }

    /** Test-only initial-placement guard; q and -q represent the same identity rotation. */
    static boolean nearIdentityOrientation(Quaterniondc orientation) {
        if(orientation==null||!Double.isFinite(orientation.x())||!Double.isFinite(orientation.y())
                ||!Double.isFinite(orientation.z())||!Double.isFinite(orientation.w()))return false;
        double vectorLength=Math.sqrt(orientation.x()*orientation.x()+orientation.y()*orientation.y()
                +orientation.z()*orientation.z());
        return vectorLength<=IDENTITY_ORIENTATION_TOLERANCE
                &&Math.abs(Math.abs(orientation.w())-1.0)<=IDENTITY_ORIENTATION_TOLERANCE;
    }
}
