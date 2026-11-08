package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Presence-only prototype capability for targets supported by SS14 ensnaring. */
public record EnsnareableComponent() implements CharacterComponent {
    public static final String TYPE = "Ensnareable";
    public static final Codec<EnsnareableComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> TYPE)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type);
        return new EnsnareableComponent();
    }));

    @Override public String type() { return TYPE; }
}
