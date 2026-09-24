package com.juicyslew.moonstation14.ms14.status_effect;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Set;

/**
 * Immutable runtime state for one status effect.
 *
 * <p>Application-specific state is carried by the closed
 * {@link StatusEffectPayload} hierarchy rather than an arbitrary data map.</p>
 */
public record StatusEffectInstance(
        int delayTicks,
        OptionalInt remainingDurationTicks,
        StatusEffectState state,
        StatusEffectPayload payload
) {
    /** Compatibility constructor for statuses without application-specific data. */
    public StatusEffectInstance(int delayTicks, OptionalInt remainingDurationTicks, StatusEffectState state) {
        this(delayTicks, remainingDurationTicks, state, StatusEffectPayload.none());
    }

    public StatusEffectInstance {
        Objects.requireNonNull(remainingDurationTicks, "remainingDurationTicks");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(payload, "payload");
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks must be nonnegative");
        }
        if (remainingDurationTicks.isPresent() && remainingDurationTicks.getAsInt() <= 0) {
            throw new IllegalArgumentException("finite duration must be positive");
        }
        StatusEffectState expectedState = delayTicks == 0
                ? StatusEffectState.ACTIVE
                : StatusEffectState.PENDING;
        if (state != expectedState) {
            throw new IllegalArgumentException("state must match delayTicks");
        }
    }

    private static final Codec<Integer> STRICT_INTEGER_CODEC = Codec.of(
            Codec.INT,
            StatusEffectInstance::decodeStrictInteger
    );

    private record EncodedFields(int delayTicks, Optional<Integer> remainingTicks,
                                 StatusEffectState state, Optional<StatusEffectPayload> payload) {
    }

    private static final Codec<EncodedFields> ENCODED_FIELDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STRICT_INTEGER_CODEC.fieldOf("delay_ticks").forGetter(EncodedFields::delayTicks),
            STRICT_INTEGER_CODEC.optionalFieldOf("remaining_ticks").forGetter(EncodedFields::remainingTicks),
            StatusEffectState.CODEC.fieldOf("state").forGetter(EncodedFields::state),
            StatusEffectPayload.CODEC.optionalFieldOf("payload").forGetter(EncodedFields::payload)
    ).apply(instance, EncodedFields::new));

    private static final Set<String> PERSISTENT_FIELDS = Set.of("delay_ticks", "remaining_ticks", "state", "payload");

    private static final Decoder<EncodedFields> STRICT_FIELDS_DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<EncodedFields, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "status effect instance must be a JSON object");
                }
                for (String field : json.getAsJsonObject().keySet()) {
                    if (!PERSISTENT_FIELDS.contains(field)) {
                        return DataResult.error(() -> "unknown status effect instance field '" + field + "'");
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

    private static final Codec<EncodedFields> STRICT_ENCODED_FIELDS_CODEC =
            Codec.of(ENCODED_FIELDS_CODEC, STRICT_FIELDS_DECODER);

    /** Strict persistent representation; absence of remaining_ticks means permanent. */
    public static final Codec<StatusEffectInstance> CODEC = STRICT_ENCODED_FIELDS_CODEC.flatXmap(
            fields -> {
                try {
                    return DataResult.success(new StatusEffectInstance(
                            fields.delayTicks(),
                            fields.remainingTicks().map(OptionalInt::of).orElseGet(OptionalInt::empty),
                            fields.state(),
                            fields.payload().orElseGet(StatusEffectPayload::none)));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(exception::getMessage);
                }
            },
            value -> DataResult.success(new EncodedFields(
                    value.delayTicks(),
                    remainingTicks(value),
                    value.state(),
                    encodedPayload(value)))
    );

    private static Optional<StatusEffectPayload> encodedPayload(StatusEffectInstance value) {
        return value.payload().isNone() ? Optional.empty() : Optional.of(value.payload());
    }

    private static Optional<Integer> remainingTicks(StatusEffectInstance value) {
        return value.remainingDurationTicks().isPresent()
                ? Optional.of(value.remainingDurationTicks().getAsInt())
                : Optional.empty();
    }

    private static <T> DataResult<Pair<Integer, T>> decodeStrictInteger(DynamicOps<T> ops, T input) {
        return ops.getNumberValue(input).flatMap(number -> {
            String spelling = number.toString();
            if (!spelling.matches("-?(0|[1-9][0-9]*)")) {
                return DataResult.error(() -> "Expected an integer, got " + spelling);
            }
            try {
                return DataResult.success(Pair.of(Integer.parseInt(spelling), input));
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "Integer is outside the supported range: " + spelling);
            }
        });
    }

    /**
     * Network representation with bounded VarInts and an explicit two-value state enum.
     * The constructor remains the final invariant check for decoded values.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusEffectInstance> STREAM_CODEC =
            StreamCodec.of(StatusEffectInstance::encodeNetwork, StatusEffectInstance::decodeNetwork);

    private static final int STATE_PENDING_ID = 0;
    private static final int STATE_ACTIVE_ID = 1;

    private static void encodeNetwork(RegistryFriendlyByteBuf buffer, StatusEffectInstance value) {
        writeBoundedInt(buffer, value.delayTicks(), 0, Integer.MAX_VALUE, "delay_ticks");
        buffer.writeBoolean(value.remainingDurationTicks().isPresent());
        if (value.remainingDurationTicks().isPresent()) {
            writeBoundedInt(buffer, value.remainingDurationTicks().getAsInt(), 1, Integer.MAX_VALUE,
                    "remaining_ticks");
        }
        writeBoundedInt(buffer, value.state() == StatusEffectState.PENDING ? STATE_PENDING_ID : STATE_ACTIVE_ID,
                STATE_PENDING_ID, STATE_ACTIVE_ID, "state");
        StatusEffectPayload.STREAM_CODEC.encode(buffer, value.payload());
    }

    private static StatusEffectInstance decodeNetwork(RegistryFriendlyByteBuf buffer) {
        int delay = readBoundedInt(buffer, 0, Integer.MAX_VALUE, "delay_ticks");
        OptionalInt remaining = buffer.readBoolean()
                ? OptionalInt.of(readBoundedInt(buffer, 1, Integer.MAX_VALUE, "remaining_ticks"))
                : OptionalInt.empty();
        int stateId = readBoundedInt(buffer, STATE_PENDING_ID, STATE_ACTIVE_ID, "state");
        StatusEffectState state = stateId == STATE_PENDING_ID
                ? StatusEffectState.PENDING
                : StatusEffectState.ACTIVE;
        return new StatusEffectInstance(delay, remaining, state, StatusEffectPayload.STREAM_CODEC.decode(buffer));
    }

    private static void writeBoundedInt(RegistryFriendlyByteBuf buffer, int value, int minimum, int maximum,
                                        String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " is outside its network bounds");
        }
        buffer.writeVarInt(value);
    }

    private static int readBoundedInt(RegistryFriendlyByteBuf buffer, int minimum, int maximum, String field) {
        int value = buffer.readVarInt();
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " is outside its network bounds");
        }
        return value;
    }

    /** Creates a finite instance, active immediately when delay is zero. */
    public static StatusEffectInstance finite(int delayTicks, int durationTicks) {
        return finite(delayTicks, durationTicks, StatusEffectPayload.none());
    }

    /** Creates a finite instance with application-specific payload. */
    public static StatusEffectInstance finite(int delayTicks, int durationTicks, StatusEffectPayload payload) {
        return new StatusEffectInstance(delayTicks, OptionalInt.of(durationTicks), stateFor(delayTicks),
                Objects.requireNonNull(payload, "payload"));
    }

    /** Creates a permanent instance, active immediately when delay is zero. */
    public static StatusEffectInstance permanent(int delayTicks) {
        return permanent(delayTicks, StatusEffectPayload.none());
    }

    /** Creates a permanent instance with application-specific payload. */
    public static StatusEffectInstance permanent(int delayTicks, StatusEffectPayload payload) {
        return new StatusEffectInstance(delayTicks, OptionalInt.empty(), stateFor(delayTicks),
                Objects.requireNonNull(payload, "payload"));
    }

    public static StatusEffectInstance activeFinite(int durationTicks) {
        return finite(0, durationTicks);
    }

    public static StatusEffectInstance activeFinite(int durationTicks, StatusEffectPayload payload) {
        return finite(0, durationTicks, payload);
    }

    public static StatusEffectInstance pendingFinite(int delayTicks, int durationTicks) {
        requirePositiveDelay(delayTicks);
        return finite(delayTicks, durationTicks);
    }

    public static StatusEffectInstance pendingFinite(int delayTicks, int durationTicks,
                                                     StatusEffectPayload payload) {
        requirePositiveDelay(delayTicks);
        return finite(delayTicks, durationTicks, payload);
    }

    public static StatusEffectInstance activePermanent() {
        return permanent(0);
    }

    public static StatusEffectInstance activePermanent(StatusEffectPayload payload) {
        return permanent(0, payload);
    }

    public static StatusEffectInstance pendingPermanent(int delayTicks) {
        requirePositiveDelay(delayTicks);
        return permanent(delayTicks);
    }

    public static StatusEffectInstance pendingPermanent(int delayTicks, StatusEffectPayload payload) {
        requirePositiveDelay(delayTicks);
        return permanent(delayTicks, payload);
    }

    public boolean isPermanent() {
        return remainingDurationTicks.isEmpty();
    }

    public boolean isPending() {
        return state == StatusEffectState.PENDING;
    }

    public boolean isActive() {
        return state == StatusEffectState.ACTIVE;
    }

    /** Advances this instance by exactly one tick. */
    public StatusEffectTickResult tick() {
        if (isPending()) {
            if (delayTicks == 1) {
                StatusEffectInstance activated = isPermanent()
                        ? activePermanent(payload)
                        : activeFinite(remainingDurationTicks.getAsInt(), payload);
                return new StatusEffectTickResult(StatusEffectTransition.ACTIVATED, Optional.of(activated));
            }

            StatusEffectInstance delayed = isPermanent()
                    ? pendingPermanent(delayTicks - 1, payload)
                    : pendingFinite(delayTicks - 1, remainingDurationTicks.getAsInt(), payload);
            return new StatusEffectTickResult(StatusEffectTransition.NONE, Optional.of(delayed));
        }

        if (isPermanent()) {
            return new StatusEffectTickResult(StatusEffectTransition.NONE, Optional.of(this));
        }

        int duration = remainingDurationTicks.getAsInt();
        if (duration == 1) {
            return new StatusEffectTickResult(StatusEffectTransition.EXPIRED, Optional.empty());
        }
        return new StatusEffectTickResult(
                StatusEffectTransition.NONE,
                Optional.of(activeFinite(duration - 1, payload))
        );
    }

    private static StatusEffectState stateFor(int delayTicks) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks must be nonnegative");
        }
        return delayTicks == 0 ? StatusEffectState.ACTIVE : StatusEffectState.PENDING;
    }

    private static void requirePositiveDelay(int delayTicks) {
        if (delayTicks <= 0) {
            throw new IllegalArgumentException("pending delayTicks must be positive");
        }
    }
}
