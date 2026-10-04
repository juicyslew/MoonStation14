package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Explicit liquid-ingestion capability. This is not a simulated physical mouth organ. */
public record MouthComponent() implements CharacterComponent {
    public static final String TYPE = "Mouth";
    public static final Codec<MouthComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(MouthComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new MouthComponent();
    }));

    @Override public String type() { return TYPE; }
}
