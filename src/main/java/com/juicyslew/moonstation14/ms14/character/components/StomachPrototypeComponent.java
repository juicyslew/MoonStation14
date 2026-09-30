package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Enrollment marker; stomach capacity remains shared by the local stomach system. */
public record StomachPrototypeComponent() implements CharacterComponent {
    public static final String TYPE = "Stomach";
    public static final Codec<StomachPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(StomachPrototypeComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new StomachPrototypeComponent();
    }));

    @Override public String type() { return TYPE; }
}
