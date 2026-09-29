package com.juicyslew.moonstation14.ms14.blood;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Persisted bleed accumulator and one-time scalar-save migration marker. */
public record BloodComponent(double bleedRate, boolean initialized) implements IMS14Component<BloodComponent, BloodAttachment> {
    private static final Codec<Double> VALID = Codec.DOUBLE.comapFlatMap(v -> Double.isFinite(v) && v >= 0
            ? DataResult.success(v) : DataResult.error(() -> "bleed rate must be finite and nonnegative"), v -> v);
    public static final Codec<BloodComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            VALID.optionalFieldOf("bleed_rate", 0d).forGetter(BloodComponent::bleedRate),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(BloodComponent::initialized)
    ).apply(i, BloodComponent::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, BloodComponent> STREAM_CODEC = StreamCodec.of(
            (b, s) -> { b.writeDouble(s.bleedRate); b.writeBoolean(s.initialized); },
            b -> new BloodComponent(b.readDouble(), b.readBoolean()));
    public BloodComponent {
        if (!Double.isFinite(bleedRate) || bleedRate < 0) throw new IllegalArgumentException("invalid bleed rate");
    }
    @Override public BloodAttachment toAttachment() { return new BloodAttachment(this); }
    @Override public Codec<BloodComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, BloodComponent> getStreamCodec() { return STREAM_CODEC; }
}
