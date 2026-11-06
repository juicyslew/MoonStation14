package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.joml.Quaternionf;

/** Composition and angle extraction using Minecraft Camera's own rotation convention. */
public final class PureAngularRecoveryCameraMath {
    private PureAngularRecoveryCameraMath() { }

    public static Angles compose(Quaternionf inertialWorldRotation, float yaw, float pitch, float roll) {
        Quaternionf camera = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(180.0 - yaw), (float) Math.toRadians(-pitch), (float) Math.toRadians(-roll));
        Quaternionf world = new Quaternionf(inertialWorldRotation).mul(camera).normalize();
        double norm=Math.sqrt((double)world.x*world.x+(double)world.y*world.y+(double)world.z*world.z+(double)world.w*world.w);
        double x=world.x/norm, y=world.y/norm, z=world.z/norm, w=world.w/norm;
        double m02=2.0*(x*z+w*y), m22=1.0-2.0*(x*x+y*y), m12=2.0*(y*z-w*x);
        double m10=2.0*(x*y+w*z), m11=1.0-2.0*(x*x+z*z);
        double m20=2.0*(x*z-w*y), m00=1.0-2.0*(y*y+z*z);
        double cosPitch=Math.hypot(m02,m22);
        double eulerX=Math.atan2(-m12,cosPitch);
        // Below this matrix-horizontal magnitude yaw and roll are numerically inseparable.
        // Choose the equivalent YXZ representation with roll zero at the pole.
        boolean singular=cosPitch<=1e-6;
        double eulerY=singular ? Math.atan2(-m20,m00) : Math.atan2(m02,m22);
        double eulerZ=singular ? 0.0 : Math.atan2(m10,m11);
        return new Angles((float) (180.0 - Math.toDegrees(eulerY)), (float) -Math.toDegrees(eulerX),
                (float) -Math.toDegrees(eulerZ));
    }

    public record Angles(float yaw, float pitch, float roll) { }
}
