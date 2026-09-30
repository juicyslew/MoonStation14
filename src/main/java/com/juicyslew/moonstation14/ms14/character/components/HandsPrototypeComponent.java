package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;

/** Ordered hand capability policy; persisted occupants live in the separate hands attachment. */
public record HandsPrototypeComponent(List<String> hands) implements CharacterComponent {
    public static final String TYPE = "Hands";
    public static final Codec<HandsPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(HandsPrototypeComponent::type),
            Codec.STRING.listOf().fieldOf("hands").forGetter(HandsPrototypeComponent::hands)
    ).apply(instance, (type, hands) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new HandsPrototypeComponent(hands);
    }));

    public HandsPrototypeComponent {
        hands = List.copyOf(Objects.requireNonNull(hands, "hands"));
        if (!hands.isEmpty()) HandState.create(hands);
    }

    @Override public String type() { return TYPE; }
}
