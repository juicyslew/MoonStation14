package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable network projection of the volatile sliding state. */
public record SlidingComponent(boolean sliding)
        implements IMS14Component<SlidingComponent, SlidingAttachment> {
    @Override public SlidingAttachment toAttachment() { return new SlidingAttachment(this); }

    public static final Codec<SlidingComponent> CODEC = Codec.BOOL.xmap(SlidingComponent::new,
            SlidingComponent::sliding);
    public static final StreamCodec<RegistryFriendlyByteBuf, SlidingComponent> STREAM_CODEC =
            StreamCodec.of((RegistryFriendlyByteBuf buffer, SlidingComponent value) ->
                            ByteBufCodecs.BOOL.encode(buffer, value.sliding()),
                    (RegistryFriendlyByteBuf buffer) -> new SlidingComponent(ByteBufCodecs.BOOL.decode(buffer)));

    @Override public Codec<SlidingComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, SlidingComponent> getStreamCodec() { return STREAM_CODEC; }
}
