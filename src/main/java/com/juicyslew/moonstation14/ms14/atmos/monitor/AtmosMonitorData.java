package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Resolved AtmosMonitor composition: measurement-to-threshold references live in the prototype. */
public record AtmosMonitorData(ResourceLocation pressureThreshold, ResourceLocation temperatureThreshold,
                               Map<GasType, ResourceLocation> gasThresholds) {
    private static final Codec<GasType> GAS = Codec.STRING.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(GasType.fromId(value)); }
        catch (IllegalArgumentException e) { return com.mojang.serialization.DataResult.error(e::getMessage); }
    }, GasType::id);
    public static final Codec<AtmosMonitorData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("pressureThreshold").forGetter(AtmosMonitorData::pressureThreshold),
            ResourceLocation.CODEC.fieldOf("temperatureThreshold").forGetter(AtmosMonitorData::temperatureThreshold),
            Codec.unboundedMap(GAS, ResourceLocation.CODEC).fieldOf("gasThresholds").forGetter(AtmosMonitorData::gasThresholds)
    ).apply(i, AtmosMonitorData::new));
    public AtmosMonitorData {
        if (pressureThreshold == null || temperatureThreshold == null || gasThresholds == null
                || !gasThresholds.keySet().equals(java.util.Set.of(GasType.values())))
            throw new IllegalArgumentException("AtmosMonitor must configure pressure, temperature, and every supported gas");
        gasThresholds = Map.copyOf(gasThresholds);
    }
}
