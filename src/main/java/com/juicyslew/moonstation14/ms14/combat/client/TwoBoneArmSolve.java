package com.juicyslew.moonstation14.ms14.combat.client;

/** Analytic two-link IK with deterministic floor-aware elbow selection. */
public final class TwoBoneArmSolve {
    private TwoBoneArmSolve() { }
    public record Result(GunArmAimMath.Vec elbow, GunArmAimMath.Vec hand, boolean reached, boolean floorContact) { }

    public static Result solve(GunArmAimMath.Vec shoulder, GunArmAimMath.Vec target, double upperLength, double lowerLength,
                               GunArmAimMath.Vec preferredBend, GunArmAimMath.Vec floorNormal, double floorContactHeight) {
        if (!finite(shoulder) || !finite(target)) { shoulder=new GunArmAimMath.Vec(0,0,0); target=shoulder; }
        double l1=validLength(upperLength)?upperLength:1e-9;
        double l2=validLength(lowerLength)?lowerLength:1e-9;
        GunArmAimMath.Vec delta=target.sub(shoulder); double raw=delta.length();
        GunArmAimMath.Vec axis=raw>1e-12?delta.scale(1/raw):new GunArmAimMath.Vec(1,0,0);
        double distance=Math.max(Math.abs(l1-l2)+1e-9,Math.min(l1+l2-1e-9,raw));
        GunArmAimMath.Vec hand=shoulder.add(axis.scale(distance));
        double a=(l1*l1-l2*l2+distance*distance)/(2*distance);
        double radius=Math.sqrt(Math.max(0,l1*l1-a*a));
        GunArmAimMath.Vec centre=shoulder.add(axis.scale(a));
        GunArmAimMath.Vec normal=finite(floorNormal)?normalizedOrZero(floorNormal):new GunArmAimMath.Vec(0,1,0);
        if(normal.length()<1e-12) normal=new GunArmAimMath.Vec(0,1,0);
        GunArmAimMath.Vec bend=finite(preferredBend)?preferredBend:new GunArmAimMath.Vec(0,0,1);
        GunArmAimMath.Vec side=axis.cross(bend);
        if(side.length()<1e-10) side=axis.cross(Math.abs(axis.y())<.9?new GunArmAimMath.Vec(0,1,0):new GunArmAimMath.Vec(1,0,0));
        side=normalizedOrZero(side);
        // Circle-plane extrema: select the floorward point, preferring the authored
        // bend direction to break a level-floor tie.
        GunArmAimMath.Vec down=normalizedOrZero(normal.scale(-1).sub(axis.scale(-normal.dot(axis))));
        if(down.length()<1e-10) down=side;
        GunArmAimMath.Vec elbow=centre.add(down.scale(radius));
        double current=elbow.dot(normal);
        double signed=centre.dot(normal)-floorContactHeight;
        GunArmAimMath.Vec inPlane=normal.sub(axis.scale(normal.dot(axis)));
        double q=inPlane.length();
        if(q>1e-10 && Math.abs(signed)<=radius*q) {
            GunArmAimMath.Vec radial=inPlane.scale(-signed/(q*q));
            double remain=Math.sqrt(Math.max(0,radius*radius-radial.dot(radial)));
            GunArmAimMath.Vec tangent=normalizedOrZero(axis.cross(inPlane));
            GunArmAimMath.Vec p=centre.add(radial).add(tangent.scale(remain));
            GunArmAimMath.Vec m=centre.add(radial).sub(tangent.scale(remain));
            GunArmAimMath.Vec preferred=finite(preferredBend)?preferredBend:new GunArmAimMath.Vec(0,0,1);
            // Select the intersection that best agrees with the requested bend side.
            elbow=p.sub(centre).dot(preferred)>=m.sub(centre).dot(preferred)?p:m;
        }
        return new Result(elbow,hand,Math.abs(raw-distance)<1e-8,Math.abs(elbow.dot(normal)-floorContactHeight)<1e-7);
    }
    private static GunArmAimMath.Vec normalizedOrZero(GunArmAimMath.Vec v) { double n=v.length(); return n>1e-12&&Double.isFinite(n)?v.scale(1/n):v.scale(0); }
    private static boolean finite(GunArmAimMath.Vec v) { return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z()); }
    private static boolean validLength(double value) { return Double.isFinite(value)&&value>0; }
}
