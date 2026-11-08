package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Species identity consumed by patient-facing scanners, not a complete appearance profile. */
public record HumanoidProfileComponent(String species) implements CharacterComponent {
    public static final String TYPE = "HumanoidProfile";
    public static final Codec<HumanoidProfileComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE),
            Codec.STRING.fieldOf("species").forGetter(HumanoidProfileComponent::species)
    ).apply(instance, (type, species) -> new HumanoidProfileComponent(species)));

    public HumanoidProfileComponent {
        if (species == null || species.isBlank() || species.length() > 64)
            throw new IllegalArgumentException("species must be a nonblank ID of at most 64 characters");
    }
    @Override public String type() { return TYPE; }
}

