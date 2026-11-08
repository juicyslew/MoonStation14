package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved source threshold: danger bounds and warning multipliers around those bounds. */
public record AtmosAlarmThresholdData(Double lowerBound, Double upperBound,
                                     Double lowerWarnAround, Double upperWarnAround, boolean ignore) {
    public static final Codec<AtmosAlarmThresholdData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("lowerBound").forGetter(v -> java.util.Optional.ofNullable(v.lowerBound)),
            Codec.DOUBLE.optionalFieldOf("upperBound").forGetter(v -> java.util.Optional.ofNullable(v.upperBound)),
            Codec.DOUBLE.optionalFieldOf("lowerWarnAround").forGetter(v -> java.util.Optional.ofNullable(v.lowerWarnAround)),
            Codec.DOUBLE.optionalFieldOf("upperWarnAround").forGetter(v -> java.util.Optional.ofNullable(v.upperWarnAround)),
            Codec.BOOL.optionalFieldOf("ignore", false).forGetter(AtmosAlarmThresholdData::ignore)
    ).apply(i, (low, high, lowWarn, highWarn, ignore) -> new AtmosAlarmThresholdData(
            low.orElse(null), high.orElse(null), lowWarn.orElse(null), highWarn.orElse(null), ignore)));

    public AtmosAlarmThresholdData {
        if (!ignore) {
            checkBound(lowerBound, "lowerBound"); checkBound(upperBound, "upperBound");
            checkMultiplier(lowerWarnAround, "lowerWarnAround"); checkMultiplier(upperWarnAround, "upperWarnAround");
            if (lowerBound == null && upperBound == null) throw new IllegalArgumentException("threshold needs a bound");
            if (lowerWarnAround != null && lowerBound == null || upperWarnAround != null && upperBound == null)
                throw new IllegalArgumentException("warning multiplier requires its danger bound");
        }
    }
    private static void checkBound(Double n, String name) { if (n != null && (!Double.isFinite(n) || n < 0)) throw new IllegalArgumentException("invalid " + name); }
    private static void checkMultiplier(Double n, String name) { if (n != null && (!Double.isFinite(n) || n <= 0)) throw new IllegalArgumentException("invalid " + name); }

    public AtmosAlarmSeverity classify(double value) {
        if (ignore || !Double.isFinite(value)) return AtmosAlarmSeverity.NORMAL;
        if (lowerBound != null && value <= lowerBound || upperBound != null && value >= upperBound) return AtmosAlarmSeverity.DANGER;
        if (lowerBound != null && lowerWarnAround != null && value <= lowerBound * lowerWarnAround
                || upperBound != null && upperWarnAround != null && value >= upperBound * upperWarnAround)
            return AtmosAlarmSeverity.WARNING;
        return AtmosAlarmSeverity.NORMAL;
    }
}
