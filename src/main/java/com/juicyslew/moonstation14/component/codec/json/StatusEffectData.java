package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical, data-only definition of a status effect.
 *
 * <p>The prototype ID is deliberately not part of this value.  It is the
 * key in the status-effect prototype catalog, just as it is for the other
 * custom prototype types.</p>
 */
public record StatusEffectData(
        String translationKey,
        boolean isBeneficial,
        int color,
        List<StatusEffectBehavior> behaviors,
        List<StatusEffectEligibility> eligibility,
        Optional<Float> movementSpeedMultiplier
) {
    private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(
            StatusEffectData::decodeColor,
            value -> "0x" + Integer.toHexString(value));

    private static final Codec<StatusEffectData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("translation_key").forGetter(StatusEffectData::translationKey),
                    Codec.BOOL.optionalFieldOf("beneficial", false).forGetter(StatusEffectData::isBeneficial),
                    COLOR_CODEC.fieldOf("color").forGetter(StatusEffectData::color),
                    Codec.list(StatusEffectBehavior.CODEC).fieldOf("behaviors").forGetter(StatusEffectData::behaviors),
                    Codec.list(StatusEffectEligibility.CODEC).fieldOf("eligibility").forGetter(StatusEffectData::eligibility),
                    finiteNonnegativeFloat().optionalFieldOf("movement_speed_multiplier")
                            .forGetter(StatusEffectData::movementSpeedMultiplier)
            ).apply(instance, StatusEffectData::new));

    private static final Decoder<StatusEffectData> STRICT_DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<StatusEffectData, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) {
                        return DataResult.error(() -> "status effect must be a JSON object");
                    }
                    StatusEffectSchemaAudit.audit(json.getAsJsonObject());
                    return STRUCTURAL_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
        }
    };

    /** Codec with strict canonical object-field auditing on both JSON and wire-like ops. */
    public static final Codec<StatusEffectData> CODEC = Codec.of(STRUCTURAL_CODEC, STRICT_DECODER);

    /** Source-compatible constructor for status definitions without movement behavior. */
    public StatusEffectData(String translationKey, boolean isBeneficial, int color,
                            List<StatusEffectBehavior> behaviors,
                            List<StatusEffectEligibility> eligibility) {
        this(translationKey, isBeneficial, color, behaviors, eligibility, Optional.empty());
    }

    public StatusEffectData {
        translationKey = requireNonEmpty(translationKey, "translationKey");
        Objects.requireNonNull(behaviors, "behaviors");
        Objects.requireNonNull(eligibility, "eligibility");
        Objects.requireNonNull(movementSpeedMultiplier, "movementSpeedMultiplier");
        behaviors = copyDistinct(behaviors, "behaviors");
        eligibility = copyDistinct(eligibility, "eligibility");
        if (eligibility.isEmpty()) {
            throw new IllegalArgumentException("eligibility must not be empty");
        }
        boolean movement = behaviors.contains(StatusEffectBehavior.MOVEMENT_SPEED);
        if (movement != movementSpeedMultiplier.isPresent()) {
            throw new IllegalArgumentException(movement
                    ? "movement_speed behavior requires movementSpeedMultiplier"
                    : "movementSpeedMultiplier is only valid with movement_speed behavior");
        }
        movementSpeedMultiplier.ifPresent(value -> {
            if (!Float.isFinite(value) || value < 0f) {
                throw new IllegalArgumentException("movementSpeedMultiplier must be finite and nonnegative");
            }
        });
    }

    private static Codec<Float> finiteNonnegativeFloat() {
        return Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0f
                ? DataResult.success(value)
                : DataResult.error(() -> "movement_speed_multiplier must be finite and nonnegative"));
    }

    private static <T> List<T> copyDistinct(List<T> values, String field) {
        List<T> copy = List.copyOf(values);
        Set<T> distinct = new HashSet<>(copy);
        if (distinct.size() != copy.size()) {
            throw new IllegalArgumentException(field + " must not contain duplicates");
        }
        return List.copyOf(copy);
    }

    private static String requireNonEmpty(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }

    private static DataResult<Integer> decodeColor(String value) {
        if (value == null || !value.matches("0x[0-9a-fA-F]{1,8}")) {
            return DataResult.error(() -> "color must be a hexadecimal string beginning with 0x");
        }
        try {
            return DataResult.success((int) Long.parseLong(value.substring(2), 16));
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "color is outside the 32-bit hexadecimal range");
        }
    }
}
