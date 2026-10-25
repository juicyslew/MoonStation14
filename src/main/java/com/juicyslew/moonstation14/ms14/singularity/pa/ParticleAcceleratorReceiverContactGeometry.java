package com.juicyslew.moonstation14.ms14.singularity.pa;

import net.minecraft.world.phys.Vec3;

/** PA-only vertical receiver capture adaptation; horizontal capture remains circular and unchanged. */
public final class ParticleAcceleratorReceiverContactGeometry {
    private ParticleAcceleratorReceiverContactGeometry() { }

    public static double entry(Vec3 from, Vec3 to, Vec3 center, double radius, double verticalPadding) {
        if (radius <= 0 || verticalPadding < 0) return -1;
        if (verticalPadding == 0) return sphereEntry(from, to, center, radius);
        double scale = radius / (radius + verticalPadding);
        Vec3 a = new Vec3(from.x, center.y + (from.y - center.y) * scale, from.z);
        Vec3 b = new Vec3(to.x, center.y + (to.y - center.y) * scale, to.z);
        return sphereEntry(a, b, center, radius);
    }

    private static double sphereEntry(Vec3 a, Vec3 b, Vec3 center, double radius) {
        Vec3 d=b.subtract(a), m=a.subtract(center); double aa=d.dot(d), cc=m.dot(m)-radius*radius;
        if (cc<=0) return 0; if (aa<=1e-12) return -1;
        double bb=m.dot(d), disc=bb*bb-aa*cc; if(disc<0) return -1;
        double t=(-bb-Math.sqrt(disc))/aa; return t>=0&&t<=1?t:-1;
    }
}
