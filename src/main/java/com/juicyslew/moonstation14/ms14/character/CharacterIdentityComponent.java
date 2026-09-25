package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable persisted character identity; policy data remains in the prototype catalog. */
public record CharacterIdentityComponent(ResourceLocation characterId)
        implements IMS14Component<CharacterIdentityComponent, CharacterIdentityAttachment> {
    public CharacterIdentityComponent {
        Objects.requireNonNull(characterId, "characterId");
    }

    public CharacterIdentityAttachment toAttachment() {
        return new CharacterIdentityAttachment(this);
    }

    public static final Codec<CharacterIdentityComponent> CODEC = ResourceLocation.CODEC
            .xmap(CharacterIdentityComponent::new, CharacterIdentityComponent::characterId);
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterIdentityComponent> STREAM_CODEC =
            StreamCodec.of((RegistryFriendlyByteBuf buffer, CharacterIdentityComponent value) ->
                            ByteBufCodecs.STRING_UTF8.encode(buffer, value.characterId().toString()),
                    (RegistryFriendlyByteBuf buffer) -> new CharacterIdentityComponent(
                            ResourceLocation.parse(ByteBufCodecs.STRING_UTF8.decode(buffer))));

    @Override public Codec<CharacterIdentityComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, CharacterIdentityComponent> getStreamCodec() {
        return STREAM_CODEC;
    }
}
