package com.juicyslew.moonstation14.ms14.fire;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable persisted/networked fire state owned by one living entity. */
public record FireStackComponent(float stacks, boolean ignited)
        implements IMS14Component<FireStackComponent, FireStackAttachment> {
    public static final float MIN_STACKS = -10f;
    public static final float MAX_STACKS = 10f;
    public static final FireStackComponent EMPTY = new FireStackComponent(0f, false);

    public FireStackComponent {
        if (!Float.isFinite(stacks) || stacks < MIN_STACKS || stacks > MAX_STACKS) {
            throw new IllegalArgumentException("fire stacks must be finite and in [-10, 10]");
        }
        if (stacks == 0f) {
            stacks = 0f;
        }
        if (ignited && stacks <= 0f) {
            throw new IllegalArgumentException("ignited fire state requires positive stacks");
        }
    }

    public FireStackComponent() {
        this(0f, false);
    }

    public boolean isEmpty() {
        return stacks == 0f && !ignited;
    }

    @Override
    public FireStackAttachment toAttachment() {
        return new FireStackAttachment(this);
    }

    private static final Codec<Float> STACKS_CODEC = Codec.FLOAT.comapFlatMap(
            value -> Float.isFinite(value) && value >= MIN_STACKS && value <= MAX_STACKS
                    ? DataResult.success(value)
                    : DataResult.error(() -> "fire stacks must be finite and in [-10, 10]"),
            value -> value);

    public static final Codec<FireStackComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STACKS_CODEC.fieldOf("firestacks").forGetter(FireStackComponent::stacks),
            Codec.BOOL.fieldOf("ignited").forGetter(FireStackComponent::ignited)
    ).apply(instance, FireStackComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FireStackComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, FireStackComponent::stacks,
                    ByteBufCodecs.BOOL, FireStackComponent::ignited,
                    FireStackComponent::new);

    @Override
    public Codec<FireStackComponent> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, FireStackComponent> getStreamCodec() {
        return STREAM_CODEC;
    }
}
