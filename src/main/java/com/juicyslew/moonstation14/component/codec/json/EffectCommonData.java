package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** The flat, shared portion of every ordinary reagent effect. */
public record EffectCommonData(List<ConditionData> conditions, float probability, float minScale, boolean scaling) {
    public EffectCommonData {
        // Null is intentionally not a second spelling of the canonical omitted field.
        conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
    }

    public static final EffectCommonData DEFAULT = new EffectCommonData(List.of(), 1.0f, 0.0f, true);

    public static final MapCodec<EffectCommonData> CODEC = RecordCodecBuilder.<EffectCommonData>mapCodec(instance -> instance.group(
            Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(EffectCommonData::conditions),
            EffectCodecHelpers.FINITE_FLOAT.optionalFieldOf("probability", 1.0f).forGetter(EffectCommonData::probability),
            EffectCodecHelpers.FINITE_FLOAT.optionalFieldOf("minscale", 0.0f).forGetter(EffectCommonData::minScale),
            Codec.BOOL.optionalFieldOf("scaling", true).forGetter(EffectCommonData::scaling)
    ).apply(instance, EffectCommonData::new));

    /**
     * Adds the common fields to a subtype codec without changing their flat
     * JSON representation.  Keeping this operation here prevents each effect
     * from growing its own subtly different copy of the common schema.
     */
    public static <T> MapCodec<T> withCommon(
            MapCodec<T> specific,
            Function<T, EffectCommonData> commonGetter,
            BiFunction<T, EffectCommonData, T> commonSetter) {
        return specific.dependent(EffectCommonData.CODEC,
                value -> Pair.of(commonGetter.apply(value), EffectCommonData.CODEC),
                commonSetter);
    }
}
