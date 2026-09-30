package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Mob body capability marker; attached organs are data, not Minecraft entities. */
public record BodyComponent() implements CharacterComponent {
    public static final String TYPE = "Body";
    public static final Codec<BodyComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(BodyComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new BodyComponent();
    }));

    @Override public String type() { return TYPE; }
}
