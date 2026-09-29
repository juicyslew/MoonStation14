package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Objects;

/** Persisted state wrapper for body-owned hands; never stores or copies Minecraft ItemStacks. */
public final class HandAttachment implements IMS14Attachment<HandAttachment, HandComponent> {
    private final HandComponent state;

    public HandAttachment() {
        this(HandComponent.from(HandState.create(List.of("left", "right"))));
    }

    public HandAttachment(HandComponent state) { this.state = Objects.requireNonNull(state, "state"); }

    public HandComponent state() { return state; }
    public boolean isEmpty() { return false; }

    @Override public HandComponent toComponent() { return state; }
    public static final Codec<HandAttachment> CODEC = HandComponent.CODEC.xmap(HandAttachment::new, HandAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, HandAttachment> STREAM_CODEC =
            HandComponent.STREAM_CODEC.map(HandAttachment::new, HandAttachment::toComponent);
    @Override public Codec<HandAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, HandAttachment> getStreamCodec() { return STREAM_CODEC; }

    @Override public boolean equals(Object other) {
        return other instanceof HandAttachment attachment && state.equals(attachment.state);
    }
    @Override public int hashCode() { return state.hashCode(); }
}
