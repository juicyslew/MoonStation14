package com.juicyslew.moonstation14.ms14.fire;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/** Immutable entity attachment view of the authoritative fire-stack state. */
public final class FireStackAttachment implements IMS14Attachment<FireStackAttachment, FireStackComponent> {
    private final FireStackComponent state;

    public FireStackAttachment(FireStackComponent state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public FireStackAttachment() {
        this(FireStackComponent.EMPTY);
    }

    public float stacks() {
        return state.stacks();
    }

    public boolean ignited() {
        return state.ignited();
    }

    public boolean isEmpty() {
        return state.isEmpty();
    }

    @Override
    public FireStackComponent toComponent() {
        return state;
    }

    public static final Codec<FireStackAttachment> CODEC = FireStackComponent.CODEC.xmap(
            FireStackAttachment::new, FireStackAttachment::toComponent);

    public static final StreamCodec<RegistryFriendlyByteBuf, FireStackAttachment> STREAM_CODEC =
            FireStackComponent.STREAM_CODEC.map(FireStackAttachment::new, FireStackAttachment::toComponent);

    @Override
    public Codec<FireStackAttachment> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, FireStackAttachment> getStreamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof FireStackAttachment other && state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }
}
