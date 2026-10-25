package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Extraction behavior composed independently from an item's food/produce identity. */
public record ExtractableProfile(String grindableSolutionName, ReagentComponent juiceSolution) {
    public static final Codec<ExtractableProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("grindable_solution_name").forGetter(p -> java.util.Optional.ofNullable(p.grindableSolutionName())),
            ReagentComponent.CODEC.optionalFieldOf("juice_solution").forGetter(p -> java.util.Optional.ofNullable(p.juiceSolution()))
    ).apply(i, (grind, juice) -> new ExtractableProfile(grind.orElse(null), juice.orElse(null))));

    public ExtractableProfile {
        if (grindableSolutionName != null && grindableSolutionName.isBlank())
            throw new IllegalArgumentException("grindable solution name cannot be blank");
    }
}
