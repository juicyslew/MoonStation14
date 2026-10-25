package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved animal milk-solution production capability (SS14 UdderComponent). */
public record UdderComponent(float quantity, float hungerUsage, float growthDelaySeconds,
                             float solutionCapacity, String solutionName) implements CharacterComponent {
    public static final String TYPE = "Udder";
    public static final UdderComponent DEFAULT = new UdderComponent(25, 10, 60, 250, "udder");
    public static final Codec<UdderComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(UdderComponent::type),
            Codec.FLOAT.optionalFieldOf("quantity", DEFAULT.quantity).forGetter(UdderComponent::quantity),
            Codec.FLOAT.optionalFieldOf("hunger_usage", DEFAULT.hungerUsage).forGetter(UdderComponent::hungerUsage),
            Codec.FLOAT.optionalFieldOf("growth_delay_seconds", DEFAULT.growthDelaySeconds).forGetter(UdderComponent::growthDelaySeconds),
            Codec.FLOAT.optionalFieldOf("solution_capacity", DEFAULT.solutionCapacity).forGetter(UdderComponent::solutionCapacity),
            Codec.STRING.optionalFieldOf("solution_name", DEFAULT.solutionName).forGetter(UdderComponent::solutionName)
    ).apply(i, (type, quantity, hunger, delay, capacity, name) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new UdderComponent(quantity, hunger, delay, capacity, name);
    }));
    public UdderComponent {
        if (!Float.isFinite(quantity) || !Float.isFinite(hungerUsage) || !Float.isFinite(growthDelaySeconds)
                || !Float.isFinite(solutionCapacity) || quantity <= 0 || hungerUsage < 0 || growthDelaySeconds <= 0
                || solutionCapacity < quantity || solutionName == null || solutionName.isBlank())
            throw new IllegalArgumentException("invalid udder capability");
    }
    @Override public String type() { return TYPE; }
}
