package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Mutable provider-owned runtime view; deliberately has no timer or persistence. */
public final class SlidingAttachment implements IMS14Attachment<SlidingAttachment, SlidingComponent> {
    private boolean sliding;

    public SlidingAttachment() { }
    public SlidingAttachment(SlidingComponent component) { sliding = component.sliding(); }
    public boolean sliding() { return sliding; }
    public void setSliding(boolean sliding) { this.sliding = sliding; }

    @Override public SlidingComponent toComponent() { return new SlidingComponent(sliding); }
    public static final Codec<SlidingAttachment> CODEC = SlidingComponent.CODEC
            .xmap(SlidingAttachment::new, SlidingAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, SlidingAttachment> STREAM_CODEC =
            SlidingComponent.STREAM_CODEC.map(SlidingAttachment::new, SlidingAttachment::toComponent);
    @Override public Codec<SlidingAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, SlidingAttachment> getStreamCodec() { return STREAM_CODEC; }
}
