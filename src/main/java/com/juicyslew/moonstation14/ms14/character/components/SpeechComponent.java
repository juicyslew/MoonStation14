package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Explicit opt-in to local text speech capability, with optional SS14-style voice policy. */
public record SpeechComponent(String accent, String speechSound) implements CharacterComponent {
    public static final String TYPE = "Speech";
    public static final Codec<SpeechComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(SpeechComponent::type),
            Codec.STRING.optionalFieldOf("accent", "").forGetter(SpeechComponent::accent),
            Codec.STRING.optionalFieldOf("speechSound", "").forGetter(SpeechComponent::speechSound)
    ).apply(instance, (type, accent, speechSound) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        if (!accent.isEmpty() && !"mouse".equals(accent)) throw new IllegalArgumentException("unknown speech accent: " + accent);
        if (!speechSound.isEmpty() && !"mouse_squeak".equals(speechSound)) throw new IllegalArgumentException("unknown speech sound: " + speechSound);
        return new SpeechComponent(accent, speechSound);
    }));

    public SpeechComponent() { this("", ""); }

    @Override public String type() { return TYPE; }
}
