package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Opt-in capability: this live character may be represented by a non-owning hand key. */
public record PortableBodyComponent(ResourceLocation profileId) implements CharacterComponent {
    public static final String TYPE = "PortableBody";
    public static final Codec<PortableBodyComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(PortableBodyComponent::type),
            ResourceLocation.CODEC.fieldOf("profile").forGetter(PortableBodyComponent::profileId)
    ).apply(i, (type, profile) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new PortableBodyComponent(profile);
    }));
    public PortableBodyComponent { java.util.Objects.requireNonNull(profileId); }
    @Override public String type() { return TYPE; }
}
