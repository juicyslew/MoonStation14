package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Explicit prototype eligibility and presentation/capabilities for ordinary ghost roles. */
public record GhostRoleComponent(String name, String description, String rules,
                                 boolean takeover, boolean allowMovement, boolean allowSpeech)
        implements CharacterComponent {
    public static final String TYPE = "GhostRole";
    public static final Codec<GhostRoleComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(GhostRoleComponent::type),
            Codec.STRING.fieldOf("name").forGetter(GhostRoleComponent::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(GhostRoleComponent::description),
            Codec.STRING.optionalFieldOf("rules", "").forGetter(GhostRoleComponent::rules),
            Codec.BOOL.optionalFieldOf("takeover", true).forGetter(GhostRoleComponent::takeover),
            Codec.BOOL.optionalFieldOf("allow_movement", true).forGetter(GhostRoleComponent::allowMovement),
            Codec.BOOL.optionalFieldOf("allow_speech", true).forGetter(GhostRoleComponent::allowSpeech)
    ).apply(i, (type, name, description, rules, takeover, movement, speech) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type);
        if (name.isBlank()) throw new IllegalArgumentException("GhostRole name must not be blank");
        return new GhostRoleComponent(name, description, rules, takeover, movement, speech);
    }));
    @Override public String type() { return TYPE; }
}
