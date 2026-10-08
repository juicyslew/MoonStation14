package com.juicyslew.moonstation14.ms14.doors.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Typed local projection of the supported SS14 Electrified component fields. */
public record ElectrifiedData(boolean enabled, boolean usesApcPower, boolean requirePower,
                              float shockDamage, float shockSeconds, float electrificationCancelSeconds,
                              float probability, float siemensCoefficient,
                              boolean onTouch, boolean onBump, boolean onUsing, boolean onAttacked) {
    public static final ElectrifiedData AIRLOCK_DEFAULTS = new ElectrifiedData(
            false, true, true, 7.5f, 5f, 30f, 1f, 1f, true, true, true, true);

    public static final Codec<ElectrifiedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", AIRLOCK_DEFAULTS.enabled()).forGetter(ElectrifiedData::enabled),
            Codec.BOOL.optionalFieldOf("usesApcPower", AIRLOCK_DEFAULTS.usesApcPower()).forGetter(ElectrifiedData::usesApcPower),
            Codec.BOOL.optionalFieldOf("requirePower", AIRLOCK_DEFAULTS.requirePower()).forGetter(ElectrifiedData::requirePower),
            Codec.FLOAT.optionalFieldOf("shockDamage", AIRLOCK_DEFAULTS.shockDamage()).forGetter(ElectrifiedData::shockDamage),
            Codec.FLOAT.optionalFieldOf("shockSeconds", AIRLOCK_DEFAULTS.shockSeconds()).forGetter(ElectrifiedData::shockSeconds),
            Codec.FLOAT.optionalFieldOf("electrificationCancelSeconds", AIRLOCK_DEFAULTS.electrificationCancelSeconds()).forGetter(ElectrifiedData::electrificationCancelSeconds),
            Codec.FLOAT.optionalFieldOf("probability", AIRLOCK_DEFAULTS.probability()).forGetter(ElectrifiedData::probability),
            Codec.FLOAT.optionalFieldOf("siemensCoefficient", AIRLOCK_DEFAULTS.siemensCoefficient()).forGetter(ElectrifiedData::siemensCoefficient),
            Codec.BOOL.optionalFieldOf("onTouch", AIRLOCK_DEFAULTS.onTouch()).forGetter(ElectrifiedData::onTouch),
            Codec.BOOL.optionalFieldOf("onBump", AIRLOCK_DEFAULTS.onBump()).forGetter(ElectrifiedData::onBump),
            Codec.BOOL.optionalFieldOf("onUsing", AIRLOCK_DEFAULTS.onUsing()).forGetter(ElectrifiedData::onUsing),
            Codec.BOOL.optionalFieldOf("onAttacked", AIRLOCK_DEFAULTS.onAttacked()).forGetter(ElectrifiedData::onAttacked)
    ).apply(i, ElectrifiedData::new));

    public ElectrifiedData {
        for (float value : new float[]{shockDamage, shockSeconds, electrificationCancelSeconds, probability, siemensCoefficient})
            if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("invalid electrified setting");
        if (electrificationCancelSeconds <= 0) throw new IllegalArgumentException("electrification cancel timeout must be positive");
        if (probability > 1) throw new IllegalArgumentException("electrified probability exceeds one");
    }
}
