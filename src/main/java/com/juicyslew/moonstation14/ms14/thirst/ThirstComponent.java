package com.juicyslew.moonstation14.ms14.thirst;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Immutable, persisted character thirst state. Presence means initialized, including at the default value. */
public record ThirstComponent(float thirst) implements IMS14Component<ThirstComponent, ThirstAttachment> {
    public static final float MIN_THIRST = 0f;
    public static final float MAX_THIRST = 600f;
    public static final ThirstComponent DEFAULT = new ThirstComponent(MAX_THIRST);

    private static final Codec<Float> VALUE = Codec.FLOAT.comapFlatMap(value -> valid(value)
            ? DataResult.success(value)
            : DataResult.error(() -> "thirst must be finite and in [0, 600]"), value -> value);
    public static final Codec<ThirstComponent> CODEC = VALUE.fieldOf("thirst").codec()
            .xmap(ThirstComponent::new, ThirstComponent::thirst);
    public static final StreamCodec<RegistryFriendlyByteBuf, ThirstComponent> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeFloat(value.thirst), buffer -> new ThirstComponent(buffer.readFloat()));

    public ThirstComponent {
        if (!valid(thirst)) throw new IllegalArgumentException("thirst must be finite and in [0, 600]");
    }

    private static boolean valid(float value) {
        return Float.isFinite(value) && value >= MIN_THIRST && value <= MAX_THIRST;
    }

    @Override public ThirstAttachment toAttachment() { return new ThirstAttachment(this); }
    @Override public Codec<ThirstComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, ThirstComponent> getStreamCodec() { return STREAM_CODEC; }
}
