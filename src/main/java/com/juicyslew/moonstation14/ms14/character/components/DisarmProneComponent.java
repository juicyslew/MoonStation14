package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** SS14 Administration DisarmProne marker; distinct from ordinary prone posture. */
public record DisarmProneComponent() implements CharacterComponent {
    public static final String TYPE = "DisarmProne";
    public static final Codec<DisarmProneComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(DisarmProneComponent::type)
    ).apply(i, type -> { if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type); return new DisarmProneComponent(); }));
    @Override public String type() { return TYPE; }
}
