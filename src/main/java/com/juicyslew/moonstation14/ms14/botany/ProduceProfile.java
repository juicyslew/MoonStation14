package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Produce-specific composition. Starting/current reagent contents remain owned by IngestionProfile. */
public record ProduceProfile(ResourceLocation seed, boolean grindable,
                             com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile.Edible.SourceSolution sourceSolution) {
    public static final Codec<ProduceProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("seed").forGetter(ProduceProfile::seed),
            Codec.BOOL.optionalFieldOf("grindable", false).forGetter(ProduceProfile::grindable),
            com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile.Edible.SourceSolution.CODEC
                    .optionalFieldOf("source_solution").forGetter(p -> java.util.Optional.ofNullable(p.sourceSolution()))
    ).apply(i, (seed, grindable, source) -> new ProduceProfile(seed, grindable, source.orElse(null))));
    public ProduceProfile { if (seed == null) throw new IllegalArgumentException("produce seed is required"); }
}
