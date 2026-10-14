package com.juicyslew.moonstation14.ms14.singularity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.radiation.RadiationSourceProfile;
import java.util.List;

/** Simulation parameters adapted from SS14's root Singularity entity prototype. */
public record SingularityGameplayProfile(double initialEnergy, int initialLevel, double radsPerLevel,
                                         double energyLoss, double gravityAcceleration, double gravityRange,
                                         double minSpeed, double maxSpeed, List<Double> levelThresholds,
                                          List<Double> drainPerSecond, RadiationSourceProfile radiation,
                                            SingularitySoundProfile sounds, EventHorizon eventHorizon) {
    public record EventHorizon(boolean consumeEntities, double entityEnergy) {
        public static final EventHorizon DEFAULT = new EventHorizon(true, 1);
        public static final Codec<EventHorizon> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("consumeEntities", true).forGetter(EventHorizon::consumeEntities),
                Codec.DOUBLE.optionalFieldOf("entityEnergy", 1d).forGetter(EventHorizon::entityEnergy)
        ).apply(i, EventHorizon::new));
    }
    public static final Codec<SingularityGameplayProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("initialEnergy").forGetter(SingularityGameplayProfile::initialEnergy),
            Codec.INT.fieldOf("initialLevel").forGetter(SingularityGameplayProfile::initialLevel),
            Codec.DOUBLE.fieldOf("radsPerLevel").forGetter(SingularityGameplayProfile::radsPerLevel),
            Codec.DOUBLE.fieldOf("energyLoss").forGetter(SingularityGameplayProfile::energyLoss),
            Codec.DOUBLE.fieldOf("gravityAcceleration").forGetter(SingularityGameplayProfile::gravityAcceleration),
            Codec.DOUBLE.fieldOf("gravityRange").forGetter(SingularityGameplayProfile::gravityRange),
            Codec.DOUBLE.fieldOf("minSpeed").forGetter(SingularityGameplayProfile::minSpeed),
            Codec.DOUBLE.fieldOf("maxSpeed").forGetter(SingularityGameplayProfile::maxSpeed),
            Codec.DOUBLE.listOf().fieldOf("levelThresholds").forGetter(SingularityGameplayProfile::levelThresholds),
            Codec.DOUBLE.listOf().fieldOf("drainPerSecond").forGetter(SingularityGameplayProfile::drainPerSecond),
            RadiationSourceProfile.CODEC.fieldOf("radiation").forGetter(SingularityGameplayProfile::radiation),
            SingularitySoundProfile.CODEC.optionalFieldOf("sounds", SingularitySoundProfile.DEFAULT).forGetter(SingularityGameplayProfile::sounds),
            EventHorizon.CODEC.optionalFieldOf("eventHorizon", EventHorizon.DEFAULT).forGetter(SingularityGameplayProfile::eventHorizon)
    ).apply(i, SingularityGameplayProfile::new));

    public SingularityGameplayProfile(double initialEnergy, int initialLevel, double radsPerLevel, double energyLoss,
            double gravityAcceleration, double gravityRange, double minSpeed, double maxSpeed, List<Double> levelThresholds,
            List<Double> drainPerSecond, RadiationSourceProfile radiation) {
        this(initialEnergy, initialLevel, radsPerLevel, energyLoss, gravityAcceleration, gravityRange, minSpeed, maxSpeed,
                levelThresholds, drainPerSecond, radiation, SingularitySoundProfile.DEFAULT, EventHorizon.DEFAULT);
    }
    public SingularityGameplayProfile(double initialEnergy, int initialLevel, double radsPerLevel, double energyLoss,
            double gravityAcceleration, double gravityRange, double minSpeed, double maxSpeed, List<Double> levelThresholds,
            List<Double> drainPerSecond, RadiationSourceProfile radiation, SingularitySoundProfile sounds) {
        this(initialEnergy, initialLevel, radsPerLevel, energyLoss, gravityAcceleration, gravityRange, minSpeed, maxSpeed,
                levelThresholds, drainPerSecond, radiation, sounds, EventHorizon.DEFAULT);
    }

    public void validate() {
        if (!finitePositive(initialEnergy) || initialLevel < 1 || !finitePositive(radsPerLevel) || !finitePositive(energyLoss) || !finitePositive(gravityRange)
                || !finitePositive(gravityAcceleration) || !finitePositive(minSpeed) || !finitePositive(maxSpeed) || maxSpeed < minSpeed
                || levelThresholds.size() != 7 || drainPerSecond.size() != 7 || levelThresholds.get(0) != 0
                || levelThresholds.get(1) != 0 || drainPerSecond.stream().anyMatch(v -> !Double.isFinite(v) || v < 0)
                || levelThresholds.stream().anyMatch(v -> !Double.isFinite(v) || v < 0)
                || !Double.isFinite(eventHorizon.entityEnergy()) || eventHorizon.entityEnergy() < 0)
            throw new IllegalArgumentException("invalid singularity gameplay profile");
    }

    /** Energy thresholds are prototype-authored and level is bounded to the supported range. */
    private static boolean finitePositive(double v) { return Double.isFinite(v) && v > 0; }
    public int levelForEnergy(double energy) {
        if (!Double.isFinite(energy) || energy <= 0) return 0;
        int level = 1;
        for (int i = 2; i <= 6; i++) if (energy >= levelThresholds.get(i)) level = i;
        return level;
    }
    public double energyAfterSeconds(double energy, int level, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(energy)) throw new IllegalArgumentException("invalid energy timestep");
        return Math.max(0, energy - drainPerSecond.get(Math.max(0, Math.min(6, level))) * seconds);
    }
    public double minSpeedAtLevel(int level) { return 7.5 / Math.max(level, 4); }
    public double maxSpeedAtLevel(int level) { return 10.0 / Math.max(level, 4); }
    /** SharedEventHorizonSystem.cs:150-173; BOTH consumers scale by EventHorizon.Radius. */
    public double eventHorizonRadiusAtLevel(int level) { return Math.max(0, level - .5); }
    /** Pinned SharedGravityWellSystem: range scales with 2 * (level + 1). */
    public double gravityRangeAtLevel(int level) { return 2d * (level + 1); }
    /** Pinned gravity well acceleration scale. */
    public double gravityAccelerationAtLevel(int level) { return gravityAcceleration * level; }
    public boolean canBreachAtLevel(int level) { return level >= 5; }
}

