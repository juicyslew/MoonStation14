package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Target eligibility marker; eye damage remains mutable attachment state. */
public record BlindablePrototypeComponent() implements CharacterComponent {
    public static final String TYPE = "Blindable";
    public static final Codec<BlindablePrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(BlindablePrototypeComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new BlindablePrototypeComponent();
    }));

    @Override public String type() { return TYPE; }
}
