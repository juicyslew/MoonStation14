package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;

/** Ordered hand capability policy; persisted occupants live in the separate hands attachment. */
public record HandsPrototypeComponent(List<String> hands, boolean canBeStripped) implements CharacterComponent {
    public static final String TYPE = "Hands";
    public static final Codec<HandsPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(HandsPrototypeComponent::type),
            Codec.STRING.listOf().fieldOf("hands").forGetter(HandsPrototypeComponent::hands),
            Codec.BOOL.optionalFieldOf("can_be_stripped", true).forGetter(HandsPrototypeComponent::canBeStripped)
    ).apply(instance, (type, hands, canBeStripped) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new HandsPrototypeComponent(hands, canBeStripped);
    }));

    public HandsPrototypeComponent(List<String> hands) { this(hands, true); }

    public HandsPrototypeComponent {
        hands = List.copyOf(Objects.requireNonNull(hands, "hands"));
        if (!hands.isEmpty()) HandState.create(hands);
    }

    @Override public String type() { return TYPE; }
}
