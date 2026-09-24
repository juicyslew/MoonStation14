package com.juicyslew.moonstation14.ms14.hunger;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Immutable authoritative character hunger, independent of vanilla FoodData. */
public record HungerComponent(float hunger) implements IMS14Component<HungerComponent, HungerAttachment> {
    public static final float DEFAULT_HUNGER = 150f;
    public static final HungerComponent DEFAULT = new HungerComponent(DEFAULT_HUNGER);
    private static final Codec<Float> VALUE = Codec.FLOAT.comapFlatMap(value -> valid(value)
            ? DataResult.success(value) : DataResult.error(() -> "hunger must be finite and in [0, 200]"), value -> value);
    public static final Codec<HungerComponent> CODEC = VALUE.fieldOf("hunger").codec()
            .xmap(HungerComponent::new, HungerComponent::hunger);
    public static final StreamCodec<RegistryFriendlyByteBuf, HungerComponent> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeFloat(value.hunger), buffer -> new HungerComponent(buffer.readFloat()));

    public HungerComponent {
        if (!valid(hunger)) throw new IllegalArgumentException("hunger must be finite and in [0, 200]");
    }
    private static boolean valid(float value) { return Float.isFinite(value) && value >= 0f && value <= 200f; }
    public boolean isDefault() { return Float.compare(hunger, DEFAULT_HUNGER) == 0; }
    @Override public HungerAttachment toAttachment() { return new HungerAttachment(this); }
    @Override public Codec<HungerComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, HungerComponent> getStreamCodec() { return STREAM_CODEC; }
}
