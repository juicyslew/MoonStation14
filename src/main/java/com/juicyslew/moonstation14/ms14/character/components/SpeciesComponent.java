package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Prototype-owned human-readable species label for scanners; not a humanoid capability. */
public record SpeciesComponent(String label) implements CharacterComponent {
    public static final String TYPE = "Species";
    public static final Codec<SpeciesComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE),
            Codec.STRING.fieldOf("label").forGetter(SpeciesComponent::label)
    ).apply(instance, (type, label) -> new SpeciesComponent(label)));

    public SpeciesComponent {
        if (label == null || label.isBlank() || label.length() > 64)
            throw new IllegalArgumentException("species label must be nonblank and at most 64 characters");
    }
    @Override public String type() { return TYPE; }
}
