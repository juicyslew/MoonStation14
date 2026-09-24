package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.datafixers.util.Pair;

import java.util.stream.Stream;

/**
 * A status duration which keeps permanent and finite durations distinct.
 *
 * <p>Permanent durations are encoded as JSON {@code null}; finite durations
 * are encoded as nonnegative finite numbers.  The value is immutable so DTOs
 * cannot accidentally turn a permanent application into a timed one.</p>
 */
public record StatusEffectDuration(boolean isPermanent, float finiteSeconds) {
    public static final float DEFAULT_SECONDS = 2.0f;
    public static final StatusEffectDuration DEFAULT = finite(DEFAULT_SECONDS);

    /** A nullable-number codec where JSON null is the permanent value. */
    public static final Codec<StatusEffectDuration> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<StatusEffectDuration, T>> decode(DynamicOps<T> ops, T input) {
            // DynamicOps.empty() is the null value for JsonOps (and the
            // corresponding empty/null sentinel for the other vanilla ops).
            if (isNull(ops, input)) {
                return DataResult.success(Pair.of(permanent(), input));
            }
            return EffectCodecHelpers.NONNEGATIVE_FLOAT.decode(ops, input)
                    .map(pair -> Pair.of(finite(pair.getFirst()), pair.getSecond()));
        }

        @Override
        public <T> DataResult<T> encode(StatusEffectDuration value, DynamicOps<T> ops, T prefix) {
            if (value.isPermanent()) {
                return DataResult.success(ops.empty());
            }
            return EffectCodecHelpers.NONNEGATIVE_FLOAT.encode(value.finiteSeconds(), ops, prefix);
        }
    };

    /**
     * A field codec that treats absence as the supplied default but preserves
     * an explicit JSON null as permanent.  MapCodec.optionalFieldOf cannot be
     * used here because it intentionally conflates absent and null values.
     */
    public static MapCodec<StatusEffectDuration> fieldOf(String name) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<StatusEffectDuration> decode(DynamicOps<T> ops, MapLike<T> input) {
                // MapLike.get(String) is allowed to treat a null value like a
                // missing key.  Inspect entries instead so explicit JSON null
                // remains distinguishable from absence.
                T value = input.entries()
                        .filter(entry -> ops.getStringValue(entry.getFirst())
                                .result().filter(name::equals).isPresent())
                        .map(Pair::getSecond)
                        .findFirst()
                        .orElse(null);
                if (value == null) {
                    return DataResult.success(DEFAULT);
                }
                return CODEC.decode(ops, value).map(Pair::getFirst);
            }

            @Override
            public <T> RecordBuilder<T> encode(StatusEffectDuration value, DynamicOps<T> ops,
                                               RecordBuilder<T> prefix) {
                if (value.equals(DEFAULT)) {
                    return prefix;
                }
                return prefix.add(name, CODEC.encode(value, ops, ops.empty()));
            }
        };
    }

    private static <T> boolean isNull(DynamicOps<T> ops, T input) {
        return ops.convertTo(com.mojang.serialization.JsonOps.INSTANCE, input).isJsonNull();
    }

    public StatusEffectDuration {
        if (isPermanent) {
            if (finiteSeconds != 0.0f) {
                throw new IllegalArgumentException("permanent duration must not have finite seconds");
            }
        } else if (!Float.isFinite(finiteSeconds) || finiteSeconds < 0.0f) {
            throw new IllegalArgumentException("duration must be finite and nonnegative");
        }
    }

    public static StatusEffectDuration finite(float seconds) {
        return new StatusEffectDuration(false, seconds);
    }

    public static StatusEffectDuration permanent() {
        return new StatusEffectDuration(true, 0.0f);
    }

    /** Returns the finite duration, or fails rather than inventing a value for permanent. */
    public float requireFiniteSeconds() {
        if (isPermanent) {
            throw new IllegalStateException("permanent duration has no finite seconds");
        }
        return finiteSeconds;
    }
}
