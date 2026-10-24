package com.juicyslew.moonstation14.ms14.singularity;

import net.minecraft.world.phys.Vec3;

/** Pure direction shaping used by the resolved-profile random-walk consumer. */
public final class SingularityWalkMath {
    private SingularityWalkMath() { }
    public static Vec3 direction(double x, double y, double z, double verticalScale,
                                  double escapeBias, Vec3 attractionBias) {
        x = finiteOrZero(x); y = finiteOrZero(y); z = finiteOrZero(z);
        verticalScale = Double.isFinite(verticalScale) ? Math.max(0, verticalScale) : 0;
        escapeBias = Double.isFinite(escapeBias) ? Math.max(0, escapeBias) : 0;
        y *= verticalScale;
        double horizontal = Math.hypot(x, z);
        if (horizontal > 1.0e-12 && escapeBias > 0) {
            x += x / horizontal * escapeBias;
            z += z / horizontal * escapeBias;
        }
        Vec3 shaped = new Vec3(x, y, z);
        if (attractionBias != null && Double.isFinite(attractionBias.x) && Double.isFinite(attractionBias.y) && Double.isFinite(attractionBias.z))
            shaped = shaped.add(attractionBias);
        return shaped.lengthSqr() > 1.0e-24 ? shaped.normalize() : Vec3.ZERO;
    }
    public static boolean escapeActive(long now, long until) { return now < until; }
    public static boolean stageDropResetsVelocity(int previousLevel, int currentLevel) {
        return previousLevel > 1 && currentLevel <= 1;
    }
    private static double finiteOrZero(double value) { return Double.isFinite(value) ? value : 0; }
}
