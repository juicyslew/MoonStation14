package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Pure local-space geometry/motion shared by rendering and focused unit tests. */
public final class AirlockGeometry {
    public static final double HALF_TRAVEL = .37;
    public static final double BACK = .08;
    public static final double FRONT = .92;
    private AirlockGeometry() { }

    public record Point(double x,double y,double z) { }
    public record Face(Point a,Point b,Point c,Point d, float nx,float ny,float nz) { }
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public List<Face> faces() { return List.of(
                face(new Point(x0,y0,z1),new Point(x1,y0,z1),new Point(x1,y1,z1),new Point(x0,y1,z1),0,0,1),
                face(new Point(x1,y0,z0),new Point(x0,y0,z0),new Point(x0,y1,z0),new Point(x1,y1,z0),0,0,-1),
                face(new Point(x0,y1,z0),new Point(x1,y1,z0),new Point(x1,y1,z1),new Point(x0,y1,z1),0,1,0),
                face(new Point(x0,y0,z1),new Point(x1,y0,z1),new Point(x1,y0,z0),new Point(x0,y0,z0),0,-1,0),
                face(new Point(x0,y0,z0),new Point(x0,y0,z1),new Point(x0,y1,z1),new Point(x0,y1,z0),-1,0,0),
                face(new Point(x1,y0,z1),new Point(x1,y0,z0),new Point(x1,y1,z0),new Point(x1,y1,z1),1,0,0));
        }
    }
    private static Face face(Point a,Point b,Point c,Point d,float x,float y,float z) { return new Face(a,b,c,d,x,y,z); }
    public static double openness(AirlockState.DoorPhase phase,double elapsed,double openOne,double openTwo,double closeOne,double closeTwo) {
        return switch(phase) {
            case OPEN -> 1; case CLOSED,DENYING -> 0;
            case OPENING -> .5*progress(elapsed,openOne);
            case OPENING_PASSABLE -> .5+.5*progress(elapsed,openTwo);
            case CLOSING -> 1-.5*progress(elapsed,closeOne);
            case CLOSING_PASSABLE -> .5-.5*progress(elapsed,closeTwo);
        };
    }
    private static double progress(double elapsed,double duration) { return duration<=0?1:Math.max(0,Math.min(1,elapsed/duration)); }
    public static List<Box> leaves(double openness) {
        double travel=HALF_TRAVEL*Math.max(0,Math.min(1,openness));
        return List.of(new Box(.13-travel,.13,BACK,.50-travel,1.87,FRONT),
                new Box(.50+travel,.13,BACK,.87+travel,1.87,FRONT));
    }
    /** Closed-canvas UV bounds assigned to the two moving panels; UVs do not move with the geometry. */
    public static double[] leafUv(int index) { return index == 0 ? new double[]{.13,.5,.13,.87} : new double[]{.5,.87,.13,.87}; }
    public static Box hatch(boolean open) { return open ? new Box(.64,.38,.92,.84,.66,.98) : new Box(.64,.38,.92,.84,.66,.94); }
    public static double[] hatchPlanes() { return new double[]{FRONT + .062, BACK - .062}; }
    /** Cuboids emitted by renderer's neutral frame geometry. */
    public static List<Box> frame() { return List.of(new Box(0,0,.08,.13,2,.92),new Box(.87,0,.08,1,2,.92),
            new Box(.13,0,.08,.87,.13,.92),new Box(.13,1.87,.08,.87,2,.92)); }
    /** World-space bounds of the renderer's frame and moving leaf cuboids, with a subtle selection margin. */
    public static List<AABB> worldBounds(BlockPos root, Direction facing, double openness) {
        java.util.ArrayList<AABB> bounds=new java.util.ArrayList<>();
        java.util.ArrayList<Box> parts=new java.util.ArrayList<>(frame()); parts.addAll(leaves(openness));
        int rx=facing.getCounterClockWise().getStepX(), rz=facing.getCounterClockWise().getStepZ();
        int fx=facing.getStepX(), fz=facing.getStepZ();
        for(Box b:parts) {
            double[] xs={b.x0(),b.x1()}, ys={b.y0(),b.y1()}, zs={b.z0(),b.z1()};
            double minX=Double.POSITIVE_INFINITY,minY=root.getY()+b.y0(),minZ=Double.POSITIVE_INFINITY;
            double maxX=Double.NEGATIVE_INFINITY,maxY=root.getY()+b.y1(),maxZ=Double.NEGATIVE_INFINITY;
            for(double x:xs)for(double z:zs) {
                double wx=root.getX()+.5+rx*(x-.5)+fx*(z-.5), wz=root.getZ()+.5+rz*(x-.5)+fz*(z-.5);
                minX=Math.min(minX,wx);maxX=Math.max(maxX,wx);minZ=Math.min(minZ,wz);maxZ=Math.max(maxZ,wz);
            }
            bounds.add(new AABB(minX,minY,minZ,maxX,maxY,maxZ).inflate(.012));
        }
        return List.copyOf(bounds);
    }
    public static AABB renderBounds(BlockPos root) {
        return new AABB(root.getX()-HALF_TRAVEL,root.getY(),root.getZ()-HALF_TRAVEL,
                root.getX()+1+HALF_TRAVEL,root.getY()+2,root.getZ()+1+HALF_TRAVEL);
    }
}
