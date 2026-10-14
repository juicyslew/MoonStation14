package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, persistent finite gas inventory owned by one portable tank stack. */
public record GasTankContents(ResourceLocation prototypeId, GasMixture mixture) {
    private static final Codec<Map<String, Double>> MOLES = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);
    public static final Codec<GasTankContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("prototype").forGetter(GasTankContents::prototypeId),
            MOLES.fieldOf("moles").forGetter(value -> encodeMoles(value.mixture())),
            Codec.DOUBLE.fieldOf("temperature").forGetter(value -> value.mixture().temperatureKelvin()),
            Codec.DOUBLE.fieldOf("volume").forGetter(value -> value.mixture().volumeCubicMeters())
    ).apply(instance, GasTankContents::decode));
    public static final StreamCodec<RegistryFriendlyByteBuf, GasTankContents> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { ResourceLocation.STREAM_CODEC.encode(buffer, value.prototypeId); buffer.writeMap(encodeMoles(value.mixture), (b, gas) -> b.writeUtf(gas), (b, amount) -> b.writeDouble(amount)); buffer.writeDouble(value.mixture.temperatureKelvin()); buffer.writeDouble(value.mixture.volumeCubicMeters()); },
            buffer -> { ResourceLocation profile = ResourceLocation.STREAM_CODEC.decode(buffer); Map<GasType, Double> moles = new EnumMap<>(GasType.class); int count = buffer.readVarInt(); for (int i = 0; i < count; i++) moles.put(GasType.fromId(buffer.readUtf()), buffer.readDouble()); return new GasTankContents(profile, new GasMixture(moles, buffer.readDouble(), buffer.readDouble())); });

    public GasTankContents {
        Objects.requireNonNull(prototypeId, "prototypeId");
        Objects.requireNonNull(mixture, "mixture");
        
    }

    public GasTankContents withMixture(GasMixture next) {
        Objects.requireNonNull(next, "next");
        if (Math.abs(next.volumeCubicMeters() - mixture.volumeCubicMeters()) > 1e-9)
            throw new IllegalArgumentException("Gas content mutation cannot change tank volume");
        return new GasTankContents(prototypeId, next);
    }

    private static Map<String, Double> encodeMoles(GasMixture mixture) {
        var values = new java.util.LinkedHashMap<String, Double>();
        mixture.gasMoles().forEach((gas, amount) -> values.put(gas.id(), amount));
        return values;
    }
    private static GasTankContents decode(ResourceLocation profile, Map<String, Double> values, double temperature, double volume) {
        Map<GasType, Double> moles = new EnumMap<>(GasType.class);
        values.forEach((gas, amount) -> moles.put(GasType.fromId(gas), amount));
        return new GasTankContents(profile, new GasMixture(moles, temperature, volume));
    }
}

