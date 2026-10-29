package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Explicit lightning-selection eligibility and damage parameters for a resolved character prototype. */
public record LightningTargetComponent(int priority, double probability, double resistance,
        double structuralDamage, boolean explodes, double intensity, double dropoff,
        double maximumPerTile) implements CharacterComponent {
    public static final String TYPE = "lightningTarget";
    public static final Codec<LightningTargetComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(v -> TYPE),
            Codec.INT.optionalFieldOf("priority",0).forGetter(LightningTargetComponent::priority), Codec.DOUBLE.optionalFieldOf("probability",1d).forGetter(LightningTargetComponent::probability),
            Codec.DOUBLE.optionalFieldOf("resistance",1d).forGetter(LightningTargetComponent::resistance), Codec.DOUBLE.optionalFieldOf("structuralDamage",1d).forGetter(LightningTargetComponent::structuralDamage),
            Codec.BOOL.optionalFieldOf("explodes",true).forGetter(LightningTargetComponent::explodes),
            Codec.DOUBLE.optionalFieldOf("intensity",25d).forGetter(LightningTargetComponent::intensity), Codec.DOUBLE.optionalFieldOf("dropoff",2d).forGetter(LightningTargetComponent::dropoff),
            Codec.DOUBLE.optionalFieldOf("maximumPerTile",5d).forGetter(LightningTargetComponent::maximumPerTile)
    ).apply(i, (type, priority, probability, resistance, damage, explodes, intensity, dropoff, max) -> new LightningTargetComponent(priority, probability, resistance, damage, explodes, intensity, dropoff, max)));
    @Override public String type() { return TYPE; }
}
