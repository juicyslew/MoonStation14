package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Marker granting admission to finite stun and action-blocking status checks. */
public record StunnableComponent() implements CharacterComponent {
    public static final String TYPE = "Stunnable";
    public static final Codec<StunnableComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(StunnableComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new StunnableComponent();
    }));

    @Override public String type() { return TYPE; }
}
