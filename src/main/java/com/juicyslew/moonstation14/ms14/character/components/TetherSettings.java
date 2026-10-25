package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Local progressive tether tuning, resolved with the puller capability. */
public record TetherSettings(double stiffness, double progressiveStiffness, double damping,
                              double maximumAcceleration, double slackAllowance) {
    public static final TetherSettings DEFAULT = new TetherSettings(60, 40, 6, 160, 1.5);
    public static final Codec<TetherSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("stiffness", 60d).forGetter(TetherSettings::stiffness),
            Codec.DOUBLE.optionalFieldOf("progressive_stiffness", 40d).forGetter(TetherSettings::progressiveStiffness),
            Codec.DOUBLE.optionalFieldOf("damping", 6d).forGetter(TetherSettings::damping),
            Codec.DOUBLE.optionalFieldOf("maximum_acceleration", 160d).forGetter(TetherSettings::maximumAcceleration),
            Codec.DOUBLE.optionalFieldOf("slackAllowance", 1.5d).forGetter(TetherSettings::slackAllowance)
    ).apply(i, TetherSettings::new));
    public TetherSettings {
        if (!valid(stiffness) || !valid(progressiveStiffness) || !valid(damping)
                || !Double.isFinite(maximumAcceleration) || maximumAcceleration <= 0 || maximumAcceleration > 200)
            throw new IllegalArgumentException("invalid tether settings");
        if (!Double.isFinite(slackAllowance) || slackAllowance < 0) throw new IllegalArgumentException("invalid slack allowance");
    }
    public TetherSettings(double stiffness, double progressiveStiffness, double damping, double maximumAcceleration) {
        this(stiffness, progressiveStiffness, damping, maximumAcceleration, 1.5);
    }
    private static boolean valid(double value) { return Double.isFinite(value) && value >= 0 && value <= 200; }
}

