package com.juicyslew.moonstation14.ms14.explosion.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.math.BigDecimal;

/** Immutable explosion behavior tuning, separate from explosive source intensity. Values are based on SS14's Default explosion prototype. */
public record ExplosionData(Map<String, Float> damagePerIntensity, List<Float> breakIntensities,
                            List<Float> breakChances, float rerollReduction, Optional<Float> temperatureKelvin,
                            Optional<Integer> fireStacks) {
    private static final Set<String> FIELDS = Set.of("damage_per_intensity", "break_intensities", "break_chances", "reroll_reduction", "temperature_kelvin", "fire_stacks");
    private static final Codec<ExplosionData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("damage_per_intensity").forGetter(ExplosionData::damagePerIntensity),
            Codec.FLOAT.listOf().fieldOf("break_intensities").forGetter(ExplosionData::breakIntensities),
            Codec.FLOAT.listOf().fieldOf("break_chances").forGetter(ExplosionData::breakChances),
            Codec.FLOAT.fieldOf("reroll_reduction").forGetter(ExplosionData::rerollReduction),
            Codec.FLOAT.optionalFieldOf("temperature_kelvin").forGetter(ExplosionData::temperatureKelvin),
            Codec.INT.optionalFieldOf("fire_stacks").forGetter(ExplosionData::fireStacks)
    ).apply(i, ExplosionData::new));

    public static final Codec<ExplosionData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<Pair<ExplosionData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement element = input instanceof JsonElement json ? json : ops.convertTo(JsonOps.INSTANCE, input);
                if (!element.isJsonObject()) return DataResult.error(() -> "explosion must be an object");
                JsonObject object = element.getAsJsonObject();
                for (String key : object.keySet()) if (!FIELDS.contains(key))
                    return DataResult.error(() -> "unknown explosion field '" + key + "'");
                for (String key : Set.of("damage_per_intensity", "break_intensities", "break_chances", "reroll_reduction")) if (!object.has(key) || object.get(key).isJsonNull())
                    return DataResult.error(() -> "missing or null explosion field '" + key + "'");
                if (object.has("temperature_kelvin")) {
                    JsonElement temperature = object.get("temperature_kelvin");
                    if (!temperature.isJsonPrimitive() || !temperature.getAsJsonPrimitive().isNumber())
                        return DataResult.error(() -> "temperature_kelvin must be a finite nonnegative number");
                    float parsed = temperature.getAsFloat();
                    if (!Float.isFinite(parsed) || parsed < 0f)
                        return DataResult.error(() -> "temperature_kelvin must be a finite nonnegative number");
                }
                if (object.has("fire_stacks")) {
                    JsonElement stacks = object.get("fire_stacks");
                    if (!stacks.isJsonPrimitive() || !stacks.getAsJsonPrimitive().isNumber())
                        return DataResult.error(() -> "fire_stacks must be a nonnegative integer");
                    try {
                        int parsed = new BigDecimal(stacks.getAsString()).intValueExact();
                        if (parsed < 0) return DataResult.error(() -> "fire_stacks must be a nonnegative integer");
                    } catch (NumberFormatException | ArithmeticException ex) {
                        return DataResult.error(() -> "fire_stacks must be a nonnegative integer");
                    }
                }
                return STRUCTURAL.decode(ops, input).flatMap(pair -> {
                    try {
                        var value = pair.getFirst();
                        if (value.temperatureKelvin().isPresent() && (!Float.isFinite(value.temperatureKelvin().get()) || value.temperatureKelvin().get() < 0f))
                            return DataResult.error(() -> "temperature_kelvin must be finite and nonnegative");
                        if (value.fireStacks().isPresent() && value.fireStacks().get() < 0)
                            return DataResult.error(() -> "fire_stacks must be nonnegative");
                        return DataResult.success(pair);
                    } catch (RuntimeException ex) {
                        return DataResult.error(() -> ex.getMessage() == null ? "invalid explosion" : ex.getMessage());
                    }
                });
            } catch (RuntimeException ex) {
                return DataResult.error(() -> ex.getMessage() == null ? "invalid explosion" : ex.getMessage());
            }
        }
    });

    public ExplosionData(Map<String, Float> damagePerIntensity, List<Float> breakIntensities,
                         List<Float> breakChances, float rerollReduction) {
        this(damagePerIntensity, breakIntensities, breakChances, rerollReduction, Optional.empty(), Optional.empty());
    }

    public ExplosionData {
        if (damagePerIntensity == null || damagePerIntensity.isEmpty()) throw new IllegalArgumentException("damage_per_intensity must be nonempty");
        for (var entry : damagePerIntensity.entrySet()) {
            if (!DamageKeys.ALL.contains(entry.getKey()) || entry.getValue() == null
                    || !Float.isFinite(entry.getValue()) || entry.getValue() < 0f)
                throw new IllegalArgumentException("invalid explosion damage type or value: " + entry.getKey());
        }
        damagePerIntensity = Map.copyOf(damagePerIntensity);
        breakIntensities = List.copyOf(breakIntensities);
        breakChances = List.copyOf(breakChances);
        if (breakIntensities.size() < 2 || breakIntensities.size() > 16 || breakChances.size() != breakIntensities.size())
            throw new IllegalArgumentException("break curve must have 2-16 aligned points");
        float previousIntensity = -1f, previousChance = -1f;
        for (int index = 0; index < breakIntensities.size(); index++) {
            float intensity = breakIntensities.get(index), chance = breakChances.get(index);
            if (!Float.isFinite(intensity) || intensity < 0f || intensity <= previousIntensity
                    || !Float.isFinite(chance) || chance < 0f || chance > 1f || chance < previousChance)
                throw new IllegalArgumentException("invalid or non-monotonic break curve");
            previousIntensity = intensity;
            previousChance = chance;
        }
        if (!Float.isFinite(rerollReduction) || rerollReduction <= 0f)
            throw new IllegalArgumentException("reroll_reduction must be finite and positive");
        temperatureKelvin = temperatureKelvin == null ? Optional.empty() : temperatureKelvin;
        fireStacks = fireStacks == null ? Optional.empty() : fireStacks;
        if (temperatureKelvin.isPresent() && (!Float.isFinite(temperatureKelvin.get()) || temperatureKelvin.get() < 0f))
            throw new IllegalArgumentException("temperature_kelvin must be finite and nonnegative");
        if (fireStacks.isPresent() && fireStacks.get() < 0)
            throw new IllegalArgumentException("fire_stacks must be nonnegative");
    }
}

