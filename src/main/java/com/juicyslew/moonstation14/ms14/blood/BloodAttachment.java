package com.juicyslew.moonstation14.ms14.blood;

import com.mojang.serialization.Codec;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Mutable wrapper for bleed metadata only; circulating volume belongs to BLOODSTREAM. */
public final class BloodAttachment implements IMS14Attachment<BloodAttachment, BloodComponent> {
    private BloodComponent state;

    public BloodAttachment() { this(new BloodComponent(0.0, false)); }
    public BloodAttachment(BloodComponent state) { this.state = java.util.Objects.requireNonNull(state); }

    public double bleedRate() { return state.bleedRate(); }
    public boolean initialized() { return state.initialized(); }
    public void set(double bleedRate, boolean initialized) { state = new BloodComponent(bleedRate, initialized); }
    public boolean isEmpty() { return false; }

    @Override public BloodComponent toComponent() { return state; }
    public static final Codec<BloodAttachment> CODEC = BloodComponent.CODEC.xmap(BloodAttachment::new, BloodAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, BloodAttachment> STREAM_CODEC =
            BloodComponent.STREAM_CODEC.map(BloodAttachment::new, BloodAttachment::toComponent);
    @Override public Codec<BloodAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, BloodAttachment> getStreamCodec() { return STREAM_CODEC; }
}
