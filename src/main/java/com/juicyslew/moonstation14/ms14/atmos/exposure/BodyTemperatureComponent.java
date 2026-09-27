package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Immutable entity-owned body temperature, expressed in kelvin. */
public record BodyTemperatureComponent(double kelvin)
        implements IMS14Component<BodyTemperatureComponent, BodyTemperatureAttachment> {
    public static final double DEFAULT_KELVIN = 310.15;
    public static final BodyTemperatureComponent DEFAULT = new BodyTemperatureComponent(DEFAULT_KELVIN);
    private static final Codec<Double> VALUE = Codec.DOUBLE.comapFlatMap(value -> valid(value)
            ? DataResult.success(value) : DataResult.error(() -> "body temperature must be finite and in [2.7, 20000]"),
            value -> value);
    public static final Codec<BodyTemperatureComponent> CODEC = VALUE.fieldOf("kelvin").codec()
            .xmap(BodyTemperatureComponent::new, BodyTemperatureComponent::kelvin);
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyTemperatureComponent> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeDouble(value.kelvin),
            buffer -> new BodyTemperatureComponent(buffer.readDouble()));

    public BodyTemperatureComponent {
        if (!valid(kelvin)) {
            throw new IllegalArgumentException("body temperature must be finite and in [2.7, 20000]");
        }
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 2.7 && value <= 20000;
    }

    public boolean isDefault() {
        return Double.compare(kelvin, DEFAULT_KELVIN) == 0;
    }

    @Override
    public BodyTemperatureAttachment toAttachment() {
        return new BodyTemperatureAttachment(this);
    }

    @Override
    public Codec<BodyTemperatureComponent> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BodyTemperatureComponent> getStreamCodec() {
        return STREAM_CODEC;
    }
}
