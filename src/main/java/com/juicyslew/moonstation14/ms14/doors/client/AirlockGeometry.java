package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import java.util.List;

/** Pure local-space geometry/motion shared by rendering and focused unit tests. */
final class AirlockGeometry {
    static final double HALF_TRAVEL = .37;
    static final double BACK = .08;
    static final double FRONT = .92;
    private AirlockGeometry() { }

    record Point(double x,double y,double z) { }
    record Face(Point a,Point b,Point c,Point d, float nx,float ny,float nz) { }
    record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        List<Face> faces() { return List.of(
                face(new Point(x0,y0,z1),new Point(x1,y0,z1),new Point(x1,y1,z1),new Point(x0,y1,z1),0,0,1),
                face(new Point(x1,y0,z0),new Point(x0,y0,z0),new Point(x0,y1,z0),new Point(x1,y1,z0),0,0,-1),
                face(new Point(x0,y1,z0),new Point(x1,y1,z0),new Point(x1,y1,z1),new Point(x0,y1,z1),0,1,0),
                face(new Point(x0,y0,z1),new Point(x1,y0,z1),new Point(x1,y0,z0),new Point(x0,y0,z0),0,-1,0),
                face(new Point(x0,y0,z0),new Point(x0,y0,z1),new Point(x0,y1,z1),new Point(x0,y1,z0),-1,0,0),
                face(new Point(x1,y0,z1),new Point(x1,y0,z0),new Point(x1,y1,z0),new Point(x1,y1,z1),1,0,0));
        }
    }
    private static Face face(Point a,Point b,Point c,Point d,float x,float y,float z) { return new Face(a,b,c,d,x,y,z); }
    static double openness(AirlockState.DoorPhase phase,double elapsed,double openOne,double openTwo,double closeOne,double closeTwo) {
        return switch(phase) {
            case OPEN -> 1; case CLOSED,DENYING -> 0;
            case OPENING -> .5*progress(elapsed,openOne);
            case OPENING_PASSABLE -> .5+.5*progress(elapsed,openTwo);
            case CLOSING -> 1-.5*progress(elapsed,closeOne);
            case CLOSING_PASSABLE -> .5-.5*progress(elapsed,closeTwo);
        };
    }
    private static double progress(double elapsed,double duration) { return duration<=0?1:Math.max(0,Math.min(1,elapsed/duration)); }
    static List<Box> leaves(double openness) {
        double travel=HALF_TRAVEL*Math.max(0,Math.min(1,openness));
        return List.of(new Box(.13-travel,.13,BACK,.50-travel,1.87,FRONT),
                new Box(.50+travel,.13,BACK,.87+travel,1.87,FRONT));
    }
    /** Closed-canvas UV bounds assigned to the two moving panels; UVs do not move with the geometry. */
    static double[] leafUv(int index) { return index == 0 ? new double[]{.13,.5,.13,.87} : new double[]{.5,.87,.13,.87}; }
    static Box hatch(boolean open) { return open ? new Box(.64,.38,.92,.84,.66,.98) : new Box(.64,.38,.92,.84,.66,.94); }
    static double[] hatchPlanes() { return new double[]{FRONT + .062, BACK - .062}; }
}
