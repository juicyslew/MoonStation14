package com.juicyslew.moonstation14.ms14.slip;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Immutable geometry snapshot for exact body/native-shape contact and projected puddle coverage. */
public final class PuddleContactGeometry {
    private final AABB worldBody;
    private final double bodyYaw;
    private final Pose3d framePose;
    private final AABB localCandidateBounds;
    private final List<Point> footprint;
    private record Point(double x, double z) { }

    private PuddleContactGeometry(AABB body, double yaw, Pose3dc pose) {
        if (!valid(body) || !Double.isFinite(yaw) || pose == null) throw new IllegalArgumentException("invalid geometry");
        framePose = new Pose3d(pose);
        if (!unitScale(framePose) || !validOrientation(framePose.orientation())) throw new IllegalArgumentException("invalid rigid frame pose");
        worldBody = body;
        bodyYaw = yaw;
        AABB localBounds = null;
        for (int i=0;i<8;i++) {
            Vector3d p = bodyCorner(body, yaw, i);
            toLocal(p);
            AABB point = new AABB(p.x,p.y,p.z,p.x,p.y,p.z);
            localBounds = localBounds == null ? point : localBounds.minmax(point);
        }
        localCandidateBounds = localBounds;
        ArrayList<Point> corners = new ArrayList<>(4);
        int[] order={0,1,5,4};
        for (int index : order) {
            Vector3d p = bodyCorner(new AABB(body.minX,body.minY,body.minZ,body.maxX,body.minY,body.maxZ),yaw,index);
            toLocal(p);
            corners.add(new Point(p.x,p.z));
        }
        footprint = List.copyOf(corners);
    }

    public static PuddleContactGeometry capture(AABB worldBodyBox, double bodyYawRadians, Pose3dc framePose) {
        return new PuddleContactGeometry(worldBodyBox, bodyYawRadians, framePose);
    }

    /** Broad-phase only; callers must use contact() before admitting contact. */
    public AABB localCandidateBounds() { return localCandidateBounds; }

    public Contact contact(AABB localSourceShape, BlockPos sourcePos) {
        if (!valid(localSourceShape) || sourcePos == null) return new Contact(false, 0);
        // Native query bounds are already absolute storage-space bounds.
        Quaterniond bodyOrientation = new Quaterniond().rotateY(bodyYaw);
        boolean intersects = com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(worldBody, bodyOrientation, localSourceShape, framePose);
        double ratio = intersects ? ratio(sourcePos) : 0;
        return new Contact(intersects,ratio);
    }

    public record Contact(boolean intersects, double triggerRatio) { }

    private double ratio(BlockPos pos) {
        List<Point> clipped = footprint.stream().map(p -> new Point(p.x-pos.getX(),p.z-pos.getZ())).toList();
        double x0=.1,x1=.9,z0=.1,z1=.9;
        clipped = clip(clipped,0,x0,true); clipped=clip(clipped,0,x1,false);
        clipped=clip(clipped,1,z0,true); clipped=clip(clipped,1,z1,false);
        double area=area(clipped);
        double footprintArea=area(clippedFootprint(pos));
        if (!Double.isFinite(footprintArea) || footprintArea <= 1e-6) return 0;
        return Math.max(area/footprintArea, area/.64);
    }
    private List<Point> clippedFootprint(BlockPos pos) { return footprint.stream().map(p -> new Point(p.x-pos.getX(),p.z-pos.getZ())).toList(); }
    private static List<Point> clip(List<Point> in,int axis,double edge,boolean greater) {
        ArrayList<Point> out=new ArrayList<>(); if(in.isEmpty())return out;
        Point a=in.get(in.size()-1); double av=axis==0?a.x:a.z;
        for(Point b:in){ double bv=axis==0?b.x:b.z; boolean ai=greater?av>=edge:av<=edge, bi=greater?bv>=edge:bv<=edge;
            if(ai!=bi){double t=(edge-av)/(bv-av);out.add(new Point(a.x+t*(b.x-a.x),a.z+t*(b.z-a.z)));}
            if(bi)out.add(b); a=b;av=bv;
        } return out;
    }
    private static double area(List<Point> p){if(p.size()<3)return 0;double ox=p.get(0).x,oz=p.get(0).z,s=0;for(int i=0;i<p.size();i++){Point a=p.get(i),b=p.get((i+1)%p.size());s+=(a.x-ox)*(b.z-oz)-(b.x-ox)*(a.z-oz);}return Math.abs(s)*.5;}
    private static Vector3d bodyCorner(AABB b,double yaw,int i){double x=(i&1)==0?b.minX:b.maxX,y=(i&2)==0?b.minY:b.maxY,z=(i&4)==0?b.minZ:b.maxZ;
        double cx=(b.minX+b.maxX)*.5,cz=(b.minZ+b.maxZ)*.5,dx=x-cx,dz=z-cz,c=Math.cos(yaw),s=Math.sin(yaw);
        return new Vector3d(cx+c*dx+s*dz,y,cz-s*dx+c*dz);}
    private void toLocal(Vector3d p){Vector3d local=framePose.transformPositionInverse(p);p.set(local);}
    private static boolean validOrientation(Quaterniond q){double n=q.lengthSquared();return Double.isFinite(q.x)&&Double.isFinite(q.y)&&Double.isFinite(q.z)&&Double.isFinite(q.w)&&Double.isFinite(n)&&Math.abs(n-1)<=1e-6;}
    private static boolean unitScale(Pose3dc p){return Double.isFinite(p.scale().x())&&Double.isFinite(p.scale().y())&&Double.isFinite(p.scale().z())&&p.scale().x()==1&&p.scale().y()==1&&p.scale().z()==1;}
    private static boolean valid(AABB b){return b!=null&&Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ)&&b.getXsize()>0&&b.getYsize()>0&&b.getZsize()>0;}
}
