package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Small, shared codecs for the closed and semantically constrained effect fields. */
final class EffectCodecHelpers {
    static final Codec<Float> FINITE_FLOAT = Codec.FLOAT.validate(value ->
            Float.isFinite(value)
                    ? DataResult.success(value)
                    : DataResult.error(() -> "expected a finite number"));
    // These semantic restrictions intentionally remain stricter than nullable/raw upstream fields.
    static final Codec<Float> NONNEGATIVE_FLOAT = FINITE_FLOAT.validate(value ->
            value >= 0f
                    ? DataResult.success(value)
                    : DataResult.error(() -> "expected a nonnegative number"));
    static final Codec<Integer> EXACT_INT = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value == Math.rint(value)
                    && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    ? DataResult.success(value)
                    : DataResult.error(() -> "expected an exact integer"))
            .xmap(Double::intValue, Integer::doubleValue);
    static final Codec<Integer> NONNEGATIVE_INT = EXACT_INT.validate(value ->
            value >= 0
                    ? DataResult.success(value)
                    : DataResult.error(() -> "expected a nonnegative integer"));
    static final Codec<String> NONBLANK_STRING = Codec.STRING.validate(value ->
            !value.isBlank()
                    ? DataResult.success(value)
                    : DataResult.error(() -> "expected a nonblank string"));

    private EffectCodecHelpers() {
    }

    static <T> Codec<T> closedString(Map<String, T> values, Function<T, String> serializedName) {
        return Codec.STRING.comapFlatMap(value -> {
            T result = values.get(value);
            return result == null
                    ? DataResult.error(() -> "unknown value '" + value + "'")
                    : DataResult.success(result);
        }, serializedName);
    }

    static Codec<String> closedString(Set<String> values) {
        return Codec.STRING.validate(value -> values.contains(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "unknown value '" + value + "'"));
    }
}
