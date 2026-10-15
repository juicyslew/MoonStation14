package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Marker capability permitting a living character body to be pulled. */
public record PullableComponent() implements CharacterComponent {
    public static final String TYPE = "Pullable";
    public static final Codec<PullableComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(PullableComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new PullableComponent();
    }));
    @Override public String type() { return TYPE; }
}

