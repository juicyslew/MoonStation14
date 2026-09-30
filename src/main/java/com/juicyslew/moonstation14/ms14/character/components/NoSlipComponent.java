package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Marker denying slipping after a valid contact has latched. */
public record NoSlipComponent() implements CharacterComponent {
    public static final String TYPE = "NoSlip";
    public static final Codec<NoSlipComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(NoSlipComponent::type)
    ).apply(instance, type -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new NoSlipComponent();
    }));

    @Override public String type() { return TYPE; }
}
