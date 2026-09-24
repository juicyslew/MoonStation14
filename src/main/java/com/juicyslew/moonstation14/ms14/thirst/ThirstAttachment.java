package com.juicyslew.moonstation14.ms14.thirst;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Mutable runtime wrapper; thirst remains persisted even when equal to its nominal default. */
public final class ThirstAttachment implements IMS14Attachment<ThirstAttachment, ThirstComponent> {
    private ThirstComponent state;

    public ThirstAttachment() { this(ThirstComponent.DEFAULT); }
    public ThirstAttachment(ThirstComponent state) { this.state = java.util.Objects.requireNonNull(state); }

    public float thirst() { return state.thirst(); }
    public void setThirst(float thirst) { state = new ThirstComponent(thirst); }
    /** Explicitly never empty: an attached default-valued state is initialized and must survive reload. */
    public boolean isEmpty() { return false; }

    @Override public ThirstComponent toComponent() { return state; }
    public static final Codec<ThirstAttachment> CODEC = ThirstComponent.CODEC.xmap(ThirstAttachment::new, ThirstAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, ThirstAttachment> STREAM_CODEC =
            ThirstComponent.STREAM_CODEC.map(ThirstAttachment::new, ThirstAttachment::toComponent);
    @Override public Codec<ThirstAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, ThirstAttachment> getStreamCodec() { return STREAM_CODEC; }

    @Override public boolean equals(Object other) {
        return other instanceof ThirstAttachment attachment && state.equals(attachment.state);
    }
    @Override public int hashCode() { return state.hashCode(); }
}
