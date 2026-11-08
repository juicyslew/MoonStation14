package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;

/** Hand-height 3D casing spawn location adapted from body eye and weapon aim. */
public final class SpentCasingOrigin {
    private SpentCasingOrigin() { }
    public static Vec3 resolve(Vec3 eye, Vec3 aim, boolean leftHand) {
        if(eye==null||aim==null||!finite(eye)||!finite(aim)||aim.lengthSqr()<1e-8)throw new IllegalArgumentException("invalid casing origin inputs");
        Vec3 axis=aim.normalize();Vec3 side=axis.cross(new Vec3(0,1,0));if(side.lengthSqr()<1e-8)side=new Vec3(1,0,0);else side=side.normalize();
        if(leftHand)side=side.scale(-1);
        return eye.add(0,-.32,0).add(axis.scale(.35)).add(side.scale(.22));
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
