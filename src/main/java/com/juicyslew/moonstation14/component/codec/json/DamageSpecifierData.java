package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;

import java.util.Map;
import java.util.Objects;

public record DamageSpecifierData(Map<String, Float> types) {
    public DamageSpecifierData {
        types = DamageKeys.validateDelta(Objects.requireNonNull(types, "types"));
    }

    static final java.util.Set<String> KNOWN_DAMAGE_TYPES = DamageKeys.ALL;

    // An omitted types object is the canonical empty damage specifier; explicit JSON null is not accepted.
    public static final Codec<DamageSpecifierData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(EffectCodecHelpers.closedString(KNOWN_DAMAGE_TYPES),
                    EffectCodecHelpers.FINITE_FLOAT).optionalFieldOf("types", Map.of())
                    .forGetter(DamageSpecifierData::types)
    ).apply(inst, DamageSpecifierData::new));
}
