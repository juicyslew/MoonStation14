package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Supported local tank composition: physical gas volume/temperature and initial finite gas. */
public record GasTankData(double volumeCubicMeters, double temperatureKelvin, Map<GasType, Double> initialMoles) {
    private static final Codec<Map<String, Double>> MOLES = GasCompositionCodec.CODEC;
    public static final Codec<GasTankData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("volumeCubicMeters").forGetter(GasTankData::volumeCubicMeters),
            Codec.DOUBLE.fieldOf("temperatureKelvin").forGetter(GasTankData::temperatureKelvin),
            MOLES.optionalFieldOf("initialMoles", Map.of()).forGetter(GasTankData::encodeMoles)
    ).apply(i, (volume, temperature, moles) -> new GasTankData(volume, temperature, decodeMoles(moles))));

    public GasTankData {
        if (!Double.isFinite(volumeCubicMeters) || volumeCubicMeters <= 0 || !Double.isFinite(temperatureKelvin)
                || temperatureKelvin < GasMixture.MIN_TEMPERATURE_KELVIN || temperatureKelvin > GasMixture.MAX_TEMPERATURE_KELVIN)
            throw new IllegalArgumentException("Invalid gas tank volume or temperature");
        initialMoles = Map.copyOf(initialMoles);
        new GasMixture(initialMoles, temperatureKelvin, volumeCubicMeters);
    }

    public GasMixture initialMixture() { return new GasMixture(initialMoles, temperatureKelvin, volumeCubicMeters); }
    private Map<String, Double> encodeMoles() { return GasCompositionCodec.encode(initialMoles); }
    private static Map<String, Double> encodeMoles(Map<GasType, Double> values) {
        var result = new java.util.LinkedHashMap<String, Double>(); values.forEach((gas, amount) -> result.put(gas.id(), amount)); return result;
    }
    private static Map<GasType, Double> decodeMoles(Map<String, Double> values) {
        return GasCompositionCodec.decode(values);
    }
}

