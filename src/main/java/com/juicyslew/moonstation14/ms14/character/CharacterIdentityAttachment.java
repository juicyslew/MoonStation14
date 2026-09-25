package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Mutable provider-owned view of the key-only character identity. */
public final class CharacterIdentityAttachment
        implements IMS14Attachment<CharacterIdentityAttachment, CharacterIdentityComponent> {
    private ResourceLocation characterId;

    public CharacterIdentityAttachment() { }
    public CharacterIdentityAttachment(CharacterIdentityComponent state) { characterId = state.characterId(); }

    public ResourceLocation characterId() { return characterId; }
    public boolean isBound() { return characterId != null; }
    public void bind(ResourceLocation id) {
        if (characterId != null && !characterId.equals(id)) {
            throw new IllegalStateException("Character identity is already bound to " + characterId);
        }
        characterId = java.util.Objects.requireNonNull(id, "id");
    }

    @Override public CharacterIdentityComponent toComponent() {
        if (characterId == null) throw new IllegalStateException("Cannot encode an unbound character identity");
        return new CharacterIdentityComponent(characterId);
    }

    public static final Codec<CharacterIdentityAttachment> CODEC = CharacterIdentityComponent.CODEC
            .xmap(CharacterIdentityAttachment::new, CharacterIdentityAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterIdentityAttachment> STREAM_CODEC =
            CharacterIdentityComponent.STREAM_CODEC.map(CharacterIdentityAttachment::new,
                    CharacterIdentityAttachment::toComponent);

    @Override public Codec<CharacterIdentityAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, CharacterIdentityAttachment> getStreamCodec() {
        return STREAM_CODEC;
    }
}
