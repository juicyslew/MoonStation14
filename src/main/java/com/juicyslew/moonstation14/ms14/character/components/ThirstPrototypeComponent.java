package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Enrollment marker; thirst tuning remains shared by the local thirst system. */
public record ThirstPrototypeComponent() implements CharacterComponent {
    public static final String TYPE = "Thirst";
    public static final Codec<ThirstPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ThirstPrototypeComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new ThirstPrototypeComponent();
    }));

    @Override public String type() { return TYPE; }
}
