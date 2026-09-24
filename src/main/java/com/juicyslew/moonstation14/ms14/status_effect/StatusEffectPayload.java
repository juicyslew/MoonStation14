package com.juicyslew.moonstation14.ms14.status_effect;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Closed set of application-specific state carried by a status instance.
 *
 * <p>The absence of a payload is represented by {@link None}.  Payload
 * variants are deliberately declared here instead of being selected by a
 * status resource key, so adding a status cannot silently add an unvalidated
 * data bag to the authoritative attachment.</p>
 */
 public sealed interface StatusEffectPayload permits StatusEffectPayload.None, StatusEffectPayload.Jitter,
         StatusEffectPayload.MovementSpeedModifier {
    None NONE = None.INSTANCE;

    /** The default payload for statuses that have no application-specific state. */
    record None() implements StatusEffectPayload {
        public static final None INSTANCE = new None();
    }

    /** Client-jitter parameters, constrained to the upstream status bounds. */
    record Jitter(float amplitude, float frequency) implements StatusEffectPayload {
        public static final float MIN_AMPLITUDE = 1.0f;
        public static final float MAX_AMPLITUDE = 300.0f;
        public static final float MIN_FREQUENCY = 1.0f;
        public static final float MAX_FREQUENCY = 10.0f;

        public Jitter {
            requireBoundedFinite(amplitude, MIN_AMPLITUDE, MAX_AMPLITUDE, "amplitude");
            requireBoundedFinite(frequency, MIN_FREQUENCY, MAX_FREQUENCY, "frequency");
        }

        /** Convenience overload for callers whose source values are doubles. */
        public Jitter(double amplitude, double frequency) {
            this(requireFloat(amplitude, "amplitude"), requireFloat(frequency, "frequency"));
        }

        private static float requireFloat(double value, String field) {
            if (!Double.isFinite(value) || value < -Float.MAX_VALUE || value > Float.MAX_VALUE) {
                throw new IllegalArgumentException(field + " must be finite and representable as a float");
            }
            return (float) value;
        }

        private static void requireBoundedFinite(float value, float minimum, float maximum, String field) {
            if (!Float.isFinite(value) || value < minimum || value > maximum) {
                throw new IllegalArgumentException(field + " must be finite and in the range "
                        + minimum + ".." + maximum);
            }
        }
    }

    /** The one movement-speed value that can be projected by Minecraft. */
    record MovementSpeedModifier(float multiplier) implements StatusEffectPayload {
        public MovementSpeedModifier {
            if (!Float.isFinite(multiplier) || multiplier < 0f) {
                throw new IllegalArgumentException("movement multiplier must be finite and nonnegative");
            }
        }

        public MovementSpeedModifier(double multiplier) {
            this(requireFloat(multiplier));
        }

        private static float requireFloat(double value) {
            if (!Double.isFinite(value) || value < 0d || value > Float.MAX_VALUE) {
                throw new IllegalArgumentException("movement multiplier must be finite and representable as a float");
            }
            return (float) value;
        }
    }

    Codec<StatusEffectPayload> CODEC = PayloadCodecs.CODEC;
    StreamCodec<RegistryFriendlyByteBuf, StatusEffectPayload> STREAM_CODEC = PayloadCodecs.STREAM_CODEC;

    static None none() {
        return NONE;
    }

    static None noPayload() {
        return NONE;
    }

    static Jitter jitter(float amplitude, float frequency) {
        return new Jitter(amplitude, frequency);
    }

    default boolean isNone() {
        return this instanceof None;
    }

    final class PayloadCodecs {
        private static final int NONE_ID = 0;
        private static final int JITTER_ID = 1;
        private static final int MOVEMENT_SPEED_ID = 2;
        private static final Codec<Float> FINITE_FLOAT = Codec.FLOAT.validate(value ->
                Float.isFinite(value)
                        ? DataResult.success(value)
                        : DataResult.error(() -> "expected a finite number"));
        private static final Codec<EncodedFields> ENCODED_FIELDS_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("type").forGetter(EncodedFields::type),
                        FINITE_FLOAT.optionalFieldOf("amplitude").forGetter(EncodedFields::amplitude),
                        FINITE_FLOAT.optionalFieldOf("frequency").forGetter(EncodedFields::frequency),
                        FINITE_FLOAT.optionalFieldOf("multiplier").forGetter(EncodedFields::multiplier)
                ).apply(instance, EncodedFields::new));
        private static final Set<String> FIELDS = Set.of("type", "amplitude", "frequency", "multiplier");

        private static final Decoder<EncodedFields> STRICT_DECODER = new Decoder<>() {
            @Override
            public <T> DataResult<Pair<EncodedFields, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) {
                        return DataResult.error(() -> "status effect payload must be a JSON object");
                    }
                    for (String field : json.getAsJsonObject().keySet()) {
                        if (!FIELDS.contains(field)) {
                            return DataResult.error(() -> "unknown status effect payload field '" + field + "'");
                        }
                    }
                    return ENCODED_FIELDS_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            }
        };

        private static final Codec<EncodedFields> STRICT_FIELDS_CODEC = Codec.of(ENCODED_FIELDS_CODEC, STRICT_DECODER);

        private static final Codec<StatusEffectPayload> CODEC = STRICT_FIELDS_CODEC.flatXmap(
                PayloadCodecs::decode,
                PayloadCodecs::encode);

        private static final StreamCodec<RegistryFriendlyByteBuf, StatusEffectPayload> STREAM_CODEC =
                StreamCodec.of(PayloadCodecs::encodeNetwork, PayloadCodecs::decodeNetwork);

        private record EncodedFields(String type, Optional<Float> amplitude, Optional<Float> frequency,
                                     Optional<Float> multiplier) {
            private EncodedFields {
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(amplitude, "amplitude");
                Objects.requireNonNull(frequency, "frequency");
                Objects.requireNonNull(multiplier, "multiplier");
            }
        }

        private static DataResult<StatusEffectPayload> decode(EncodedFields fields) {
            return switch (fields.type()) {
                case "none" -> fields.amplitude().isPresent() || fields.frequency().isPresent()
                        || fields.multiplier().isPresent()
                        ? DataResult.error(() -> "none status effect payload cannot contain variant fields")
                        : DataResult.success(StatusEffectPayload.none());
                case "jitter" -> {
                    if (fields.amplitude().isEmpty() || fields.frequency().isEmpty()
                            || fields.multiplier().isPresent()) {
                        yield DataResult.error(() -> "jitter status effect payload requires amplitude and frequency");
                    }
                    try {
                        yield DataResult.success(new Jitter(
                                fields.amplitude().orElseThrow(), fields.frequency().orElseThrow()));
                    } catch (IllegalArgumentException exception) {
                        yield DataResult.error(exception::getMessage);
                    }
                }
                case "movement_speed" -> {
                    if (fields.multiplier().isEmpty()
                            || fields.amplitude().isPresent() || fields.frequency().isPresent()) {
                        yield DataResult.error(() -> "movement_speed status effect payload requires multiplier only");
                    }
                    try {
                        yield DataResult.success(new MovementSpeedModifier(fields.multiplier().orElseThrow()));
                    } catch (IllegalArgumentException exception) {
                        yield DataResult.error(exception::getMessage);
                    }
                }
                default -> DataResult.error(() -> "unknown status effect payload type '" + fields.type() + "'");
            };
        }

        private static DataResult<EncodedFields> encode(StatusEffectPayload payload) {
            Objects.requireNonNull(payload, "payload");
            if (payload instanceof None) {
                return DataResult.success(new EncodedFields("none", Optional.empty(), Optional.empty(), Optional.empty()));
            }
            if (payload instanceof Jitter jitter) {
                return DataResult.success(new EncodedFields("jitter",
                        Optional.of(jitter.amplitude()), Optional.of(jitter.frequency()), Optional.empty()));
            }
            if (payload instanceof MovementSpeedModifier movement) {
                return DataResult.success(new EncodedFields("movement_speed", Optional.empty(), Optional.empty(),
                        Optional.of(movement.multiplier())));
            }
            throw new IllegalStateException("unhandled status effect payload " + payload.getClass());
        }

        private static void encodeNetwork(RegistryFriendlyByteBuf buffer, StatusEffectPayload payload) {
            Objects.requireNonNull(payload, "payload");
            if (payload instanceof None) {
                writeBoundedInt(buffer, NONE_ID);
            } else if (payload instanceof Jitter jitter) {
                writeBoundedInt(buffer, JITTER_ID);
                buffer.writeFloat(jitter.amplitude());
                buffer.writeFloat(jitter.frequency());
            } else if (payload instanceof MovementSpeedModifier movement) {
                writeBoundedInt(buffer, MOVEMENT_SPEED_ID);
                buffer.writeFloat(movement.multiplier());
            } else {
                throw new IllegalArgumentException("unhandled status effect payload " + payload.getClass());
            }
        }

        private static StatusEffectPayload decodeNetwork(RegistryFriendlyByteBuf buffer) {
            int id = readBoundedInt(buffer);
            if (id == NONE_ID) {
                return StatusEffectPayload.none();
            }
            if (id == JITTER_ID) {
                return new Jitter(buffer.readFloat(), buffer.readFloat());
            }
            if (id == MOVEMENT_SPEED_ID) {
                return new MovementSpeedModifier(buffer.readFloat());
            }
            throw new IllegalArgumentException("unknown status effect payload discriminator " + id);
        }

        private static void writeBoundedInt(RegistryFriendlyByteBuf buffer, int value) {
            if (value < NONE_ID || value > MOVEMENT_SPEED_ID) {
                throw new IllegalArgumentException("payload discriminator is outside its network bounds");
            }
            buffer.writeVarInt(value);
        }

        private static int readBoundedInt(RegistryFriendlyByteBuf buffer) {
            int value = buffer.readVarInt();
            if (value < NONE_ID || value > MOVEMENT_SPEED_ID) {
                throw new IllegalArgumentException("payload discriminator is outside its network bounds");
            }
            return value;
        }

        private PayloadCodecs() {
        }
    }
}
