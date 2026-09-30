package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.datafixers.util.Pair;
import java.util.Set;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistence-only reader for pre-BODY saves. Never use as gameplay authority. */
public record LungAttachment(LungComponent component) {
    private static final Codec<Double> NONNEGATIVE = Codec.DOUBLE.comapFlatMap(v ->
            Double.isFinite(v) && v >= 0 ? DataResult.success(v) : DataResult.error(() -> "invalid lung quantity"), v -> v);
    private static final Codec<Double> SATURATION = Codec.DOUBLE.comapFlatMap(v ->
            Double.isFinite(v) && v >= -2 ? DataResult.success(v) : DataResult.error(() -> "invalid saturation"), v -> v);
    private static final Codec<String> GAS_ID = Codec.STRING.comapFlatMap(id -> {
        try {
            GasType.fromId(id);
            return DataResult.success(id);
        } catch (IllegalArgumentException invalid) {
            return DataResult.error(() -> "unknown legacy lung gas: " + id);
        }
    }, id -> id);
    // Exact old field names and optional phase default (older saves predate respiratory phase).
    private static final Codec<LungComponent> COMPONENT = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(GAS_ID, NONNEGATIVE).fieldOf("gas_moles").forGetter(LungComponent::gasMoles),
            NONNEGATIVE.fieldOf("temperature_kelvin").forGetter(LungComponent::temperatureKelvin),
            SATURATION.fieldOf("saturation").forGetter(LungComponent::saturation),
            Codec.BOOL.fieldOf("initialized").forGetter(LungComponent::initialized),
            Codec.STRING.comapFlatMap(v -> {
                try { return DataResult.success(LungComponent.Phase.valueOf(v)); }
                catch (IllegalArgumentException e) { return DataResult.error(() -> "unknown lung phase: " + v); }
            }, LungComponent.Phase::name).optionalFieldOf("phase", LungComponent.Phase.INHALING)
                    .forGetter(LungComponent::phase)
    ).apply(i, LungComponent::new));
    private static final Codec<LungComponent> VALIDATED = Codec.of(COMPONENT, new Decoder<>() {
        @Override public <T> DataResult<Pair<LungComponent, T>> decode(DynamicOps<T> ops, T input) {
            try {
                var json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject() || !json.getAsJsonObject().keySet().containsAll(
                        Set.of("gas_moles", "temperature_kelvin", "saturation", "initialized"))
                        || !Set.of("gas_moles", "temperature_kelvin", "saturation", "initialized", "phase")
                                .containsAll(json.getAsJsonObject().keySet()))
                    return DataResult.error(() -> "ambiguous legacy lung fields");
                return COMPONENT.decode(ops, input);
            }
            catch (RuntimeException invalid) { return DataResult.error(() -> "invalid legacy lung: " + invalid.getMessage()); }
        }
    });
    public static final Codec<LungAttachment> CODEC = VALIDATED.xmap(LungAttachment::new, LungAttachment::component);

    public LungAttachment {
        if (component == null) throw new IllegalArgumentException("component required");
        component.gasMoles().keySet().forEach(GasType::fromId);
    }
}
