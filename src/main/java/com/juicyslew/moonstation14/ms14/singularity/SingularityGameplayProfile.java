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
                                             SingularitySoundProfile sounds, EventHorizon eventHorizon, RandomWalk randomWalk,
                                               double gravityStrengthMultiplier, GravityRamp gravityRamp) {
    public SingularityGameplayProfile(double initialEnergy, int initialLevel, double radsPerLevel, double energyLoss,
            double gravityAcceleration, double gravityRange, double minSpeed, double maxSpeed, List<Double> levelThresholds,
            List<Double> drainPerSecond, RadiationSourceProfile radiation, SingularitySoundProfile sounds,
            EventHorizon eventHorizon, RandomWalk randomWalk) {
        this(initialEnergy, initialLevel, radsPerLevel, energyLoss, gravityAcceleration, gravityRange, minSpeed, maxSpeed,
                levelThresholds, drainPerSecond, radiation, sounds, eventHorizon, randomWalk, 15d, GravityRamp.DEFAULT);
    }
    public record GravityRamp(double rangeMultiplier, double fullStrengthRadiusScale) {
        public static final GravityRamp DEFAULT = new GravityRamp(3d, 2d);
        public static final Codec<GravityRamp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("rangeMultiplier", 3d).forGetter(GravityRamp::rangeMultiplier),
                Codec.DOUBLE.optionalFieldOf("fullStrengthRadiusScale", 2d).forGetter(GravityRamp::fullStrengthRadiusScale)
        ).apply(i, GravityRamp::new));
    }
    public double gravityRangeMultiplier() { return gravityRamp.rangeMultiplier(); }
    public double gravityRampFullStrengthRadiusScale() { return gravityRamp.fullStrengthRadiusScale(); }
    public record EventHorizon(boolean consumeEntities, boolean consumeTiles, double consumePeriodSeconds,
                                 double entityEnergy, double terrainEnergy, double lowYieldTerrainEnergy,
                                 double playerLethalRadiusScale, double playerLethalRadiusMax) {
        public static final EventHorizon DEFAULT = new EventHorizon(true, true, 0.5, 1, 1, 0.01, .35, .35);
        public EventHorizon(boolean consumeEntities, boolean consumeTiles, double consumePeriodSeconds,
                            double entityEnergy, double terrainEnergy, double lowYieldTerrainEnergy,
                            double playerLethalRadiusScale) {
            this(consumeEntities, consumeTiles, consumePeriodSeconds, entityEnergy, terrainEnergy, lowYieldTerrainEnergy,
                    playerLethalRadiusScale, .35);
        }
        public EventHorizon(boolean consumeEntities, boolean consumeTiles, double consumePeriodSeconds,
                            double entityEnergy, double terrainEnergy, double lowYieldTerrainEnergy) {
            this(consumeEntities, consumeTiles, consumePeriodSeconds, entityEnergy, terrainEnergy, lowYieldTerrainEnergy, .35);
        }
        public EventHorizon(boolean consumeEntities, double entityEnergy) {
            this(consumeEntities, true, 0.5, entityEnergy, 1, 0.01, .35, .35);
        }
        public static final Codec<EventHorizon> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("consumeEntities", true).forGetter(EventHorizon::consumeEntities),
                Codec.BOOL.optionalFieldOf("consumeTiles", true).forGetter(EventHorizon::consumeTiles),
                Codec.DOUBLE.optionalFieldOf("consumePeriodSeconds", 0.5d).forGetter(EventHorizon::consumePeriodSeconds),
                Codec.DOUBLE.optionalFieldOf("entityEnergy", 1d).forGetter(EventHorizon::entityEnergy),
                Codec.DOUBLE.optionalFieldOf("terrainEnergy", 1d).forGetter(EventHorizon::terrainEnergy),
                Codec.DOUBLE.optionalFieldOf("lowYieldTerrainEnergy", 0.01d).forGetter(EventHorizon::lowYieldTerrainEnergy),
                 Codec.DOUBLE.optionalFieldOf("playerLethalRadiusScale", .35d).forGetter(EventHorizon::playerLethalRadiusScale),
                 Codec.DOUBLE.optionalFieldOf("playerLethalRadiusMax", .35d).forGetter(EventHorizon::playerLethalRadiusMax)
        ).apply(i, EventHorizon::new));
    }
    public record RandomWalk(int minimumIntervalTicks, int maximumIntervalTicks, double verticalScale, int initialEscapeTicks,
                             double initialEscapeHorizontalBias) {
        public static final RandomWalk DEFAULT = new RandomWalk(40, 100, 0.7, 200, 1.8);
        public static final Codec<RandomWalk> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("minimumIntervalTicks", 40).forGetter(RandomWalk::minimumIntervalTicks),
                Codec.INT.optionalFieldOf("maximumIntervalTicks", 100).forGetter(RandomWalk::maximumIntervalTicks),
                Codec.DOUBLE.optionalFieldOf("verticalScale", 0.7).forGetter(RandomWalk::verticalScale),
                Codec.INT.optionalFieldOf("initialEscapeTicks", 200).forGetter(RandomWalk::initialEscapeTicks),
                Codec.DOUBLE.optionalFieldOf("initialEscapeHorizontalBias", 1.8).forGetter(RandomWalk::initialEscapeHorizontalBias)
        ).apply(i, RandomWalk::new));
        public void validate() {
            if (minimumIntervalTicks < 1 || maximumIntervalTicks < minimumIntervalTicks
                    || !Double.isFinite(verticalScale) || verticalScale < 0 || initialEscapeTicks < 0
                    || !Double.isFinite(initialEscapeHorizontalBias) || initialEscapeHorizontalBias < 0)
                throw new IllegalArgumentException("invalid singularity random walk");
        }
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
            EventHorizon.CODEC.optionalFieldOf("eventHorizon", EventHorizon.DEFAULT).forGetter(SingularityGameplayProfile::eventHorizon),
            RandomWalk.CODEC.optionalFieldOf("randomWalk", RandomWalk.DEFAULT).forGetter(SingularityGameplayProfile::randomWalk)
              ,Codec.DOUBLE.optionalFieldOf("gravityStrengthMultiplier", 15d).forGetter(SingularityGameplayProfile::gravityStrengthMultiplier),
              GravityRamp.CODEC.optionalFieldOf("gravityRamp", GravityRamp.DEFAULT).forGetter(SingularityGameplayProfile::gravityRamp)
    ).apply(i, SingularityGameplayProfile::new));

    public SingularityGameplayProfile(double initialEnergy, int initialLevel, double radsPerLevel, double energyLoss,
            double gravityAcceleration, double gravityRange, double minSpeed, double maxSpeed, List<Double> levelThresholds,
            List<Double> drainPerSecond, RadiationSourceProfile radiation) {
        this(initialEnergy, initialLevel, radsPerLevel, energyLoss, gravityAcceleration, gravityRange, minSpeed, maxSpeed,
                levelThresholds, drainPerSecond, radiation, SingularitySoundProfile.DEFAULT, EventHorizon.DEFAULT, RandomWalk.DEFAULT);
    }
    public SingularityGameplayProfile(double initialEnergy, int initialLevel, double radsPerLevel, double energyLoss,
            double gravityAcceleration, double gravityRange, double minSpeed, double maxSpeed, List<Double> levelThresholds,
            List<Double> drainPerSecond, RadiationSourceProfile radiation, SingularitySoundProfile sounds) {
        this(initialEnergy, initialLevel, radsPerLevel, energyLoss, gravityAcceleration, gravityRange, minSpeed, maxSpeed,
                levelThresholds, drainPerSecond, radiation, sounds, EventHorizon.DEFAULT, RandomWalk.DEFAULT);
    }
    public SingularityGameplayProfile(double initialEnergy, int initialLevel, double radsPerLevel, double energyLoss,
            double gravityAcceleration, double gravityRange, double minSpeed, double maxSpeed, List<Double> levelThresholds,
            List<Double> drainPerSecond, RadiationSourceProfile radiation, SingularitySoundProfile sounds, EventHorizon eventHorizon) {
        this(initialEnergy, initialLevel, radsPerLevel, energyLoss, gravityAcceleration, gravityRange, minSpeed, maxSpeed,
                levelThresholds, drainPerSecond, radiation, sounds, eventHorizon, RandomWalk.DEFAULT);
    }

    public void validate() {
        if (!finitePositive(initialEnergy) || initialLevel < 1 || !finitePositive(radsPerLevel) || !finitePositive(energyLoss) || !finitePositive(gravityRange)
                || !finitePositive(gravityAcceleration) || !finitePositive(minSpeed) || !finitePositive(maxSpeed) || maxSpeed < minSpeed
                || !finitePositive(gravityStrengthMultiplier) || gravityStrengthMultiplier > 1000
                || !finitePositive(gravityRangeMultiplier()) || gravityRangeMultiplier() > 100
                || !finitePositive(gravityRampFullStrengthRadiusScale()) || gravityRampFullStrengthRadiusScale() > 100
                || levelThresholds.size() != 7 || drainPerSecond.size() != 7 || levelThresholds.get(0) != 0
                || levelThresholds.get(1) != 0 || drainPerSecond.stream().anyMatch(v -> !Double.isFinite(v) || v < 0)
                || levelThresholds.stream().anyMatch(v -> !Double.isFinite(v) || v < 0)
                || !Double.isFinite(eventHorizon.entityEnergy()) || eventHorizon.entityEnergy() < 0
                || !Double.isFinite(eventHorizon.terrainEnergy()) || eventHorizon.terrainEnergy() < 0
                || !Double.isFinite(eventHorizon.lowYieldTerrainEnergy()) || eventHorizon.lowYieldTerrainEnergy() < 0
                || !Double.isFinite(eventHorizon.playerLethalRadiusScale()) || eventHorizon.playerLethalRadiusScale() <= 0 || eventHorizon.playerLethalRadiusScale() > 1
                || !Double.isFinite(eventHorizon.playerLethalRadiusMax()) || eventHorizon.playerLethalRadiusMax() <= 0
                || !Double.isFinite(eventHorizon.consumePeriodSeconds()) || eventHorizon.consumePeriodSeconds() <= 0
                || eventHorizon.consumePeriodSeconds() * 20 >= Long.MAX_VALUE)
            throw new IllegalArgumentException("invalid singularity gameplay profile");
        randomWalk.validate();
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
    /** Minecraft player-core adaptation; SS14 uses one shared EventHorizon radius for all contact. */
    public double playerLethalRadiusAtLevel(int level) { return Math.min(eventHorizonRadiusAtLevel(level) * eventHorizon.playerLethalRadiusScale(), eventHorizon.playerLethalRadiusMax()); }
    /** Pinned SharedGravityWellSystem: range scales with 2 * (level + 1). */
    public double gravityRangeAtLevel(int level) { return 2d * (level + 1); }
    public double gravityPullRangeAtLevel(int level) { return gravityRangeAtLevel(level) * gravityRangeMultiplier(); }
    public double gravityFullStrengthRadiusAtLevel(int level) {
        return 2d * (level + 1) * gravityRampFullStrengthRadiusScale();
    }
    /** Zero at the expanded outer boundary, smoothly reaching full pull inside the authored warning band. */
    public double gravityRadialEnvelopeAtLevel(int level, double distance) {
        double outer = gravityPullRangeAtLevel(level), inner = Math.min(outer, gravityFullStrengthRadiusAtLevel(level));
        if (!Double.isFinite(distance) || distance >= outer) return 0;
        if (distance <= inner) return 1;
        double t = (outer - distance) / (outer - inner);
        return t * t * (3 - 2 * t);
    }
    /** Pinned gravity well acceleration scale. */
    public double gravityAccelerationAtLevel(int level) { return gravityAcceleration * level; }
    /** Minecraft continuous-force adaptation: normalize old capture-boundary displacement to b/s². */
    public double continuousGravityAccelerationAtLevel(int level) {
        return gravityAccelerationAtLevel(level) * gravityStrengthMultiplier * eventHorizonRadiusAtLevel(level) / Math.max(1, level);
    }
    public boolean canBreachAtLevel(int level) { return level >= 5; }
}

