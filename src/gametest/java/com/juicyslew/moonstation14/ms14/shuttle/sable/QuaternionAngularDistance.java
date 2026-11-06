package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Quaterniondc;

/** Pure, sign-invariant angular distance for sampled native and predicted orientations. */
public final class QuaternionAngularDistance {
    private QuaternionAngularDistance() { }
    public static double radians(Quaterniondc a, Quaterniondc b) {
        double an = Math.sqrt(a.x() * a.x() + a.y() * a.y() + a.z() * a.z() + a.w() * a.w());
        double bn = Math.sqrt(b.x() * b.x() + b.y() * b.y() + b.z() * b.z() + b.w() * b.w());
        if (!Double.isFinite(an) || !Double.isFinite(bn) || an < 1e-12 || bn < 1e-12)
            throw new IllegalArgumentException("invalid native/predicted quaternion norm");
        double dot = (a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w()) / (an * bn);
        if (!Double.isFinite(dot)) throw new IllegalArgumentException("nonfinite quaternion dot");
        return 2 * Math.acos(Math.max(0, Math.min(1, Math.abs(dot))));
    }
}
