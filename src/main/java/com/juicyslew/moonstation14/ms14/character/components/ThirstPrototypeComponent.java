package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Typed per-character thirst policy; defaults preserve the human thirst behavior. */
public record ThirstPrototypeComponent(float overHydrated, float okay, float thirsty, float parched,
                                       float dead, float decay, float startingThirst) implements CharacterComponent {
    public static final String TYPE = "Thirst";
    public static final ThirstPrototypeComponent DEFAULT = new ThirstPrototypeComponent(600f, 450f, 300f, 150f, 0f, .1f, -1f);
    public static final Codec<ThirstPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ThirstPrototypeComponent::type),
            Codec.FLOAT.optionalFieldOf("overHydrated", DEFAULT.overHydrated).forGetter(ThirstPrototypeComponent::overHydrated),
            Codec.FLOAT.optionalFieldOf("okay", DEFAULT.okay).forGetter(ThirstPrototypeComponent::okay),
            Codec.FLOAT.optionalFieldOf("thirsty", DEFAULT.thirsty).forGetter(ThirstPrototypeComponent::thirsty),
            Codec.FLOAT.optionalFieldOf("parched", DEFAULT.parched).forGetter(ThirstPrototypeComponent::parched),
            Codec.FLOAT.optionalFieldOf("dead", DEFAULT.dead).forGetter(ThirstPrototypeComponent::dead),
            Codec.FLOAT.optionalFieldOf("decay", DEFAULT.decay).forGetter(ThirstPrototypeComponent::decay),
            Codec.FLOAT.optionalFieldOf("startingThirst", DEFAULT.startingThirst).forGetter(ThirstPrototypeComponent::startingThirst)
    ).apply(instance, (type, overHydrated, okay, thirsty, parched, dead, decay, startingThirst) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new ThirstPrototypeComponent(overHydrated, okay, thirsty, parched, dead, decay, startingThirst);
    }));

    public ThirstPrototypeComponent {
        if (!Float.isFinite(overHydrated) || !Float.isFinite(okay) || !Float.isFinite(thirsty)
                || !Float.isFinite(parched) || !Float.isFinite(dead) || !Float.isFinite(decay)
                || !Float.isFinite(startingThirst) || dead < 0 || parched < dead || thirsty < parched
                || okay < thirsty || overHydrated < okay || decay < 0 || startingThirst < -1f
                || (startingThirst >= 0f && startingThirst > overHydrated))
            throw new IllegalArgumentException("invalid thirst policy");
    }

    @Override public String type() { return TYPE; }
}
