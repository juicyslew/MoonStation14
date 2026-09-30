package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Current flattened mob metabolizer capability; stage-specific organ ownership is separate work. */
public record MetabolizerPrototypeComponent(Set<MetabolizerTypeEnum> types) implements CharacterComponent {
    public static final String TYPE = "Metabolizer";
    public static final Codec<MetabolizerPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(MetabolizerPrototypeComponent::type),
            Codec.list(MetabolizerTypeEnum.CODEC).fieldOf("types").forGetter(value -> List.copyOf(value.types()))
    ).apply(instance, (type, types) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        if (types.size() != Set.copyOf(types).size()) throw new IllegalArgumentException("duplicate metabolizer type");
        return new MetabolizerPrototypeComponent(Set.copyOf(types));
    }));

    public MetabolizerPrototypeComponent {
        types = Set.copyOf(Objects.requireNonNull(types, "types"));
    }

    @Override public String type() { return TYPE; }
}
