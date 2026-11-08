package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Presence marker granting the character's strip-view capability; upstream marker has no timing fields. */
public record StrippingPrototypeComponent() implements CharacterComponent {
    public static final String TYPE = "Stripping";
    public static final Codec<StrippingPrototypeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(StrippingPrototypeComponent::type)
    ).apply(i, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new StrippingPrototypeComponent();
    }));
    @Override public String type() { return TYPE; }
}

