package com.juicyslew.moonstation14.ms14.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved SS14-style radiation source values (intensity is rads/second at origin). */
public record RadiationSourceProfile(double intensity, double slope) {
    public static final Codec<RadiationSourceProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("intensity").forGetter(RadiationSourceProfile::intensity),
            Codec.DOUBLE.fieldOf("slope").forGetter(RadiationSourceProfile::slope)
    ).apply(i, RadiationSourceProfile::new));
    public RadiationSourceProfile {
        if (!Double.isFinite(intensity) || intensity < 0 || !Double.isFinite(slope) || slope < 0)
            throw new IllegalArgumentException("invalid radiation source profile");
    }

    public double range() { return slope == 0 ? 50 : Math.min(intensity / slope, 50); }
    public double atDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > range()) return 0;
        return Math.max(0, intensity - slope * distance);
    }
}

