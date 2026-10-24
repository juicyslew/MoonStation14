package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved capability to hear and learn nearby local IC speech. */
public record ParrotListenerComponent(double hearingRange) implements CharacterComponent {
    public static final String TYPE = "ParrotListener";
    public static final ParrotListenerComponent DEFAULT = new ParrotListenerComponent(10);
    public static final Codec<ParrotListenerComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ParrotListenerComponent::type),
            Codec.DOUBLE.optionalFieldOf("hearingRange", DEFAULT.hearingRange).forGetter(ParrotListenerComponent::hearingRange)
    ).apply(instance, (type, range) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new ParrotListenerComponent(range);
    }));

    public ParrotListenerComponent {
        if (!Double.isFinite(hearingRange) || hearingRange <= 0 || hearingRange > 10)
            throw new IllegalArgumentException("invalid parrot hearing range");
    }

    @Override public String type() { return TYPE; }
}
