package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Target-side stripping policy. Delay is expressed in seconds. */
public record StrippablePrototypeComponent(double handStripDelay) implements CharacterComponent {
    public static final String TYPE = "Strippable";
    public static final Codec<StrippablePrototypeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(StrippablePrototypeComponent::type),
            Codec.DOUBLE.optionalFieldOf("hand_strip_delay", 4.0).forGetter(StrippablePrototypeComponent::handStripDelay)
    ).apply(i, (type, delay) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new StrippablePrototypeComponent(delay);
    }));
    public StrippablePrototypeComponent { if (!Double.isFinite(handStripDelay) || handStripDelay < 0) throw new IllegalArgumentException("invalid hand strip delay"); }
    @Override public String type() { return TYPE; }
}

