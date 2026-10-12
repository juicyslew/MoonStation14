package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Typed, per-character hunger policy. Human defaults follow the pinned SS14 component defaults. */
public record HungerPrototypeComponent(float overfed, float okay, float peckish, float starving,
                                       float dead, float decay) implements CharacterComponent {
    public static final String TYPE = "Hunger";
    public static final HungerPrototypeComponent DEFAULT = new HungerPrototypeComponent(200f,150f,100f,50f,0f,0.01666666666f);
    public static final Codec<HungerPrototypeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(HungerPrototypeComponent::type),
            Codec.FLOAT.optionalFieldOf("overfed", DEFAULT.overfed).forGetter(HungerPrototypeComponent::overfed),
            Codec.FLOAT.optionalFieldOf("okay", DEFAULT.okay).forGetter(HungerPrototypeComponent::okay),
            Codec.FLOAT.optionalFieldOf("peckish", DEFAULT.peckish).forGetter(HungerPrototypeComponent::peckish),
            Codec.FLOAT.optionalFieldOf("starving", DEFAULT.starving).forGetter(HungerPrototypeComponent::starving),
            Codec.FLOAT.optionalFieldOf("dead", DEFAULT.dead).forGetter(HungerPrototypeComponent::dead),
            Codec.FLOAT.optionalFieldOf("decay", DEFAULT.decay).forGetter(HungerPrototypeComponent::decay)
    ).apply(i, (type, overfed, okay, peckish, starving, dead, decay) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new HungerPrototypeComponent(overfed, okay, peckish, starving, dead, decay);
    }));
    public HungerPrototypeComponent {
        if (!Float.isFinite(overfed) || !Float.isFinite(okay) || !Float.isFinite(peckish)
                || !Float.isFinite(starving) || !Float.isFinite(dead) || !Float.isFinite(decay)
                || dead < 0 || starving < dead || peckish < starving || okay < peckish || overfed < okay
                || decay < 0)
            throw new IllegalArgumentException("invalid hunger policy");
    }
    @Override public String type() { return TYPE; }
}
