package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Mutable runtime wrapper around immutable body-temperature state. */
public final class BodyTemperatureAttachment
        implements IMS14Attachment<BodyTemperatureAttachment, BodyTemperatureComponent> {
    private BodyTemperatureComponent state;

    public BodyTemperatureAttachment() {
        this(BodyTemperatureComponent.DEFAULT);
    }

    public BodyTemperatureAttachment(BodyTemperatureComponent state) {
        this.state = java.util.Objects.requireNonNull(state);
    }

    public double kelvin() {
        return state.kelvin();
    }

    public void setKelvin(double kelvin) {
        state = new BodyTemperatureComponent(kelvin);
    }

    /** Even the normal baseline is meaningful initialized entity state. */
    public boolean isEmpty() {
        return false;
    }

    @Override
    public BodyTemperatureComponent toComponent() {
        return state;
    }

    public static final Codec<BodyTemperatureAttachment> CODEC = BodyTemperatureComponent.CODEC
            .xmap(BodyTemperatureAttachment::new, BodyTemperatureAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyTemperatureAttachment> STREAM_CODEC =
            BodyTemperatureComponent.STREAM_CODEC.map(BodyTemperatureAttachment::new,
                    BodyTemperatureAttachment::toComponent);

    @Override
    public Codec<BodyTemperatureAttachment> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BodyTemperatureAttachment> getStreamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BodyTemperatureAttachment attachment && state.equals(attachment.state);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }
}
