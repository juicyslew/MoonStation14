package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Data-only definition of an alert. The prototype catalog key is its identity. */
public record AlertData(
        String nameTranslationKey,
        String descriptionTranslationKey,
        int color,
        int order,
        Optional<ResourceLocation> category
) {
    private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(
            AlertData::decodeColor,
            value -> "0x" + Integer.toHexString(value));
    private static final Codec<Integer> ORDER_CODEC = Codec.INT.validate(value -> value >= 0
            ? DataResult.success(value)
            : DataResult.error(() -> "order must be nonnegative"));
    private static final Codec<ResourceLocation> CATEGORY_CODEC = Codec.STRING.comapFlatMap(
            AlertData::decodeCategory,
            ResourceLocation::toString);

    private static final Codec<AlertData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("name_translation_key").forGetter(AlertData::nameTranslationKey),
                    Codec.STRING.fieldOf("description_translation_key").forGetter(AlertData::descriptionTranslationKey),
                    COLOR_CODEC.fieldOf("color").forGetter(AlertData::color),
                    ORDER_CODEC.fieldOf("order").forGetter(AlertData::order),
                    CATEGORY_CODEC.optionalFieldOf("category").forGetter(AlertData::category)
            ).apply(instance, AlertData::new));

    private static final Decoder<AlertData> STRICT_DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<AlertData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "alert must be a JSON object");
                }
                AlertSchemaAudit.audit(json.getAsJsonObject());
                return STRUCTURAL_CODEC.decode(ops, input);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                return DataResult.error(() -> message);
            }
        }
    };

    /** Codec with strict canonical object-field auditing on JSON and wire-like ops. */
    public static final Codec<AlertData> CODEC = Codec.of(STRUCTURAL_CODEC, STRICT_DECODER);

    /** Compatibility constructor for an alert without a category. */
    public AlertData(String nameTranslationKey, String descriptionTranslationKey, int color, int order) {
        this(nameTranslationKey, descriptionTranslationKey, color, order, Optional.empty());
    }

    public AlertData {
        nameTranslationKey = requireNonBlank(nameTranslationKey, "nameTranslationKey");
        descriptionTranslationKey = requireNonBlank(descriptionTranslationKey, "descriptionTranslationKey");
        category = Objects.requireNonNull(category, "category");
        if (order < 0) {
            throw new IllegalArgumentException("order must be nonnegative");
        }
        category.ifPresent(AlertData::requireCanonicalCategory);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
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

    private static DataResult<ResourceLocation> decodeCategory(String value) {
        if (value == null || !value.contains(":")) {
            return DataResult.error(() -> "category must be a fully qualified resource location");
        }
        try {
            ResourceLocation parsed = ResourceLocation.parse(value);
            if (!value.equals(parsed.toString())) {
                return DataResult.error(() -> "category must be a canonical resource location");
            }
            return DataResult.success(parsed);
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "category must be a canonical resource location");
        }
    }

    private static void requireCanonicalCategory(ResourceLocation value) {
        Objects.requireNonNull(value, "category value");
        if (!value.toString().equals(value.toString().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("category must use lowercase canonical spelling");
        }
    }
}
