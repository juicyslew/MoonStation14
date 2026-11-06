package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Function;

/** Pure selection of a captured native side contact for a world-space normal target. */
public final class NativeHullContactTargetSelector {
    private NativeHullContactTargetSelector() { }

    public static NativeNormalContactPolicy.Target select(Vec3 incomingWorldMps,
            List<NativeSelectedContactAccess.Entry> entries, long gameTime, boolean overflowed,
            Function<NativeSelectedContactAccess.Entry, Vec3> surfaceAt) {
        if (!finite(incomingWorldMps) || entries == null || entries.isEmpty() || overflowed || surfaceAt == null) return null;
        Vec3 firstNormal = null;
        NativeNormalContactPolicy.Target best = null;
        double strongestClosing = 0;
        for (var entry : entries) {
            if (entry == null || entry.verticalCollision()) continue;
            if (entry.levelGameTime() != gameTime || entry.entityPosition() == null || !finite(entry.normal())
                    || entry.normal().lengthSqr() < 1.0e-12 || !finite(entry.entityVelocity())) return null;
            Vec3 normal = entry.normal().normalize();
            if (firstNormal != null && firstNormal.dot(normal) <= .999) return null;
            if (firstNormal == null) firstNormal = normal;
            Vec3 surface = surfaceAt.apply(entry);
            if (!finite(surface)) return null;
            double closing = incomingWorldMps.subtract(surface).dot(normal);
            if (closing < strongestClosing) {
                strongestClosing = closing;
                best = NativeNormalContactPolicy.prescribe(incomingWorldMps, surface, normal, 0);
            }
        }
        return best;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
