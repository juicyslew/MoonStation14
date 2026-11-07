package com.juicyslew.moonstation14.ms14.combat;

/** Deterministic cosmetic bounce/damping rules shared by native casing particles and tests. */
public final class SpentCasingMotion {
    public record Step(double vx,double vy,double vz,boolean landedNow,boolean grounded) { }
    private SpentCasingMotion() { }
    public static double bounce(double velocity, float restitution) {
        if (!Double.isFinite(velocity) || !Float.isFinite(restitution) || restitution < 0 || restitution > 1)
            throw new IllegalArgumentException("invalid casing bounce");
        return Math.abs(velocity) * restitution;
    }
    public static double damp(double velocity, float damping) {
        if (!Double.isFinite(velocity) || !Float.isFinite(damping) || damping < 0 || damping > 1)
            throw new IllegalArgumentException("invalid casing damping");
        return velocity * damping;
    }
    public static int lifetimeTicks(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0 || seconds > 30) throw new IllegalArgumentException("invalid casing lifetime");
        return Math.max(1, (int) Math.ceil(seconds * 20));
    }
    public static boolean firstLanding(boolean alreadyLanded, boolean onGround) { return !alreadyLanded && onGround; }
    /** Advance interpolated roll, damping its angular speed with casing movement. */
    public static float advanceRoll(float previous, float current, double vx, double vy, double vz, boolean grounded) {
        double speed = Math.sqrt(vx*vx + vy*vy + vz*vz);
        float rate = (float) Math.min(.22, speed * .35);
        if (grounded) rate *= .35f;
        return current + rate;
    }
    /** Resolve one already-collided movement step. Gravity applies during every airborne tick. */
    public static Step step(double vx,double vy,double vz,float gravity,float damping,float restitution,
                            boolean clippedX,boolean clippedY,boolean clippedZ,boolean grounded,boolean hadLanded) {
        if(!Double.isFinite(vx)||!Double.isFinite(vy)||!Double.isFinite(vz)||!Float.isFinite(gravity)||gravity<0
                ||!Float.isFinite(damping)||damping<0||damping>1||!Float.isFinite(restitution)||restitution<0||restitution>1)
            throw new IllegalArgumentException("invalid casing step");
        double x=clippedX?0:damp(vx,damping), z=clippedZ?0:damp(vz,damping);
        double y=vy-gravity;
        if(clippedY&&vy<-.06)y=bounce(vy,restitution);
        else if(grounded&&y<0)y=0;
        boolean landed=firstLanding(hadLanded,grounded);
        if(grounded){x=damp(x,damping);z=damp(z,damping);}
        return new Step(x,y,z,landed,grounded);
    }
}
