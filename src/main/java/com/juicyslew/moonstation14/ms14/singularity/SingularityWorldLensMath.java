package com.juicyslew.moonstation14.ms14.singularity;

/** Reference CPU implementation of the world-space, forward-branch visual lens map. */
public final class SingularityWorldLensMath {
    public static final double ORIGIN_EPSILON_PIXELS = .01;
    private static final int BISECTIONS = 24;

    private SingularityWorldLensMath() { }

    public record Point(double x, double y, double z) { }
    public record Source(Point eyePosition, double intensity, double exponent, double cutoffWorld,
                        double strength, double visibility, double foregroundRampWorld) { }

    /** Maps a point in camera-relative eye coordinates; cutoffPixels and the computed radius use SS14 pixels (32/world unit). */
    public static Point map(Point point, Point source, double intensity, double exponent,
                            double cutoffPixels, double strength, double visibility,
                            double foregroundRampWorld) {
        if (point == null || source == null || !(intensity > 0) || !(exponent > 0)
                || !(cutoffPixels > 0) || !(strength > 0) || !Double.isFinite(strength)
                || !(foregroundRampWorld > 0)) return point;
        double cx=source.x, cy=source.y, cz=source.z;
        double d=Math.sqrt(cx*cx+cy*cy+cz*cz);
        if (!(d > 0)) return point;
        double px=point.x, py=point.y, pz=point.z;
        double s=(px*cx+py*cy+pz*cz)/d;
        if (!(s > 0)) return point;
        double vx=px-cx/d*s, vy=py-cy/d*s, vz=pz-cz/d*s;
        double r=Math.sqrt(vx*vx+vy*vy+vz*vz)*d/s*SingularityVisualProfile.PIXELS_PER_METER;
        double qd=(d-s);
        double t=clamp(1-qd/foregroundRampWorld,0,1);
        double weight=t*t*(3-2*t);
        double a=weight*strength*clamp(visibility,0,1);
        double radius=cutoffPixels;
        if (!(a>0) || !(r>0) || !(r<radius)) return point;
        double mapped=forwardRadius(r,intensity,exponent,radius,a);
        double scale=r == 0 ? 1 : mapped/r;
        return new Point(cx/d*s+vx*scale,cy/d*s+vy*scale,cz/d*s+vz*scale);
    }

    /** Apply source list in reverse ordering, matching sequential SS14 backward-source composition. */
    public static Point map(Point point, java.util.List<Source> sources) {
        Point result=point;
        if(sources==null)return point;
        for(int i=sources.size()-1;i>=0;i--) {
            Source source=sources.get(i);
            if(source==null || source.eyePosition()==null)continue;
            double cutoff=source.cutoffWorld()*SingularityVisualProfile.PIXELS_PER_METER;
            Point before=result;
            result=map(result,source.eyePosition(),source.intensity(),source.exponent(),cutoff,
                    source.strength(),source.visibility(),source.foregroundRampWorld());
            if(source.strength()<=0 || source.visibility()<=0 || foregroundWeight(before,source.eyePosition(),source.foregroundRampWorld())<=0)
                result=before;
        }
        return result;
    }

    public static double foregroundWeight(Point p, Point c, double ramp) {
        double d=Math.sqrt(c.x*c.x+c.y*c.y+c.z*c.z);
        if(!(d>0)||!(ramp>0))return 0;
        double s=(p.x*c.x+p.y*c.y+p.z*c.z)/d;
        if(!(s>0))return 0;
        double t=clamp(1-(d-s)/ramp,0,1); return t*t*(3-2*t);
    }

    public static double forwardRadius(double r,double intensity,double exponent,double cutoff,double amount) {
        if (!(r >= 0) || !(intensity > 0) || !(exponent > 0) || !(cutoff > 0)
                || !(amount > 0) || r >= cutoff) return r;
        if (r < ORIGIN_EPSILON_PIXELS) {
            double e=ORIGIN_EPSILON_PIXELS;
            double b=solve(e,intensity,exponent,cutoff,amount);
            double m=e/(b*derivative(b,intensity,exponent,cutoff,amount));
            m=clamp(m,0,1);
            double t=r/e;
            return b*((2-m)*t-(1-m)*t*t);
        }
        return solve(r,intensity,exponent,cutoff,amount);
    }

    private static double solve(double r,double i,double k,double R,double a) {
        double lo=r, hi=R;
        for(int n=0;n<BISECTIONS;n++) {
            double mid=(lo+hi)*.5;
            if (forward(mid,i,k,R,a)<r) lo=mid; else hi=mid;
        }
        return (lo+hi)*.5;
    }
    private static double forward(double q,double i,double k,double R,double a) {
        return q*(1-a*coefficient(q,i,k,R));
    }
    private static double derivative(double q,double i,double k,double R,double a) {
        SingularityRadialResponse.ValueDerivative response=SingularityRadialResponse.evaluate(q,i,k,R);
        return 1-a*(response.value()+q*response.derivative());
    }
    private static double coefficient(double q,double i,double k,double R) {
        return SingularityRadialResponse.evaluate(q,i,k,R).value();
    }
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
