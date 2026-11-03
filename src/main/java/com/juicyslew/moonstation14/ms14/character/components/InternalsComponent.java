package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Typed composition marker for bodies whose resolved mob composition supports internals. */
public record InternalsComponent() implements CharacterComponent {
    public static final String TYPE = "Internals";
    public static final Codec<InternalsComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(InternalsComponent::type)
    ).apply(i, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new InternalsComponent();
    }));
    @Override public String type() { return TYPE; }
}
