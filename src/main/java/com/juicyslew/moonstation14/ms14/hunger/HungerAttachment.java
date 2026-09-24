package com.juicyslew.moonstation14.ms14.hunger;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Mutable runtime wrapper around immutable hunger state. */
public final class HungerAttachment implements IMS14Attachment<HungerAttachment, HungerComponent> {
    private HungerComponent state;
    public HungerAttachment() { this(HungerComponent.DEFAULT); }
    public HungerAttachment(HungerComponent state) { this.state = java.util.Objects.requireNonNull(state); }
    public float hunger() { return state.hunger(); }
    public void setHunger(float hunger) { state = new HungerComponent(hunger); }
    /** Default-valued 150 is meaningful initialized state and must survive save/reload. */
    public boolean isEmpty() { return false; }
    @Override public HungerComponent toComponent() { return state; }
    public static final Codec<HungerAttachment> CODEC = HungerComponent.CODEC.xmap(HungerAttachment::new, HungerAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, HungerAttachment> STREAM_CODEC = HungerComponent.STREAM_CODEC.map(HungerAttachment::new, HungerAttachment::toComponent);
    @Override public Codec<HungerAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, HungerAttachment> getStreamCodec() { return STREAM_CODEC; }
    @Override public boolean equals(Object other) {
        return other instanceof HungerAttachment attachment && state.equals(attachment.state);
    }
    @Override public int hashCode() { return state.hashCode(); }
}
