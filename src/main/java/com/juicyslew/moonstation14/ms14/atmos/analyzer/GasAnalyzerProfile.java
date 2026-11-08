package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved local handheld analyzer capabilities. Range/distance are Minecraft presentation inputs. */
public record GasAnalyzerProfile(int maximumRange, int defaultSampleDistance, int updateIntervalTicks,
                                 double minimumGasMoles) {
    public static final Codec<GasAnalyzerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 64).optionalFieldOf("maximumRange", 6).forGetter(GasAnalyzerProfile::maximumRange),
            Codec.intRange(0, 64).optionalFieldOf("defaultSampleDistance", 1).forGetter(GasAnalyzerProfile::defaultSampleDistance),
            Codec.intRange(1, 1200).optionalFieldOf("updateIntervalTicks", 20).forGetter(GasAnalyzerProfile::updateIntervalTicks),
            Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("minimumGasMoles", .01).forGetter(GasAnalyzerProfile::minimumGasMoles)
    ).apply(i, GasAnalyzerProfile::new));

    public GasAnalyzerProfile {
        if (maximumRange < 1 || defaultSampleDistance < 0 || defaultSampleDistance > maximumRange
                || updateIntervalTicks < 1 || !Double.isFinite(minimumGasMoles) || minimumGasMoles < 0)
            throw new IllegalArgumentException("Invalid gas analyzer profile");
    }
}
