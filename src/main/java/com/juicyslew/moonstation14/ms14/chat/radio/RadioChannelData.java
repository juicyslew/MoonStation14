package com.juicyslew.moonstation14.ms14.chat.radio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/** Display metadata only. The immutable catalog key identifies a channel; this data grants no radio access. */
public record RadioChannelData(String labelTranslationKey, char keycode, int color) {
    private static final Set<String> FIELDS = Set.of("label_translation_key", "keycode", "color");
    private static final Codec<String> KEYCODE_CODEC = Codec.STRING.validate(value ->
            value.matches("[a-z]") ? DataResult.success(value)
                    : DataResult.error(() -> "keycode must be one lowercase ASCII letter"));
    private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(value -> {
        if (!value.matches("0x[0-9a-fA-F]{6}")) {
            return DataResult.error(() -> "color must be an RGB 0xRRGGBB string");
        }
        return DataResult.success(Integer.parseInt(value.substring(2), 16));
    }, value -> String.format(java.util.Locale.ROOT, "0x%06X", value));

    private static final Codec<RadioChannelData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("label_translation_key").forGetter(RadioChannelData::labelTranslationKey),
                    KEYCODE_CODEC.fieldOf("keycode").forGetter(data -> String.valueOf(data.keycode())),
                    COLOR_CODEC.fieldOf("color").forGetter(RadioChannelData::color)
            ).apply(instance, (label, keycode, color) -> new RadioChannelData(label, keycode.charAt(0), color)));

    public static final Codec<RadioChannelData> CODEC = Codec.of(STRUCTURAL_CODEC, new Decoder<>() {
        @Override
        public <T> DataResult<Pair<RadioChannelData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "radio channel must be an object");
                JsonObject object = json.getAsJsonObject();
                for (String field : object.keySet()) {
                    if (!FIELDS.contains(field)) return DataResult.error(() -> "unknown radio channel field '" + field + "'");
                }
                return STRUCTURAL_CODEC.decode(ops, input);
            } catch (RuntimeException exception) {
                return DataResult.error(() -> "invalid radio channel: " + exception.getMessage());
            }
        }
    });

    public RadioChannelData {
        if (labelTranslationKey == null || !labelTranslationKey.matches("[a-z0-9_-]+(?:[.][a-z0-9_-]+)+")
                || labelTranslationKey.length() > 96) {
            throw new IllegalArgumentException("label_translation_key must be a nonempty canonical translation key");
        }
        if (keycode < 'a' || keycode > 'z') {
            throw new IllegalArgumentException("keycode must be one lowercase ASCII letter");
        }
        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("color must be a readable RGB value");
        }
    }
}
