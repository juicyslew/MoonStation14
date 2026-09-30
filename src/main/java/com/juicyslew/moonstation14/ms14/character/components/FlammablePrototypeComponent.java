package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Target eligibility marker; fire stacks remain mutable attachment state. */
public record FlammablePrototypeComponent() implements CharacterComponent {
    public static final String TYPE = "Flammable";
    public static final Codec<FlammablePrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(FlammablePrototypeComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new FlammablePrototypeComponent();
    }));

    @Override public String type() { return TYPE; }
}
