package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;

/** Client-only display step between authoritative entity tracking corrections. */
public final class ProjectilePresentationMotion {
    private ProjectilePresentationMotion() {}
    public static Vec3 advance(Vec3 position, Vec3 trackedVelocity) {
        if (!finite(position)) return Vec3.ZERO;
        if (!finite(trackedVelocity)) return position;
        Vec3 next=position.add(trackedVelocity);
        return finite(next)?next:position;
    }
    public static double closestSegmentDistanceSquared(Vec3 start, Vec3 end, Vec3 point) {
        Vec3 segment=end.subtract(start); double length=segment.lengthSqr();
        double t=length==0?0:Math.max(0,Math.min(1,point.subtract(start).dot(segment)/length));
        return start.add(segment.scale(t)).distanceToSqr(point);
    }
    private static boolean finite(Vec3 v) { return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
}
