package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.entities.SingularityEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared per-tick delta-velocity calculation for singularity gravity. */
public final class SingularityGravityPulse {
    private SingularityGravityPulse() { }

    public static Vec3 deltaVelocity(Level level, Vec3 target, long gameTime) {
        double searchRadius;
        try {
            searchRadius = ModSingularityGameplay.catalog(level).values().stream()
                    .mapToDouble(profile -> profile.gravityPullRangeAtLevel(6)).max().orElse(0d);
        } catch (RuntimeException catalogUnavailable) {
            return Vec3.ZERO;
        }
        if (searchRadius <= 0 || !Double.isFinite(searchRadius)) return Vec3.ZERO;
        Vec3 total = Vec3.ZERO;
        for (SingularityEntity source : level.getEntitiesOfClass(SingularityEntity.class,
                new net.minecraft.world.phys.AABB(target, target).inflate(searchRadius))) {
            SingularityGameplayProfile profile = source.gravityProfileIfReady();
            if (profile == null) continue;
            total = total.add(sourceDeltaVelocity(source.position(), target, source.singularityLevel(), profile, .05));
        }
        return total;
    }

    public static Vec3 sourceDeltaVelocity(Vec3 center, Vec3 target, int level,
                                           SingularityGameplayProfile profile, double elapsedSeconds) {
        if (profile == null || level <= 0 || !Double.isFinite(elapsedSeconds) || elapsedSeconds <= 0) return Vec3.ZERO;
        Vec3 displacement = center.subtract(target);
        double distanceSquared = displacement.lengthSqr();
        double range = profile.gravityPullRangeAtLevel(level);
        if (distanceSquared < 1.0e-5 || distanceSquared > range * range) return Vec3.ZERO;
        double envelope = profile.gravityRadialEnvelopeAtLevel(level, Math.sqrt(distanceSquared));
        return displacement.scale(profile.continuousGravityAccelerationAtLevel(level) * envelope * elapsedSeconds / distanceSquared);
    }
}

