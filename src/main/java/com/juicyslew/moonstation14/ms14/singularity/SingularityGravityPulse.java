package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.entities.SingularityEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared delta-velocity calculation for a singularity gravity pulse. */
public final class SingularityGravityPulse {
    public static final int PULSE_TICKS = 10;

    /** Immutable source-local schedule. A late pulse advances only one deadline. */
    public record Schedule(long nextTick) {
        public Schedule advance(long now) {
            if (now < nextTick) throw new IllegalArgumentException("pulse is not due");
            return new Schedule(nextTick + PULSE_TICKS);
        }
        public double elapsedSeconds(long now) {
            return now < nextTick ? 0d : Math.max(0L, now - (nextTick - PULSE_TICKS)) * .05d;
        }
    }

    /** Replay-safe local prediction: only nominal scheduled pulse slots are predicted. */
    public static double predictedElapsedSeconds(long firstPulseTick, long requestedTick) {
        if (requestedTick < firstPulseTick
                || Math.floorMod(requestedTick - firstPulseTick, PULSE_TICKS) != 0) return 0d;
        return PULSE_TICKS * .05d;
    }

    private SingularityGravityPulse() { }

    public static Vec3 deltaVelocity(Level level, Vec3 target, long gameTime) {
        double searchRadius;
        try {
            searchRadius = ModSingularityGameplay.catalog(level).values().stream()
                    .mapToDouble(profile -> profile.gravityRangeAtLevel(6)).max().orElse(0d);
        } catch (RuntimeException catalogUnavailable) {
            return Vec3.ZERO;
        }
        if (searchRadius <= 0 || !Double.isFinite(searchRadius)) return Vec3.ZERO;
        Vec3 total = Vec3.ZERO;
        for (SingularityEntity source : level.getEntitiesOfClass(SingularityEntity.class,
                new net.minecraft.world.phys.AABB(target, target).inflate(searchRadius))) {
            SingularityGameplayProfile profile = source.gravityProfileIfReady();
            if (profile == null) continue;
            // Do not consume the source's mutable server deadline while its resolved
            // gameplay profile is unavailable (e.g. during catalog reload).
            double elapsed = source.gravityPulseElapsed(gameTime);
            if (elapsed <= 0) continue;
            total = total.add(sourceDeltaVelocity(source.position(), target, source.singularityLevel(), profile, elapsed));
        }
        return total;
    }

    public static Vec3 sourceDeltaVelocity(Vec3 center, Vec3 target, int level,
                                           SingularityGameplayProfile profile, double elapsedSeconds) {
        if (profile == null || level <= 0 || !Double.isFinite(elapsedSeconds) || elapsedSeconds <= 0) return Vec3.ZERO;
        Vec3 displacement = center.subtract(target);
        double distanceSquared = displacement.lengthSqr();
        double range = profile.gravityRangeAtLevel(level);
        if (distanceSquared < 1.0e-5 || distanceSquared > range * range) return Vec3.ZERO;
        return displacement.scale(profile.gravityAccelerationAtLevel(level) * elapsedSeconds / distanceSquared);
    }
}

