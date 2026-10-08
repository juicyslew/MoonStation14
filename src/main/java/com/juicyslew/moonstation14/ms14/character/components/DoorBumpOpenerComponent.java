package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved opt-in equivalent of the upstream DoorBumpOpener entity tag. */
public record DoorBumpOpenerComponent() implements CharacterComponent {
    public static final String TYPE = "DoorBumpOpener";
    public static final Codec<DoorBumpOpenerComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(DoorBumpOpenerComponent::type)
    ).apply(i, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new DoorBumpOpenerComponent();
    }));
    @Override public String type() { return TYPE; }
}
