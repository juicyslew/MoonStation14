package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Explicit opt-in to local text speech capability, with optional SS14-style voice policy. */
public record SpeechComponent(String accent, String speechSound, java.util.Set<String> allowedEmotes) implements CharacterComponent {
    public static final String TYPE = "Speech";
    public static final Codec<SpeechComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(SpeechComponent::type),
            Codec.STRING.optionalFieldOf("accent", "").forGetter(SpeechComponent::accent),
            Codec.STRING.optionalFieldOf("speechSound", "").forGetter(SpeechComponent::speechSound),
            Codec.list(Codec.STRING).optionalFieldOf("allowedEmotes", java.util.List.of()).forGetter(value -> value.allowedEmotes().stream().sorted().toList())
    ).apply(instance, (type, accent, speechSound, allowedEmotes) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        if (!accent.isEmpty() && !"mouse".equals(accent)) throw new IllegalArgumentException("unknown speech accent: " + accent);
        if (!speechSound.isEmpty() && !"mouse_squeak".equals(speechSound)) throw new IllegalArgumentException("unknown speech sound: " + speechSound);
        java.util.Set<String> emotes = java.util.Set.copyOf(allowedEmotes);
        if (emotes.size() != allowedEmotes.size() || emotes.stream().anyMatch(id -> !com.juicyslew.moonstation14.component.codec.json.EmoteRegistry.ids().contains(id)))
            throw new IllegalArgumentException("unknown or duplicate allowed emote");
        return new SpeechComponent(accent, speechSound, emotes);
    }));

    public SpeechComponent(String accent, String speechSound) { this(accent, speechSound, java.util.Set.of()); }
    public SpeechComponent() { this("", "", java.util.Set.of()); }

    @Override public String type() { return TYPE; }
}

