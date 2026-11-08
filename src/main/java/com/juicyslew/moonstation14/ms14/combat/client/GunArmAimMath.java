package com.juicyslew.moonstation14.ms14.combat.client;

/** Pure arm-frame alignment math. Coordinates are the item's native model space. */
public final class GunArmAimMath {
    private GunArmAimMath() { }

    public record Vec(double x, double y, double z) {
        public Vec add(Vec b) { return new Vec(x+b.x,y+b.y,z+b.z); }
        public Vec sub(Vec b) { return new Vec(x-b.x,y-b.y,z-b.z); }
        public Vec scale(double s) { return new Vec(x*s,y*s,z*s); }
        public double dot(Vec b) { return x*b.x+y*b.y+z*b.z; }
        public Vec cross(Vec b) { return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x); }
        public double length() { return Math.sqrt(dot(this)); }
        public Vec normalized() { double n=length(); if (!(n>1e-10) || !Double.isFinite(n)) throw new IllegalArgumentException("degenerate direction"); return scale(1/n); }
    }
    /** ModelPart Euler angles, applied as Rz * Ry * Rx. */
    public record Rotation(float x, float y, float z) { }
    private record Frame(Vec x, Vec y, Vec z) { }

    public static Rotation aim(Vec bore, Vec up, Vec look) {
        return align(bore, up, look, new Vec(0,-1,0));
    }
    /** Align an authored orthogonal basis with a desired directed basis. */
    public static Rotation align(Vec bore, Vec up, Vec targetBore, Vec targetUp) {
        Frame source=frame(bore,up);
        Frame target=frame(targetBore,targetUp);
        Vec targetZ=target.z;
        Vec desiredUp=target.y;
        Vec targetX=desiredUp.cross(targetZ);
        if (targetX.length()<1e-8) targetX=new Vec(1,0,0).cross(targetZ);
        if (targetX.length()<1e-8) targetX=new Vec(0,0,1).cross(targetZ);
        targetX=targetX.normalized();
        Vec targetY=targetZ.cross(targetX).normalized();
        target=new Frame(targetX,targetY,targetZ);
        // R = target * transpose(source), represented row-major.
        double[][] m=new double[3][3];
        Vec[] t={target.x,target.y,target.z}, s={source.x,source.y,source.z};
        for(int r=0;r<3;r++) for(int c=0;c<3;c++) for(int k=0;k<3;k++) m[r][c]+=component(t[k],r)*component(s[k],c);
        double sy=Math.max(-1,Math.min(1,-m[2][0]));
        double y=Math.asin(sy), cy=Math.cos(y), x,z;
        if(Math.abs(cy)>1e-7) { x=Math.atan2(m[2][1],m[2][2]); z=Math.atan2(m[1][0],m[0][0]); }
        else { x=0; z=Math.atan2(-m[0][1],m[1][1]); }
        return new Rotation((float)x,(float)y,(float)z);
    }
    private static Frame frame(Vec bore, Vec up) {
        Vec z=bore.normalized(); Vec y=up.add(z.scale(-up.dot(z)));
        if(y.length()<1e-8) y=(Math.abs(z.y)<.9?new Vec(0,1,0):new Vec(1,0,0)).add(z.scale(-((Math.abs(z.y)<.9?new Vec(0,1,0):new Vec(1,0,0)).dot(z))));
        y=y.normalized(); Vec x=y.cross(z).normalized(); y=z.cross(x).normalized();
        return new Frame(x,y,z);
    }
    private static double component(Vec v,int i) { return i==0?v.x:i==1?v.y:v.z; }
}
