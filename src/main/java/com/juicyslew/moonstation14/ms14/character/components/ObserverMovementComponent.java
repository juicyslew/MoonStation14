package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Observer movement rates in canonical blocks per second. */
public record ObserverMovementComponent(double walkSpeed, double sprintSpeed) implements CharacterComponent {
    public static final String TYPE = "ObserverMovement";
    public static final double DEFAULT_WALK_SPEED = 8.0;
    public static final double DEFAULT_SPRINT_SPEED = 12.0;
    public static final Codec<ObserverMovementComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ObserverMovementComponent::type),
            Codec.DOUBLE.optionalFieldOf("walk_speed", DEFAULT_WALK_SPEED).forGetter(ObserverMovementComponent::walkSpeed),
            Codec.DOUBLE.optionalFieldOf("sprint_speed", DEFAULT_SPRINT_SPEED).forGetter(ObserverMovementComponent::sprintSpeed)
    ).apply(instance, (type, walk, sprint) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        if (!Double.isFinite(walk) || walk <= 0 || !Double.isFinite(sprint) || sprint <= 0)
            throw new IllegalArgumentException("ObserverMovement speeds must be finite and positive");
        return new ObserverMovementComponent(walk, sprint);
    }));
    public ObserverMovementComponent {
        if (!Double.isFinite(walkSpeed) || walkSpeed <= 0 || !Double.isFinite(sprintSpeed) || sprintSpeed <= 0)
            throw new IllegalArgumentException("ObserverMovement speeds must be finite and positive");
    }
    @Override public String type() { return TYPE; }
}

