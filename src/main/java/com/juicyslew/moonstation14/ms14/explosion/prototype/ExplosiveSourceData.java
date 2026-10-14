package com.juicyslew.moonstation14.ms14.explosion.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Immutable source-side intensity proposal, separate from explosion behavior. */
public record ExplosiveSourceData(double totalIntensity, double intensitySlope, double maxIntensity,
                                 ResourceLocation explosionType) {
    private static final Set<String> FIELDS = Set.of("total_intensity", "intensity_slope", "max_intensity", "explosion_type");
    private static final Codec<ExplosiveSourceData> STRUCTURAL = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("total_intensity").forGetter(ExplosiveSourceData::totalIntensity),
            Codec.DOUBLE.fieldOf("intensity_slope").forGetter(ExplosiveSourceData::intensitySlope),
            Codec.DOUBLE.fieldOf("max_intensity").forGetter(ExplosiveSourceData::maxIntensity),
            ResourceLocation.CODEC.fieldOf("explosion_type").forGetter(ExplosiveSourceData::explosionType)
    ).apply(i, ExplosiveSourceData::new));
    public static final Codec<ExplosiveSourceData> CODEC = Codec.of(STRUCTURAL, new Decoder<>() {
        @Override public <T> DataResult<com.mojang.datafixers.util.Pair<ExplosiveSourceData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement element = input instanceof JsonElement json ? json : ops.convertTo(JsonOps.INSTANCE, input);
                if (!element.isJsonObject()) return DataResult.error(() -> "explosive_source must be an object");
                JsonObject object = element.getAsJsonObject();
                for (String key : object.keySet()) if (!FIELDS.contains(key)) return DataResult.error(() -> "unknown explosive_source field '" + key + "'");
                for (String key : FIELDS) if (!object.has(key) || object.get(key).isJsonNull()) return DataResult.error(() -> "missing or null explosive_source field '" + key + "'");
                for (String key : Set.of("total_intensity", "intensity_slope", "max_intensity")) {
                    JsonElement number = object.get(key);
                    if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) return DataResult.error(() -> key + " must be a finite positive number");
                    double value = number.getAsDouble();
                    if (!Double.isFinite(value) || value <= 0)
                        return DataResult.error(() -> key + " must be a finite positive number");
                }
                return STRUCTURAL.decode(ops, input).flatMap(pair -> {
                    try { pair.getFirst().proposal(); return DataResult.success(pair); }
                    catch (RuntimeException ex) { return DataResult.error(() -> ex.getMessage()); }
                });
            } catch (RuntimeException ex) { return DataResult.error(() -> ex.getMessage() == null ? "invalid explosive_source" : ex.getMessage()); }
        }
    });

    public ExplosiveSourceData {
        if (explosionType == null) throw new IllegalArgumentException("explosion_type cannot be null");
        new BlastPlanner.Proposal(totalIntensity, intensitySlope, maxIntensity);
    }

    public BlastPlanner.Proposal proposal() {
        return new BlastPlanner.Proposal(totalIntensity, intensitySlope, maxIntensity);
    }
}

