package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Body-connected 3D strike presentation timing and trail style. */
public record MeleeStrikeProfile(double durationSeconds, double extensionEndFraction,
                                 double retractionStartFraction, double trailWidth, double trailAlpha) {
    public static final Codec<MeleeStrikeProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("duration_seconds").forGetter(MeleeStrikeProfile::durationSeconds),
            Codec.DOUBLE.fieldOf("extension_end_fraction").forGetter(MeleeStrikeProfile::extensionEndFraction),
            Codec.DOUBLE.fieldOf("retraction_start_fraction").forGetter(MeleeStrikeProfile::retractionStartFraction),
            Codec.DOUBLE.fieldOf("trail_width").forGetter(MeleeStrikeProfile::trailWidth),
            Codec.DOUBLE.fieldOf("trail_alpha").forGetter(MeleeStrikeProfile::trailAlpha)
    ).apply(i, MeleeStrikeProfile::new));
    public MeleeStrikeProfile {
        if (!bounded(durationSeconds, .01, 10) || !bounded(extensionEndFraction, 0, 1)
                || extensionEndFraction <= 0 || !bounded(retractionStartFraction, extensionEndFraction, 1)
                || retractionStartFraction >= 1
                || !bounded(trailWidth, .001, 2) || !bounded(trailAlpha, 0, 1))
            throw new IllegalArgumentException("invalid melee strike presentation profile");
    }
    private static boolean bounded(double x, double min, double max) { return Double.isFinite(x) && x >= min && x <= max; }
}
