package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Enrollment marker; hunger tuning remains shared by the local hunger system. */
public record HungerPrototypeComponent() implements CharacterComponent {
    public static final String TYPE = "Hunger";
    public static final Codec<HungerPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(HungerPrototypeComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new HungerPrototypeComponent();
    }));

    @Override public String type() { return TYPE; }
}
