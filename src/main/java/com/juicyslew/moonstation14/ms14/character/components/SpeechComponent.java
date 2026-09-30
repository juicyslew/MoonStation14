package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Explicit opt-in to local text speech capability. */
public record SpeechComponent() implements CharacterComponent {
    public static final String TYPE = "Speech";
    public static final Codec<SpeechComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(SpeechComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new SpeechComponent();
    }));

    @Override public String type() { return TYPE; }
}
