package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** SS14 pull admission and movement penalties; hand requirement is a fixed local capability contract. */
public record PullerComponent(double walkModifier, double sprintModifier, TetherSettings tether) implements CharacterComponent {
    public static final String TYPE = "Puller";
    public static final Codec<PullerComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(PullerComponent::type),
            Codec.DOUBLE.optionalFieldOf("walk_modifier", .95d).forGetter(PullerComponent::walkModifier),
            Codec.DOUBLE.optionalFieldOf("sprint_modifier", .95d).forGetter(PullerComponent::sprintModifier),
            TetherSettings.CODEC.optionalFieldOf("tether", TetherSettings.DEFAULT).forGetter(PullerComponent::tether)
    ).apply(i, (type, walk, sprint, tether) -> new PullerComponent(walk, sprint, tether)));
    public PullerComponent(double walkModifier, double sprintModifier) { this(walkModifier, sprintModifier, TetherSettings.DEFAULT); }
    public PullerComponent {
        if (!Double.isFinite(walkModifier) || walkModifier < 0 || !Double.isFinite(sprintModifier) || sprintModifier < 0 || tether == null)
            throw new IllegalArgumentException("puller modifiers must be finite and nonnegative");
    }
    @Override public String type() { return TYPE; }
}
