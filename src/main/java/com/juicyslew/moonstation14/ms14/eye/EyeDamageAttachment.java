package com.juicyslew.moonstation14.ms14.eye;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/** Immutable entity-attachment view of authoritative eye damage. */
public final class EyeDamageAttachment implements IMS14Attachment<EyeDamageAttachment, EyeDamageComponent> {
    private final EyeDamageComponent state;

    public EyeDamageAttachment(EyeDamageComponent state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public EyeDamageAttachment() {
        this(EyeDamageComponent.EMPTY);
    }

    public int damage() {
        return state.damage();
    }

    public boolean isBlind() {
        return state.isBlind();
    }

    public boolean isEmpty() {
        return state.isEmpty();
    }

    @Override
    public EyeDamageComponent toComponent() {
        return state;
    }

    public static final Codec<EyeDamageAttachment> CODEC = EyeDamageComponent.CODEC.xmap(
            EyeDamageAttachment::new, EyeDamageAttachment::toComponent);

    public static final StreamCodec<RegistryFriendlyByteBuf, EyeDamageAttachment> STREAM_CODEC =
            EyeDamageComponent.STREAM_CODEC.map(EyeDamageAttachment::new, EyeDamageAttachment::toComponent);

    @Override
    public Codec<EyeDamageAttachment> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, EyeDamageAttachment> getStreamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof EyeDamageAttachment other && state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }
}
