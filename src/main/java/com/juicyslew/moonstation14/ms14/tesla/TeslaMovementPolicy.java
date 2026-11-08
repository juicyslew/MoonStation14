package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.world.phys.Vec3;

/** Small pure motion policy, parameterized by resolved prototype tuning. */
public final class TeslaMovementPolicy {
    private TeslaMovementPolicy() { }

    public static Vec3 dampTargetlessVertical(Vec3 velocity, double deltaSeconds, double halfLifeSeconds) {
        if (halfLifeSeconds <= 0 || !Double.isFinite(halfLifeSeconds)) return velocity;
        double factor = Math.pow(2, -deltaSeconds / halfLifeSeconds);
        return new Vec3(velocity.x, velocity.y * factor, velocity.z);
    }

    /** UUID-phased, smooth orbit sample centered on the target's current position. */
    public static Vec3 orbitPoint(Vec3 center, java.util.UUID id, double ageSeconds, TeslaBallProfile.Orbit tuning) {
        long bits=id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(),23);
        double phase=(bits & 0xffffL)*(Math.PI*2/65536.0);
        double radial=2*Math.PI*ageSeconds/tuning.radialPeriodSeconds()+phase*.73;
        double angular=2*Math.PI*ageSeconds/tuning.angularPeriodSeconds()+phase;
        double fraction=.5+.34*Math.sin(radial)+.16*Math.sin(radial*.47+phase*1.91);
        fraction=Math.max(0,Math.min(1,fraction));
        double radius=tuning.minimumRadius()+(tuning.maximumRadius()-tuning.minimumRadius())*fraction;
        double angle=angular+.35*Math.sin(radial*.61+phase*2.3);
        // Rotate a fixed orthonormal basis directly. Unlike choosing a reference
        // vector from the normal, this remains continuous through every inclination.
        double yaw=2*Math.PI*(ageSeconds/53.0)+phase*1.3;
        double pitch=(Math.PI/2)*Math.sin(radial*.19+phase*1.7);
        double roll=Math.PI*Math.sin(angular*.17+phase*.4);
        Vec3 u=rotateBasis(new Vec3(1,0,0),yaw,pitch,roll);
        Vec3 v=rotateBasis(new Vec3(0,0,1),yaw,pitch,roll);
        return center.add(u.scale(Math.cos(angle)*radius).add(v.scale(Math.sin(angle)*radius)));
    }

    private static Vec3 rotateBasis(Vec3 p,double yaw,double pitch,double roll) {
        double cy=Math.cos(yaw),sy=Math.sin(yaw),cx=Math.cos(pitch),sx=Math.sin(pitch),cz=Math.cos(roll),sz=Math.sin(roll);
        double x=cy*p.x+sy*p.z, y=p.y, z=-sy*p.x+cy*p.z;
        double px=x, py=cx*y-sx*z, pz=sx*y+cx*z;
        return new Vec3(cz*px-sz*py,sz*px+cz*py,pz);
    }

    /** Velocity response integrated in the existing fixed 20Hz entity movement loop. */
    public static Vec3 orbitVelocity(Vec3 velocity, Vec3 position, Vec3 target, Vec3 targetVelocity,
                                     TeslaBallProfile.Orbit tuning, double maximumSpeed, double dt) {
        Vec3 desired=targetVelocity.add(target.subtract(position).scale(tuning.responseRate()));
        desired=clamp(desired,maximumSpeed);
        Vec3 change=clamp(desired.subtract(velocity),tuning.acceleration()*dt);
        return clamp(velocity.add(change),maximumSpeed);
    }
    private static Vec3 clamp(Vec3 v,double maximum){double n=v.length();return n>maximum?v.scale(maximum/n):v;}
}
