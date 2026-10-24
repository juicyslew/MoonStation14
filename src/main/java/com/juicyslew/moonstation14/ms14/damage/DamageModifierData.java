package com.juicyslew.moonstation14.ms14.damage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Canonical positive-damage modifier subset shared by armor and item resistance. */
public record DamageModifierData(Map<String, Float> coefficients, Map<String, Float> flatReductions) {
    public static final DamageModifierData EMPTY = new DamageModifierData(Map.of(), Map.of());
    public static final Codec<DamageModifierData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("coefficients", Map.of()).forGetter(DamageModifierData::coefficients),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("flat_reductions", Map.of()).forGetter(DamageModifierData::flatReductions)
    ).apply(i, DamageModifierData::new));

    public DamageModifierData {
        coefficients = checked(coefficients);
        flatReductions = checked(flatReductions);
    }

    private static Map<String, Float> checked(Map<String, Float> input) {
        if (input == null) throw new IllegalArgumentException("modifier map required");
        Map<String, Float> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            DamageKeys.requireCanonical(key);
            if (value == null || !Float.isFinite(value) || value < 0f) throw new IllegalArgumentException("damage modifiers must be finite and nonnegative");
            result.put(key, value);
        });
        return Map.copyOf(result);
    }

    public static Map<String, Float> apply(Map<String, Float> raw, DamageModifierData modifier) {
        Map<String, Float> result = new LinkedHashMap<>();
        DamageKeys.validateDelta(raw).forEach((key, value) -> {
            if (value <= 0f) { result.put(key, value); return; }
            if (!modifier.coefficients.containsKey(key) && !modifier.flatReductions.containsKey(key)) { result.put(key, value); return; }
            float adjusted = Math.max(0f, value - modifier.flatReductions.getOrDefault(key, 0f));
            adjusted *= modifier.coefficients.getOrDefault(key, 1f);
            if (!Float.isFinite(adjusted)) throw new IllegalArgumentException("modified damage must be finite");
            float quantized = ((int)(adjusted * 100f + (adjusted > 0f ? 0.00001f : 0f))) / 100f;
            if (quantized != 0f) result.put(key, quantized);
        });
        return Map.copyOf(result);
    }
}
