package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Marker for entities whose observer harness is non-corporeal. */
public record IncorporealComponent() implements CharacterComponent {
    public static final String TYPE = "Incorporeal";
    public static final Codec<IncorporealComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(IncorporealComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new IncorporealComponent();
    }));
    @Override public String type() { return TYPE; }
}
